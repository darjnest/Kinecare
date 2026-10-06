import assert from "node:assert/strict";
import { randomBytes } from "node:crypto";
import { describe, it } from "node:test";
import { cifrar, descifrar, parseClave } from "../../src/mercadopago/cifrado.js";

const clave = randomBytes(32);

describe("cifrado de tokens", () => {
  it("descifra lo que cifra y no deja el texto en claro", () => {
    const secreto = "APP_USR-token-secreto";
    const cifrado = cifrar(secreto, clave, "pro1");
    assert.ok(!cifrado.includes(secreto));
    assert.equal(descifrar(cifrado, clave, "pro1"), secreto);
  });

  it("dos cifrados del mismo texto difieren (IV aleatorio)", () => {
    assert.notEqual(cifrar("x", clave, "pro1"), cifrar("x", clave, "pro1"));
  });

  it("no descifra con otro contexto: un token copiado a otro profesional falla", () => {
    assert.throws(() => descifrar(cifrar("x", clave, "pro1"), clave, "pro2"));
  });

  it("no descifra con otra clave ni si el texto fue alterado", () => {
    const cifrado = cifrar("x", clave, "pro1");
    assert.throws(() => descifrar(cifrado, randomBytes(32), "pro1"));
    const [v, iv, tag, ct] = cifrado.split(".");
    assert.throws(() => descifrar([v, iv, tag, `${ct}A`].join("."), clave, "pro1"));
  });

  it("rechaza formatos desconocidos", () => {
    assert.throws(() => descifrar("v2.a.b.c", clave, "pro1"));
    assert.throws(() => descifrar("basura", clave, "pro1"));
  });

  it("parseClave exige exactamente 32 bytes en base64", () => {
    assert.equal(parseClave(clave.toString("base64")).length, 32);
    assert.throws(() => parseClave(randomBytes(16).toString("base64")));
    assert.throws(() => parseClave(""));
  });
});
