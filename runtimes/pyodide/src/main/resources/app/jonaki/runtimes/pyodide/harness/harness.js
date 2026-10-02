"use strict";
// The page side of a Python run. Python itself runs in a worker, which has no
// DOM, no frames and no WebRTC, so the page's Content-Security-Policy and the
// app's request filter are the only ways out, and both refuse the internet.
// The page relays the worker's messages to the app through JonakiBridge.

const MAX_CHUNK_CHARACTERS = 1_000_000;

let pythonWorker = null;

function bytesToBase64(bytes) {
  let binary = "";
  const step = 0x8000;
  for (let start = 0; start < bytes.length; start += step) {
    binary += String.fromCharCode.apply(null, bytes.subarray(start, start + step));
  }
  return btoa(binary);
}

function relayFile(path, bytes) {
  // A JavascriptInterface call takes strings only; large files go in chunks
  // so that no single string is too big for the bridge.
  const base64 = bytesToBase64(bytes);
  JonakiBridge.fileStarted(path);
  for (let start = 0; start < base64.length; start += MAX_CHUNK_CHARACTERS) {
    JonakiBridge.fileChunk(path, base64.substring(start, start + MAX_CHUNK_CHARACTERS));
  }
  JonakiBridge.fileFinished(path);
}

function handleWorkerMessage(event) {
  const message = event.data;
  if (message.type === "stdout") {
    JonakiBridge.stdout(message.text);
  } else if (message.type === "stderr") {
    JonakiBridge.stderr(message.text);
  } else if (message.type === "phase") {
    JonakiBridge.phase(message.name);
  } else if (message.type === "file") {
    relayFile(message.path, message.bytes);
  } else if (message.type === "done") {
    JonakiBridge.done(JSON.stringify(message.outcome));
  }
}

function startRun() {
  const job = JSON.parse(JonakiBridge.job());
  pythonWorker = new Worker("python-worker.js");
  pythonWorker.onmessage = handleWorkerMessage;
  pythonWorker.onerror = (event) => {
    JonakiBridge.done(JSON.stringify({ error: "The Python worker failed: " + event.message }));
  };
  pythonWorker.postMessage(job);
}

// Called by the app when the time limit passes; terminate() stops even a
// worker stuck in an endless loop.
function stopRun() {
  if (pythonWorker !== null) {
    pythonWorker.terminate();
    pythonWorker = null;
  }
}

startRun();
