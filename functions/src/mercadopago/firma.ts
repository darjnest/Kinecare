import { createHmac, timingSafeEqual } from "node:crypto";

export interface DatosFirma {
  /** Cabecera `x-signature` (`ts=...,v1=...`). */
  xSignature: string | undefined;
  /** Cabecera `x-request-id`. */
  xRequestId: string | undefined;
  /** `data.id` de la notificacion. */
  dataId: string | undefined;
  /** "Clave secreta" del panel de Webhooks. */
  secreto: string;
}

/**
 * Valida la firma HMAC-SHA256 de una notificacion de Mercado Pago. El manifiesto es
 * `id:<data.id>;request-id:<x-request-id>;ts:<ts>;`, con `data.id` en minusculas.
 * Cualquier dato ausente invalida la firma (nunca se procesa una notificacion sin firmar).
 */
export function firmaWebhookValida({ xSignature, xRequestId, dataId, secreto }: DatosFirma): boolean {
  if (!xSignature || !xRequestId || !dataId || secreto === "") return false;
  const partes = new Map<string, string>();
  for (const parte of xSignature.split(",")) {
    const i = parte.indexOf("=");
    if (i > 0) partes.set(parte.slice(0, i).trim(), parte.slice(i + 1).trim());
  }
  const ts = partes.get("ts");
  const v1 = partes.get("v1");
  if (!ts || !v1) return false;

  const manifiesto = `id:${dataId.toLowerCase()};request-id:${xRequestId};ts:${ts};`;
  const esperado = createHmac("sha256", secreto).update(manifiesto).digest("hex");
  const a = Buffer.from(esperado);
  const b = Buffer.from(v1);
  return a.length === b.length && timingSafeEqual(a, b);
}
