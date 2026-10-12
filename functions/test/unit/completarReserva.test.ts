import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseCompletar } from "../../src/completarReserva.js";
import { esperarErrorSync } from "../helpers.js";

function invalido(data: unknown) {
  esperarErrorSync(() => parseCompletar(data), "invalid-argument", "DATOS_INVALIDOS");
}

describe("parseCompletar", () => {
  it("acepta un id y lo normaliza", () => {
    assert.deepEqual(parseCompletar({ reservaId: " r1 " }), { reservaId: "r1" });
  });

  it("ignora claves desconocidas (el estado nunca lo elige el cliente)", () => {
    assert.deepEqual(parseCompletar({ reservaId: "r1", estado: "COMPLETADA", pago: { estado: "AUTORIZADO" } }), {
      reservaId: "r1",
    });
  });

  it("rechaza cuerpos que no son objeto", () => {
    invalido(null);
    invalido("r1");
    invalido([]);
  });

  it("rechaza ids vacios, no texto o con barra", () => {
    invalido({});
    invalido({ reservaId: "  " });
    invalido({ reservaId: 42 });
    invalido({ reservaId: "reservas/otra" });
    invalido({ reservaId: "x".repeat(129) });
  });
});
