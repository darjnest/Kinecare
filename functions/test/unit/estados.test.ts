import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { calcularComision, estadoDesdeMP, transicionValida } from "../../src/mercadopago/estados.js";
import { esperarErrorSync } from "../helpers.js";

describe("estadoDesdeMP", () => {
  it("traduce los status de Mercado Pago", () => {
    assert.equal(estadoDesdeMP("approved"), "AUTORIZADO");
    assert.equal(estadoDesdeMP("rejected"), "RECHAZADO");
    assert.equal(estadoDesdeMP("cancelled"), "RECHAZADO");
    assert.equal(estadoDesdeMP("refunded"), "REEMBOLSADO");
    assert.equal(estadoDesdeMP("charged_back"), "REEMBOLSADO");
    assert.equal(estadoDesdeMP("pending"), "PENDIENTE");
    assert.equal(estadoDesdeMP("in_process"), "PENDIENTE");
    assert.equal(estadoDesdeMP(undefined), "PENDIENTE");
    assert.equal(estadoDesdeMP("algo_nuevo"), "PENDIENTE");
  });
});

describe("transicionValida", () => {
  it("un pago autorizado solo puede reembolsarse", () => {
    assert.ok(transicionValida("AUTORIZADO", "REEMBOLSADO"));
    assert.ok(!transicionValida("AUTORIZADO", "RECHAZADO"));
    assert.ok(!transicionValida("AUTORIZADO", "PENDIENTE"));
  });

  it("reembolsado es terminal", () => {
    for (const n of ["PENDIENTE", "AUTORIZADO", "RECHAZADO"] as const) assert.ok(!transicionValida("REEMBOLSADO", n));
  });

  it("un intento rechazado puede reintentarse y aprobarse", () => {
    assert.ok(transicionValida("RECHAZADO", "AUTORIZADO"));
    assert.ok(transicionValida("RECHAZADO", "PENDIENTE"));
    assert.ok(!transicionValida("RECHAZADO", "REEMBOLSADO"));
  });

  it("pendiente pasa a cualquier estado y el mismo estado no es transicion", () => {
    for (const n of ["AUTORIZADO", "RECHAZADO", "REEMBOLSADO"] as const) assert.ok(transicionValida("PENDIENTE", n));
    assert.ok(!transicionValida("PENDIENTE", "PENDIENTE"));
  });
});

describe("calcularComision", () => {
  it("redondea a CLP enteros", () => {
    assert.equal(calcularComision(25000, 0.1), 2500);
    assert.equal(calcularComision(12345, 0.1), 1235);
  });

  it("rechaza montos no enteros, cero o negativos", () => {
    for (const m of [0, -1, 100.5, Number.NaN, Number.POSITIVE_INFINITY]) {
      esperarErrorSync(() => calcularComision(m, 0.1), "failed-precondition", "MONTO_INVALIDO");
    }
  });

  it("rechaza comisiones fuera de rango o que igualan al monto", () => {
    esperarErrorSync(() => calcularComision(1000, -0.1), "failed-precondition", "MONTO_INVALIDO");
    esperarErrorSync(() => calcularComision(1000, 1), "failed-precondition", "MONTO_INVALIDO");
    esperarErrorSync(() => calcularComision(1, 0.9), "failed-precondition", "MONTO_INVALIDO"); // redondea a 1 == monto
  });
});
