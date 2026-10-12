import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseCupos } from "../../src/cupos.js";
import { esperarErrorSync } from "../helpers.js";

const ok = { profesionalId: "pro1", desde: "2026-10-05T00:00:00Z", hasta: "2026-10-12T00:00:00Z" };

function invalido(data: unknown) {
  esperarErrorSync(() => parseCupos(data), "invalid-argument", "DATOS_INVALIDOS");
}

describe("parseCupos", () => {
  it("acepta una ventana valida", () => {
    const r = parseCupos(ok);
    assert.equal(r.profesionalId, "pro1");
    assert.equal(r.desde.toISOString(), "2026-10-05T00:00:00.000Z");
    assert.equal(r.hasta.toISOString(), "2026-10-12T00:00:00.000Z");
  });

  it("acepta exactamente 15 dias y rechaza un milisegundo mas", () => {
    parseCupos({ ...ok, hasta: "2026-10-20T00:00:00Z" });
    invalido({ ...ok, hasta: "2026-10-20T00:00:00.001Z" });
  });

  it("rechaza hasta <= desde", () => {
    invalido({ ...ok, hasta: ok.desde });
    invalido({ ...ok, hasta: "2026-10-04T00:00:00Z" });
  });

  it("rechaza fechas sin zona, no parseables o que no son texto", () => {
    invalido({ ...ok, desde: "2026-10-05" });
    invalido({ ...ok, desde: "ayer" });
    invalido({ ...ok, hasta: 1760000000000 });
    invalido({ profesionalId: "pro1" });
  });

  it("rechaza ids invalidos y cuerpos que no son objeto", () => {
    invalido({ ...ok, profesionalId: "" });
    invalido({ ...ok, profesionalId: "a/b" });
    invalido(null);
    invalido([]);
  });
});
