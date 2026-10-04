// Runs use_helper.py in the same Pyodide release the app installs, with the
// same packages, and writes the Office files it makes into made/.
// Setup and results: README.md beside this file.
import { loadPyodide } from "pyodide";
import { mkdirSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const helperPath = join(here, "../../runtimes/pyodide/src/main/resources/app/jonaki/runtimes/pyodide/python/jonaki_docs.py");
const lockPackages = ["lxml", "pillow", "typing-extensions", "beautifulsoup4", "soupsieve"];

const pyodide = await loadPyodide();
await pyodide.loadPackage(lockPackages, { messageCallback: () => {} });
const wheels = readdirSync(join(here, "wheels")).filter((name) => name.endsWith(".whl"));
await pyodide.loadPackage(wheels.map((name) => join(here, "wheels", name)), { messageCallback: () => {} });

const sitePackages = pyodide.runPython("import site; site.getsitepackages()[0]");
pyodide.FS.writeFile(sitePackages + "/jonaki_docs.py", readFileSync(helperPath));
pyodide.FS.mkdirTree("/thread/artifacts");
for (const name of ["report.html", "slides.html"]) {
  pyodide.FS.writeFile("/thread/artifacts/" + name, readFileSync(join(here, name)));
}
pyodide.FS.chdir("/thread");

const started = Date.now();
try {
  await pyodide.runPythonAsync(readFileSync(join(here, "use_helper.py"), "utf8"), { filename: "main.py" });
} catch (error) {
  console.log(String(error.message).split("\n").slice(-14).join("\n"));
  process.exit(1);
}
console.log("program ms", Date.now() - started);

mkdirSync(join(here, "made"), { recursive: true });
for (const name of pyodide.FS.readdir("/thread/artifacts")) {
  if (/\.(docx|xlsx|pptx)$/.test(name)) {
    writeFileSync(join(here, "made", name), pyodide.FS.readFile("/thread/artifacts/" + name));
    console.log("made", name);
  }
}
