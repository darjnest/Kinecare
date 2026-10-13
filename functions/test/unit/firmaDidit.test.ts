import assert from "node:assert/strict";
import { createHmac } from "node:crypto";
import { describe, it } from "node:test";
import { canonizarJson, firmaDiditValida } from "../../src/verificacion/firmaDidit.js";

const SECRETO = "secreto-didit-de-prueba";
const AHORA = new Date("2026-10-01T12:00:00Z");
const TS = String(Math.floor(AHORA.getTime() / 1000));
const hmac = (datos: string | Buffer, secreto = SECRETO) => createHmac("sha256", secreto).update(datos).digest("hex");

// Cuerpo con claves desordenadas, anidados, un float entero, Unicode y un caracter de control.
const CRUDO = '{ "b": 1.0, "a": {"z": "ñ", "d": [3, {"y": true, "x": null}]}, "c": "é\\u0001" }';
// Forma canonica escrita a mano (lo que produciria json.dumps(sort_keys=True, separators=(",", ":"), ensure_ascii=False)).
const CANONICO = '{"a":{"d":[3,{"x":null,"y":true}],"z":"ñ"},"b":1,"c":"é\\u0001"}';

describe("canonizarJson", () => {
  it("ordena claves recursivamente, compacta, no escapa Unicode y escribe floats enteros como enteros", () => {
    assert.equal(canonizarJson(JSON.parse(CRUDO)), CANONICO);
  });

  it("no altera el orden de los arreglos y representa null/booleanos", () => {
    assert.equal(canonizarJson({ l: [true, false, null, "b", "a"] }), '{"l":[true,false,null,"b","a"]}');
  });

  it("ordena por punto de codigo Unicode (no por unidad UTF-16): U+FFFF va antes que U+1F600", () => {
    assert.equal(canonizarJson({ "\u{1F600}": 1, "￿": 2 }), '{"￿":2,"\u{1F600}":1}');
  });

  it("escribe floats enteros grandes sin notacion cientifica y conserva los decimales", () => {
    assert.equal(canonizarJson({ n: 1e21, f: 0.5, e: 2.0 }), '{"e":2,"f":0.5,"n":1000000000000000000000}');
  });
});

describe("firmaDiditValida", () => {
  const base = { secreto: SECRETO, ahora: AHORA, xTimestamp: TS };

  it("acepta X-Signature calculada sobre los bytes crudos", () => {
    assert.ok(firmaDiditValida({ ...base, rawBody: Buffer.from(CRUDO), xSignature: hmac(CRUDO), xSignatureV2: undefined }));
  });

  it("acepta X-Signature-V2 sobre el JSON canonico aunque el cuerpo crudo tenga otro formato", () => {
    assert.ok(firmaDiditValida({ ...base, rawBody: CRUDO, xSignature: "no-coincide", xSignatureV2: hmac(CANONICO) }));
  });

  it("acepta firma en mayusculas hexadecimales", () => {
    assert.ok(firmaDiditValida({ ...base, rawBody: CRUDO, xSignature: hmac(CRUDO).toUpperCase(), xSignatureV2: undefined }));
  });

  it("rechaza una firma invalida (ambas)", () => {
    assert.ok(!firmaDiditValida({ ...base, rawBody: CRUDO, xSignature: hmac(CRUDO, "otro"), xSignatureV2: hmac(CANONICO, "otro") }));
  });

  it("rechaza si el cuerpo fue alterado", () => {
    const alterado = CRUDO.replace('"b": 1.0', '"b": 2.0');
    assert.ok(!firmaDiditValida({ ...base, rawBody: alterado, xSignature: hmac(CRUDO), xSignatureV2: hmac(CANONICO) }));
  });

  it("rechaza firmas de longitud distinta sin lanzar", () => {
    assert.ok(!firmaDiditValida({ ...base, rawBody: CRUDO, xSignature: "abc", xSignatureV2: "abcd".repeat(40) }));
    assert.ok(!firmaDiditValida({ ...base, rawBody: CRUDO, xSignature: "", xSignatureV2: "" }));
  });

  it("rechaza si no llega ninguna firma", () => {
    assert.ok(!firmaDiditValida({ ...base, rawBody: CRUDO, xSignature: undefined, xSignatureV2: undefined }));
  });

  it("rechaza un timestamp fuera de ventana (viejo o futuro), aunque la firma sea correcta", () => {
    const firma = hmac(CRUDO);
    const viejo = String(Math.floor(AHORA.getTime() / 1000) - 301);
    const futuro = String(Math.floor(AHORA.getTime() / 1000) + 301);
    assert.ok(!firmaDiditValida({ ...base, xTimestamp: viejo, rawBody: CRUDO, xSignature: firma, xSignatureV2: undefined }));
    assert.ok(!firmaDiditValida({ ...base, xTimestamp: futuro, rawBody: CRUDO, xSignature: firma, xSignatureV2: undefined }));
  });

  it("acepta el borde de la ventana (300 s)", () => {
    const borde = String(Math.floor(AHORA.getTime() / 1000) - 300);
    assert.ok(firmaDiditValida({ ...base, xTimestamp: borde, rawBody: CRUDO, xSignature: hmac(CRUDO), xSignatureV2: undefined }));
  });

  it("rechaza timestamp ausente o no numerico", () => {
    const firma = hmac(CRUDO);
    for (const ts of [undefined, "", "abc", "1.5e9", "-5"]) {
      assert.ok(!firmaDiditValida({ ...base, xTimestamp: ts, rawBody: CRUDO, xSignature: firma, xSignatureV2: undefined }));
    }
  });

  it("rechaza secreto vacio aunque la firma se haya calculado con secreto vacio", () => {
    assert.ok(!firmaDiditValida({ ...base, secreto: "", rawBody: CRUDO, xSignature: hmac(CRUDO, ""), xSignatureV2: undefined }));
  });

  it("rechaza si no hay cuerpo crudo", () => {
    assert.ok(!firmaDiditValida({ ...base, rawBody: undefined, xSignature: hmac(CRUDO), xSignatureV2: hmac(CANONICO) }));
  });

  it("con V2 y un cuerpo que no es JSON no lanza: rechaza", () => {
    assert.ok(!firmaDiditValida({ ...base, rawBody: "no es json", xSignature: "x", xSignatureV2: hmac("no es json") }));
  });

  it("ignora X-Signature-Simple: una firma solo simple no basta (no es parametro)", () => {
    assert.ok(!firmaDiditValida({ ...base, rawBody: CRUDO, xSignature: undefined, xSignatureV2: undefined }));
  });
});
