import { createHmac, timingSafeEqual } from "node:crypto";
import { TOLERANCIA_FIRMA_DIDIT_SEGUNDOS } from "../constantes.js";

export interface DatosFirmaDidit {
  /** Bytes exactos recibidos (`req.rawBody`). Sin ellos no hay forma de verificar `X-Signature`. */
  rawBody: Buffer | string | undefined;
  /** Cabecera `X-Signature` (HMAC-SHA256 hex sobre los bytes crudos). */
  xSignature: string | undefined;
  /** Cabecera `X-Signature-V2` (HMAC-SHA256 hex sobre el JSON canonico). */
  xSignatureV2: string | undefined;
  /** Cabecera `X-Timestamp` (epoch en segundos). */
  xTimestamp: string | undefined;
  /** `secret_shared_key` del destino de webhook en la consola de Didit. */
  secreto: string;
  ahora: Date;
  toleranciaSegundos?: number;
}

function puntoDeCodigo(texto: string, i: number): number {
  return texto.codePointAt(i) ?? 0;
}

/** Orden por punto de codigo Unicode (como `sort_keys` de Python), no por unidad UTF-16 como `Array.sort`. */
function compararClaves(a: string, b: string): number {
  const ta = Array.from(a);
  const tb = Array.from(b);
  const n = Math.min(ta.length, tb.length);
  for (let i = 0; i < n; i++) {
    const d = puntoDeCodigo(ta[i], 0) - puntoDeCodigo(tb[i], 0);
    if (d !== 0) return d;
  }
  return ta.length - tb.length;
}

/**
 * JSON canonico de Didit para `X-Signature-V2`: claves ordenadas recursivamente, separadores compactos
 * (`,` y `:` sin espacios), Unicode sin escapar y floats enteros escritos como enteros (`1.0` -> `1`).
 *
 * Limitacion conocida: los floats no enteros se escriben con la representacion de JavaScript, que
 * difiere de la de Python en exponentes (`1e-7` vs `1e-07`); y los enteros mayores a 2^53 ya perdieron
 * precision al parsear. Ninguno aparece en un evento de estado; ademas `X-Signature` (bytes crudos) se
 * prueba antes y no depende de esto.
 */
export function canonizarJson(valor: unknown): string {
  if (valor === null) return "null";
  switch (typeof valor) {
    case "boolean":
      return valor ? "true" : "false";
    case "number":
      if (!Number.isFinite(valor)) return "null";
      return Number.isInteger(valor) ? BigInt(valor).toString() : JSON.stringify(valor);
    case "string":
      return JSON.stringify(valor);
    case "object": {
      if (Array.isArray(valor)) return `[${valor.map(canonizarJson).join(",")}]`;
      const o = valor as Record<string, unknown>;
      const partes = Object.keys(o)
        .filter((k) => o[k] !== undefined)
        .sort(compararClaves)
        .map((k) => `${JSON.stringify(k)}:${canonizarJson(o[k])}`);
      return `{${partes.join(",")}}`;
    }
    default:
      return "null";
  }
}

/** Comparacion en tiempo constante; longitudes distintas son simplemente "no coincide" (timingSafeEqual lanzaria). */
function igualesSeguro(esperadoHex: string, recibido: string | undefined): boolean {
  if (recibido === undefined) return false;
  const a = Buffer.from(esperadoHex);
  const b = Buffer.from(recibido.trim().toLowerCase());
  return a.length === b.length && timingSafeEqual(a, b);
}

const hmac = (secreto: string, datos: Buffer | string) => createHmac("sha256", secreto).update(datos).digest("hex");

/**
 * Valida un webhook de Didit. Rechaza si el `X-Timestamp` esta fuera de ventana (anti-replay); luego
 * acepta si coincide `X-Signature` (sobre los bytes crudos) O `X-Signature-V2` (sobre el JSON
 * canonico). `X-Signature-Simple` esta obsoleta y no se mira. Cualquier dato ausente invalida.
 */
export function firmaDiditValida({
  rawBody,
  xSignature,
  xSignatureV2,
  xTimestamp,
  secreto,
  ahora,
  toleranciaSegundos = TOLERANCIA_FIRMA_DIDIT_SEGUNDOS,
}: DatosFirmaDidit): boolean {
  if (secreto === "" || rawBody === undefined) return false;
  if (xTimestamp === undefined || !/^\d{1,12}$/.test(xTimestamp.trim())) return false;
  if (Math.abs(ahora.getTime() / 1000 - Number(xTimestamp.trim())) > toleranciaSegundos) return false;

  if (igualesSeguro(hmac(secreto, rawBody), xSignature)) return true;

  if (xSignatureV2 !== undefined) {
    let parseado: unknown;
    try {
      parseado = JSON.parse(typeof rawBody === "string" ? rawBody : rawBody.toString("utf8"));
    } catch {
      return false;
    }
    return igualesSeguro(hmac(secreto, canonizarJson(parseado)), xSignatureV2);
  }
  return false;
}
