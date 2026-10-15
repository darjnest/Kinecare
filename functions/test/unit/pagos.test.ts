import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseIniciarPago } from "../../src/iniciarPago.js";
import { metodoDesde, retornoPagoHandler } from "../../src/pagos.js";
import type { PagoMP } from "../../src/mercadopago/pasarela.js";
import { esperarErrorSync } from "../helpers.js";

describe("parseIniciarPago", () => {
  it("solo toma reservaId e ignora monto, comision y vendedor", () => {
    assert.deepEqual(parseIniciarPago({ reservaId: " r1 ", monto: 1, comision: 0, profesionalId: "otro" }), { reservaId: "r1" });
  });

  it("acepta un medio de pago valido y lo ignora si viene null", () => {
    assert.deepEqual(parseIniciarPago({ reservaId: "r1", medio: "DEBITO" }), { reservaId: "r1", medio: "DEBITO" });
    assert.deepEqual(parseIniciarPago({ reservaId: "r1", medio: null }), { reservaId: "r1" });
  });

  it("rechaza un medio desconocido", () => {
    for (const medio of ["EFECTIVO", "credit_card", "", 3, {}, "toString", "__proto__"]) {
      esperarErrorSync(() => parseIniciarPago({ reservaId: "r1", medio }), "invalid-argument", "DATOS_INVALIDOS");
    }
  });

  it("rechaza cuerpos y ids invalidos", () => {
    for (const d of [null, "r1", [], {}, { reservaId: "" }, { reservaId: 7 }, { reservaId: "a/b" }]) {
      esperarErrorSync(() => parseIniciarPago(d), "invalid-argument", "DATOS_INVALIDOS");
    }
  });
});

describe("metodoDesde", () => {
  const pago = (tipo: string | null): PagoMP => ({ id: "1", status: "approved", referenciaExterna: "p", monto: 1, tipo, ultimosDigitos: "4321" });

  it("distingue credito, debito, transferencia y dinero en cuenta", () => {
    assert.deepEqual(metodoDesde(pago("credit_card")), { tipo: "TARJETA_CREDITO", ultimosDigitos: "4321" });
    assert.deepEqual(metodoDesde(pago("debit_card")), { tipo: "TARJETA_DEBITO", ultimosDigitos: "4321" });
    assert.deepEqual(metodoDesde(pago("bank_transfer")), { tipo: "TRANSFERENCIA", ultimosDigitos: null });
    assert.deepEqual(metodoDesde(pago("account_money")), { tipo: "DINERO_EN_CUENTA", ultimosDigitos: null });
  });

  it("devuelve null para tipos que KineCare no ofrece o desconocidos", () => {
    for (const tipo of ["ticket", "atm", "prepaid_card", "otro", null]) assert.equal(metodoDesde(pago(tipo)), null);
  });
});

describe("retornoPagoHandler", () => {
  it("redirige a la app solo con el pagoId", () => {
    assert.deepEqual(retornoPagoHandler({ external_reference: "abc123", collection_status: "approved", payment_id: "9" }), {
      status: 302,
      redirect: "kinecare://pago/resultado?pagoId=abc123",
    });
  });

  it("descarta referencias con caracteres peligrosos (no se inyecta nada en el deep link)", () => {
    for (const ref of ["a&x=1", "a/b", "a b", "../x", "", undefined, ["a"]]) {
      assert.equal(retornoPagoHandler({ external_reference: ref }).redirect, "kinecare://pago/resultado");
    }
  });
});
