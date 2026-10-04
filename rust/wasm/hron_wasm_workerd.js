/* @ts-self-types="./hron_wasm.d.ts" */

import * as imports from "./hron_wasm_bg.js";
import wasmModule from "./hron_wasm_bg.wasm";

// Cloudflare Workers import a .wasm file as a compiled WebAssembly.Module, not as
// the instance that wasm-bindgen's bundler entry expects.
const instance = new WebAssembly.Instance(wasmModule, {
  "./hron_wasm_bg.js": imports,
});
imports.__wbg_set_wasm(instance.exports);
instance.exports.__wbindgen_start();

export { Schedule, explainCron, fromCron } from "./hron_wasm_bg.js";
