import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseCancelar, reembolsoSegunPolitica } from "../../src/cancelarReserva.js";
import { esperarErrorSync } from "../helpers.js";

function invalido(data: unknown) {
  esperarErrorSync(() => parseCancelar(data), "invalid-argument", "DATOS_INVALIDOS");
}
function motivoInvalido(data: unknown) {
  esperarErrorSync(() => parseCancelar(data), "invalid-argument", "MOTIVO_INVALIDO");
}

describe("parseCancelar", () => {
  it("acepta reservaId sin motivo", () => {
    assert.deepEqual(parseCancelar({ reservaId: " r1 " }), { reservaId: "r1", motivo: null });
    assert.deepEqual(parseCancelar({ reservaId: "r1", motivo: null }), { reservaId: "r1", motivo: null });
  });

  it("normaliza el motivo (trim) y trata el vacio como ausente", () => {
    assert.deepEqual(parseCancelar({ reservaId: "r1", motivo: "  Tengo un imprevisto " }), {
      reservaId: "r1",
      motivo: "Tengo un imprevisto",
    });
    assert.equal(parseCancelar({ reservaId: "r1", motivo: "   " }).motivo, null);
  });

  it("acepta un motivo de exactamente 300 caracteres y rechaza 301", () => {
    assert.equal(parseCancelar({ reservaId: "r1", motivo: "a".repeat(300) }).motivo?.length, 300);
    motivoInvalido({ reservaId: "r1", motivo: "a".repeat(301) });
  });

  it("rechaza un motivo que no es texto", () => {
    motivoInvalido({ reservaId: "r1", motivo: 42 });
    motivoInvalido({ reservaId: "r1", motivo: {} });
  });

  it("ignora claves desconocidas (estado, quien cancela)", () => {
    assert.deepEqual(parseCancelar({ reservaId: "r1", estado: "CANCELADA_PROFESIONAL", rol: "PROFESIONAL" }), {
      reservaId: "r1",
      motivo: null,
    });
  });

  it("rechaza cuerpo no objeto e ids invalidos", () => {
    invalido(null);
    invalido([]);
    invalido({});
    invalido({ reservaId: "a/b" });
    invalido({ reservaId: 7 });
  });
});

describe("reembolsoSegunPolitica", () => {
  const ahora = new Date("2026-10-01T12:00:00Z");
  const en = (horas: number) => new Date(ahora.getTime() + horas * 3_600_000);

  it("el profesional siempre reembolsa total, incluso minutos antes", () => {
    assert.equal(reembolsoSegunPolitica("PROFESIONAL", en(0.1), ahora), "TOTAL");
    assert.equal(reembolsoSegunPolitica("PROFESIONAL", en(100), ahora), "TOTAL");
  });

  it("el cliente con 24 h o mas recibe reembolso total (borde inclusivo)", () => {
    assert.equal(reembolsoSegunPolitica("CLIENTE", en(24), ahora), "TOTAL");
    assert.equal(reembolsoSegunPolitica("CLIENTE", en(72), ahora), "TOTAL");
  });

  it("el cliente con menos de 24 h no tiene reembolso automatico", () => {
    assert.equal(reembolsoSegunPolitica("CLIENTE", new Date(en(24).getTime() - 1), ahora), "NINGUNO");
    assert.equal(reembolsoSegunPolitica("CLIENTE", en(2), ahora), "NINGUNO");
  });
});
