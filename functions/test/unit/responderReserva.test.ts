import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseRespuesta } from "../../src/responderReserva.js";
import { esperarErrorSync } from "../helpers.js";

function invalido(data: unknown) {
  esperarErrorSync(() => parseRespuesta(data), "invalid-argument", "DATOS_INVALIDOS");
}

describe("parseRespuesta", () => {
  it("acepta ACEPTAR y RECHAZAR y normaliza el id", () => {
    assert.deepEqual(parseRespuesta({ reservaId: " r1 ", respuesta: "ACEPTAR" }), {
      reservaId: "r1",
      respuesta: "ACEPTAR",
    });
    assert.deepEqual(parseRespuesta({ reservaId: "r1", respuesta: "RECHAZAR" }), {
      reservaId: "r1",
      respuesta: "RECHAZAR",
    });
  });

  it("ignora claves desconocidas (el estado nunca lo elige el cliente)", () => {
    assert.deepEqual(parseRespuesta({ reservaId: "r1", respuesta: "ACEPTAR", estado: "COMPLETADA" }), {
      reservaId: "r1",
      respuesta: "ACEPTAR",
    });
  });

  it("rechaza cuerpos que no son objeto", () => {
    invalido(null);
    invalido("ACEPTAR");
    invalido([]);
  });

  it("rechaza respuestas fuera del enum", () => {
    invalido({ reservaId: "r1" });
    invalido({ reservaId: "r1", respuesta: "aceptar" });
    invalido({ reservaId: "r1", respuesta: "CONFIRMADA" });
    invalido({ reservaId: "r1", respuesta: true });
  });

  it("rechaza ids vacios, no texto o con barra", () => {
    invalido({ respuesta: "ACEPTAR" });
    invalido({ reservaId: "  ", respuesta: "ACEPTAR" });
    invalido({ reservaId: 42, respuesta: "ACEPTAR" });
    invalido({ reservaId: "reservas/otra", respuesta: "ACEPTAR" });
    invalido({ reservaId: "x".repeat(129), respuesta: "ACEPTAR" });
  });
});
