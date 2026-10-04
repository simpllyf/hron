import { describe, it, expect } from "vitest";
import { unstable_startWorker } from "wrangler";
import * as bindings from "../pkg/hron_wasm_bg.js";
import * as entry from "../pkg/hron_wasm.js";

describe("package entry", () => {
  it("explains a cron expression", () => {
    expect(entry.explainCron("0 9 * * 1-5")).toBe("every weekday at 09:00");
  });
});

// Wrangler resolves hron-wasm through its "workerd" export condition, so the worker
// runs hron_wasm_workerd.js, which re-exports the bindings by name: a binding missing
// from its export list is missing from the package in Workers.
describe("Cloudflare Workers entry", () => {
  it("instantiates the module and exports every public binding", async () => {
    const worker = await unstable_startWorker({
      entrypoint: "worker/index.js",
      compatibilityDate: "2026-09-01",
      dev: { inspector: false, server: { port: 0 } },
    });
    try {
      const response = await worker.fetch("http://localhost/");
      const { exports, cron } = await response.json();
      const publicBindings = Object.keys(bindings).filter((name) => !name.startsWith("__"));
      expect(exports.sort()).toEqual(publicBindings.sort());
      expect(cron).toBe("0 9 * * 1-5");
    } finally {
      await worker.dispose();
    }
  }, 30_000);
});
