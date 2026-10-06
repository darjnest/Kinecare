import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseIniciarPago } from "../../src/iniciarPago.js";
import { retornoPagoHandler } from "../../src/pagos.js";
import { esperarErrorSync } from "../helpers.js";

describe("parseIniciarPago", () => {
  it("solo toma reservaId e ignora monto, comision y vendedor", () => {
    assert.deepEqual(parseIniciarPago({ reservaId: " r1 ", monto: 1, comision: 0, profesionalId: "otro" }), { reservaId: "r1" });
  });

  it("rechaza cuerpos y ids invalidos", () => {
    for (const d of [null, "r1", [], {}, { reservaId: "" }, { reservaId: 7 }, { reservaId: "a/b" }]) {
      esperarErrorSync(() => parseIniciarPago(d), "invalid-argument", "DATOS_INVALIDOS");
    }
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
