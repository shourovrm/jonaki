"use strict";
// Runs one Python program with Pyodide. Every file it fetches comes from the
// app's request filter: pyodide/ holds the installed Pyodide files and
// input/ the thread files named in the run_code call.

importScripts("pyodide/pyodide.js");

const THREAD_ROOT = "/thread";
const STARTING_FOLDERS = ["inbox", "work", "artifacts"];

function post(message, transfer) {
  self.postMessage(message, transfer || []);
}

function makeFolders(fileSystem, path) {
  let current = "";
  for (const part of path.split("/").filter((piece) => piece.length > 0)) {
    current += "/" + part;
    if (!fileSystem.analyzePath(current).exists) {
      fileSystem.mkdir(current);
    }
  }
}

async function copyInputsIn(pyodide, inputPaths) {
  const fileSystem = pyodide.FS;
  const inputBytes = new Map();
  for (const relativePath of inputPaths) {
    const response = await fetch("input/" + relativePath.split("/").map(encodeURIComponent).join("/"));
    if (!response.ok) {
      throw new Error("Could not read " + relativePath + " (" + response.status + ")");
    }
    const bytes = new Uint8Array(await response.arrayBuffer());
    const fullPath = THREAD_ROOT + "/" + relativePath;
    makeFolders(fileSystem, fullPath.substring(0, fullPath.lastIndexOf("/")));
    fileSystem.writeFile(fullPath, bytes);
    inputBytes.set(relativePath, bytes);
  }
  return inputBytes;
}

function sameBytes(first, second) {
  if (first.length !== second.length) {
    return false;
  }
  for (let index = 0; index < first.length; index++) {
    if (first[index] !== second[index]) {
      return false;
    }
  }
  return true;
}

function filesUnder(fileSystem, folder) {
  const found = [];
  if (!fileSystem.analyzePath(folder).exists) {
    return found;
  }
  for (const name of fileSystem.readdir(folder)) {
    if (name === "." || name === "..") {
      continue;
    }
    const path = folder + "/" + name;
    const mode = fileSystem.stat(path).mode;
    if (fileSystem.isDir(mode)) {
      found.push(...filesUnder(fileSystem, path));
    } else if (fileSystem.isFile(mode)) {
      found.push(path);
    }
  }
  return found;
}

// Sends back every file that is new or differs from what was copied in.
// The app alone decides what is saved (only work/ and artifacts/, D-067)
// and names the rest as not saved, so the model learns that an edit to
// inbox/ did not happen.
function sendChangedFiles(pyodide, inputBytes) {
  const fileSystem = pyodide.FS;
  for (const fullPath of filesUnder(fileSystem, THREAD_ROOT)) {
    const relativePath = fullPath.substring(THREAD_ROOT.length + 1);
    const bytes = fileSystem.readFile(fullPath);
    const original = inputBytes.get(relativePath);
    if (original !== undefined && sameBytes(original, bytes)) {
      continue;
    }
    post({ type: "file", path: relativePath, bytes: bytes }, [bytes.buffer]);
  }
}

// Pyodide's own frames come before the program's; the model needs only the latter.
function programTraceback(message) {
  const programFrame = message.indexOf('  File "main.py"');
  if (programFrame < 0) {
    return message;
  }
  return "Traceback (most recent call last):\n" + message.substring(programFrame);
}

function missingPackagesFor(pyodide, code, job) {
  const codeModule = pyodide.pyimport("pyodide.code");
  const importsProxy = codeModule.find_imports(code);
  const imports = importsProxy.toJs();
  importsProxy.destroy();
  codeModule.destroy();
  const installed = new Set(job.installedPackages);
  const needed = new Set();
  const missing = new Set();
  for (const importName of imports) {
    const packageName = job.packageForImport[importName];
    if (packageName === undefined) {
      continue;
    }
    if (installed.has(packageName)) {
      needed.add(packageName);
    } else {
      missing.add(packageName);
    }
  }
  return { needed: Array.from(needed), missing: Array.from(missing) };
}

function describeResult(result) {
  if (result === undefined || result === null) {
    return null;
  }
  if (typeof result === "object" && typeof result.destroy === "function") {
    const text = result.toString();
    result.destroy();
    return text;
  }
  return String(result);
}

async function run(job) {
  post({ type: "phase", name: "loading" });
  const pyodide = await loadPyodide({
    indexURL: "pyodide/",
    stdout: (line) => post({ type: "stdout", text: line + "\n" }),
    stderr: (line) => post({ type: "stderr", text: line + "\n" }),
  });
  const packages = missingPackagesFor(pyodide, job.code, job);
  if (packages.missing.length > 0) {
    return { missingPackages: packages.missing };
  }
  if (packages.needed.length > 0) {
    post({ type: "phase", name: "packages" });
    await pyodide.loadPackage(packages.needed, { messageCallback: () => {} });
  }
  for (const folder of STARTING_FOLDERS) {
    makeFolders(pyodide.FS, THREAD_ROOT + "/" + folder);
  }
  const inputBytes = await copyInputsIn(pyodide, job.inputPaths);
  pyodide.FS.chdir(THREAD_ROOT);

  post({ type: "phase", name: "running" });
  let result = null;
  let error = null;
  try {
    result = describeResult(await pyodide.runPythonAsync(job.code, { filename: "main.py" }));
  } catch (exception) {
    error = programTraceback(String(exception.message || exception));
  }
  // Files written before an exception are still sent back, like a script
  // that fails halfway on a computer.
  sendChangedFiles(pyodide, inputBytes);
  return { result: result, error: error };
}

self.onmessage = async (event) => {
  let outcome;
  try {
    outcome = await run(event.data);
  } catch (exception) {
    outcome = { setupError: String(exception.message || exception) };
  }
  post({ type: "done", outcome: outcome });
};
