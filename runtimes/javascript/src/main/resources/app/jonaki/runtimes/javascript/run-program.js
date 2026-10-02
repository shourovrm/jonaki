// Runs one run_code program inside a JavaScriptSandbox isolate. The app puts
// `const jonakiJob = {code, files}` in front of this text; the promise below
// resolves to the JSON text the app reads back. The isolate has no network,
// no DOM and no timers of its own, so the program can only compute and use
// the `files` object.
(async () => {
  const maxPrintedCharacters = 1000000;
  const printed = { stdout: "", stderr: "", dropped: 0 };
  const written = {};

  function describe(value) {
    if (typeof value === "string") {
      return value;
    }
    if (value === undefined) {
      return "undefined";
    }
    if (typeof value === "function" || typeof value === "symbol" || typeof value === "bigint") {
      return String(value);
    }
    try {
      const text = JSON.stringify(value, null, 2);
      return text === undefined ? String(value) : text;
    } catch (error) {
      return String(value);
    }
  }

  function print(stream, values) {
    const line = values.map(describe).join(" ") + "\n";
    const room = maxPrintedCharacters - printed[stream].length;
    if (line.length <= room) {
      printed[stream] += line;
    } else {
      printed[stream] += line.substring(0, Math.max(room, 0));
      printed.dropped += line.length - Math.max(room, 0);
    }
  }

  function normalPath(path) {
    return String(path).replace(/^(\.\/)+/, "");
  }

  globalThis.console = {
    log: (...values) => print("stdout", values),
    info: (...values) => print("stdout", values),
    debug: (...values) => print("stdout", values),
    warn: (...values) => print("stderr", values),
    error: (...values) => print("stderr", values),
  };

  globalThis.files = {
    read(path) {
      const key = normalPath(path);
      if (Object.prototype.hasOwnProperty.call(written, key)) {
        return written[key];
      }
      if (Object.prototype.hasOwnProperty.call(jonakiJob.files, key)) {
        return jonakiJob.files[key];
      }
      throw new Error(key + " was not given to run_code; list it in files");
    },
    write(path, text) {
      written[normalPath(path)] = String(text);
    },
    list() {
      return Array.from(new Set(Object.keys(jonakiJob.files).concat(Object.keys(written)))).sort();
    },
  };

  let result = null;
  let error = null;
  try {
    let value;
    try {
      // Indirect eval runs the program in global scope and returns the
      // value of its last expression, like a console.
      value = (0, eval)(jonakiJob.code);
    } catch (syntaxError) {
      // Top-level await is not valid in a script; run such a program as the
      // body of an async function, where `return` gives the result.
      if (!(syntaxError instanceof SyntaxError) || !jonakiJob.code.includes("await")) {
        throw syntaxError;
      }
      value = (0, eval)("(async () => {\n" + jonakiJob.code + "\n})()");
    }
    if (value !== null && typeof value === "object" && typeof value.then === "function") {
      value = await value;
    }
    result = value === undefined ? null : describe(value);
  } catch (thrown) {
    // The message and the program's own frame; deeper frames name only this wrapper.
    error = thrown && thrown.stack ? String(thrown.stack).split("\n").slice(0, 2).join("\n") : String(thrown);
  }

  return JSON.stringify({
    stdout: printed.stdout,
    stderr: printed.stderr,
    droppedCharacters: printed.dropped,
    result: result,
    error: error,
    written: written,
  });
})()
