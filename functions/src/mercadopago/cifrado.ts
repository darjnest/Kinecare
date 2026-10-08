import { createCipheriv, createDecipheriv, randomBytes } from "node:crypto";

const ALGORITMO = "aes-256-gcm";
const LARGO_CLAVE = 32;
const LARGO_IV = 12;
const VERSION = "v1";

/** Decodifica la clave base64 de `MP_TOKEN_ENCRYPTION_KEY`; exige exactamente 32 bytes. */
export function parseClave(base64: string): Buffer {
  const clave = Buffer.from(base64.trim(), "base64");
  if (clave.length !== LARGO_CLAVE) {
    throw new Error(`MP_TOKEN_ENCRYPTION_KEY debe decodificar a ${LARGO_CLAVE} bytes (base64)`);
  }
  return clave;
}

/**
 * Cifra con AES-256-GCM. `contexto` (ej. el id del profesional) se autentica como AAD: un
 * token copiado a la cuenta de otro profesional no descifra.
 * Formato: `v1.<iv>.<tag>.<texto cifrado>` en base64url.
 */
export function cifrar(texto: string, clave: Buffer, contexto: string): string {
  const iv = randomBytes(LARGO_IV);
  const cifrador = createCipheriv(ALGORITMO, clave, iv);
  cifrador.setAAD(Buffer.from(contexto, "utf8"));
  const cifrado = Buffer.concat([cifrador.update(texto, "utf8"), cifrador.final()]);
  return [VERSION, iv, cifrador.getAuthTag(), cifrado].map((p) => (typeof p === "string" ? p : p.toString("base64url"))).join(".");
}

export function descifrar(valor: string, clave: Buffer, contexto: string): string {
  const [version, iv, tag, cifrado, ...resto] = valor.split(".");
  if (version !== VERSION || iv === undefined || tag === undefined || cifrado === undefined || resto.length > 0) {
    throw new Error("formato de token cifrado desconocido");
  }
  const descifrador = createDecipheriv(ALGORITMO, clave, Buffer.from(iv, "base64url"));
  descifrador.setAAD(Buffer.from(contexto, "utf8"));
  descifrador.setAuthTag(Buffer.from(tag, "base64url"));
  return Buffer.concat([descifrador.update(Buffer.from(cifrado, "base64url")), descifrador.final()]).toString("utf8");
}
