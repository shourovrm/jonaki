"use strict";
// Runs one Python program with Pyodide. Every file it fetches comes from the
// app's request filter: pyodide/ holds the installed Pyodide files and
// input/ the thread files named in the run_code call.

importScripts("pyodide/pyodide.js");

const THREAD_ROOT = "/thread";
// Outside /thread, so that it is never sent back as a changed file.
const MODULES_FOLDER = "/jonaki_modules";
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

function importedModules(pyodide, code) {
  const codeModule = pyodide.pyimport("pyodide.code");
  const importsProxy = codeModule.find_imports(code);
  const imports = importsProxy.toJs();
  importsProxy.destroy();
  codeModule.destroy();
  return imports;
}

// Sorts what the program imports into packages to load and packages that are
// not installed. A program that imports a documents wheel or a bundled module
// needs the whole documents add-on, the combination that was tested together.
function packagesFor(imports, job) {
  const installedLock = new Set(job.installedPackages);
  const installedWheels = job.installedWheelPaths;
  const needsDocuments = imports.some((importName) => job.documentsImports.includes(importName));
  const neededLock = new Set();
  const missing = new Set();
  for (const importName of imports) {
    const packageName = job.packageForImport[importName];
    if (packageName === undefined) {
      continue;
    }
    if (installedLock.has(packageName)) {
      neededLock.add(packageName);
    } else {
      missing.add(packageName);
    }
  }
  const neededWheelPaths = [];
  if (needsDocuments) {
    for (const partName of job.documentsAddOn) {
      if (installedLock.has(partName)) {
        neededLock.add(partName);
      } else if (installedWheels[partName] !== undefined) {
        neededWheelPaths.push(installedWheels[partName]);
      } else {
        missing.add(partName);
      }
    }
  }
  return { neededLock: Array.from(neededLock), neededWheelPaths: neededWheelPaths, missing: Array.from(missing) };
}

// Pyodide loads a pure-Python wheel from a URL without resolving its
// dependencies; the app serves the file from the folder it checked.
function wheelUrl(path) {
  return new URL(path, self.location.href).href;
}

// Writes the bundled modules the program imports into a folder on Python's
// import path. The app serves only the names it lists (python/<name>.py).
async function writeBundledModules(pyodide, imports, job) {
  const wanted = job.bundledModules.filter((name) => imports.includes(name));
  if (wanted.length === 0) {
    return;
  }
  makeFolders(pyodide.FS, MODULES_FOLDER);
  for (const name of wanted) {
    const response = await fetch("python/" + name + ".py");
    if (!response.ok) {
      throw new Error("Could not read the bundled module " + name + " (" + response.status + ")");
    }
    pyodide.FS.writeFile(MODULES_FOLDER + "/" + name + ".py", new Uint8Array(await response.arrayBuffer()));
  }
  pyodide.runPython("import sys\nsys.path.insert(0, " + JSON.stringify(MODULES_FOLDER) + ")");
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
  const imports = importedModules(pyodide, job.code);
  const packages = packagesFor(imports, job);
  if (packages.missing.length > 0) {
    return { missingPackages: packages.missing };
  }
  if (packages.neededLock.length > 0 || packages.neededWheelPaths.length > 0) {
    post({ type: "phase", name: "packages" });
  }
  if (packages.neededLock.length > 0) {
    await pyodide.loadPackage(packages.neededLock, { messageCallback: () => {} });
  }
  if (packages.neededWheelPaths.length > 0) {
    await pyodide.loadPackage(packages.neededWheelPaths.map(wheelUrl), { messageCallback: () => {} });
  }
  await writeBundledModules(pyodide, imports, job);
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
