import assert from "node:assert/strict";
import { createHmac } from "node:crypto";
import { describe, it } from "node:test";
import { firmaWebhookValida } from "../../src/mercadopago/firma.js";

const SECRETO = "secreto-de-prueba";

function firmar(dataId: string, requestId: string, ts: string, secreto = SECRETO): string {
  const v1 = createHmac("sha256", secreto).update(`id:${dataId};request-id:${requestId};ts:${ts};`).digest("hex");
  return `ts=${ts},v1=${v1}`;
}

describe("firmaWebhookValida", () => {
  it("acepta una firma correcta", () => {
    assert.ok(
      firmaWebhookValida({ xSignature: firmar("123", "req-1", "1700000000"), xRequestId: "req-1", dataId: "123", secreto: SECRETO }),
    );
  });

  it("normaliza data.id a minusculas, como en el manifiesto de Mercado Pago", () => {
    assert.ok(
      firmaWebhookValida({ xSignature: firmar("abc123", "req-1", "1"), xRequestId: "req-1", dataId: "ABC123", secreto: SECRETO }),
    );
  });

  it("rechaza secreto equivocado, id distinto o request-id distinto", () => {
    const firma = firmar("123", "req-1", "1");
    assert.ok(!firmaWebhookValida({ xSignature: firma, xRequestId: "req-1", dataId: "123", secreto: "otro" }));
    assert.ok(!firmaWebhookValida({ xSignature: firma, xRequestId: "req-1", dataId: "124", secreto: SECRETO }));
    assert.ok(!firmaWebhookValida({ xSignature: firma, xRequestId: "req-2", dataId: "123", secreto: SECRETO }));
  });

  it("rechaza si falta cualquier dato o la firma esta mal formada", () => {
    const firma = firmar("123", "req-1", "1");
    assert.ok(!firmaWebhookValida({ xSignature: undefined, xRequestId: "req-1", dataId: "123", secreto: SECRETO }));
    assert.ok(!firmaWebhookValida({ xSignature: firma, xRequestId: undefined, dataId: "123", secreto: SECRETO }));
    assert.ok(!firmaWebhookValida({ xSignature: firma, xRequestId: "req-1", dataId: undefined, secreto: SECRETO }));
    assert.ok(!firmaWebhookValida({ xSignature: firma, xRequestId: "req-1", dataId: "123", secreto: "" }));
    assert.ok(!firmaWebhookValida({ xSignature: "ts=1", xRequestId: "req-1", dataId: "123", secreto: SECRETO }));
    assert.ok(!firmaWebhookValida({ xSignature: "v1=abc", xRequestId: "req-1", dataId: "123", secreto: SECRETO }));
    assert.ok(!firmaWebhookValida({ xSignature: "ts=1,v1=corta", xRequestId: "req-1", dataId: "123", secreto: SECRETO }));
  });
});
