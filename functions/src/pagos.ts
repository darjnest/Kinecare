import { FieldValue, type Firestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { COLECCION_PAGOS, ESQUEMA_APP } from "./constantes.js";
import { errorDeNegocio } from "./errores.js";
import type { RespuestaHttp } from "./conectarMercadoPago.js";
import type { DepsPago } from "./iniciarPago.js";
import { tokenVigente } from "./mercadopago/cuentas.js";
import { estadoDesdeMP, transicionValida, type EstadoPago } from "./mercadopago/estados.js";
import { firmaWebhookValida } from "./mercadopago/firma.js";
import type { PagoMP } from "./mercadopago/pasarela.js";
import { ID_SEGURO, esObjeto, idObligatorio, invalido } from "./validacion.js";

function pagoNoEncontrado() {
  return errorDeNegocio("not-found", "PAGO_NO_ENCONTRADO", "No encontramos ese pago.");
}

function metodoDesde(pago: PagoMP): { tipo: "TARJETA" | "TRANSFERENCIA"; ultimosDigitos: string | null } | null {
  if (pago.tipo === "credit_card" || pago.tipo === "debit_card") {
    return { tipo: "TARJETA", ultimosDigitos: pago.ultimosDigitos };
  }
  if (pago.tipo === "bank_transfer") return { tipo: "TRANSFERENCIA", ultimosDigitos: null };
  return null;
}

/**
 * Aplica a Firestore lo que Mercado Pago reporta de un pago. Es idempotente y tolera
 * notificaciones repetidas o desordenadas (ver `transicionValida`). Devuelve el estado final
 * del pago. Un pago que Mercado Pago da por aprobado con un monto distinto al cobrado NO se
 * autoriza: se deja constancia en el log para revisarlo a mano.
 */
export async function aplicarPago(db: Firestore, pagoId: string, pagoMP: PagoMP): Promise<EstadoPago> {
  const pagoRef = db.collection(COLECCION_PAGOS).doc(pagoId);
  return db.runTransaction(async (tx) => {
    const pago = await tx.get(pagoRef);
    if (!pago.exists) throw pagoNoEncontrado();
    const actual = pago.get("estado") as EstadoPago;

    if (pagoMP.referenciaExterna !== pagoId) {
      logger.error("Mercado Pago: external_reference no coincide con el pago", { pagoId, recibido: pagoMP.referenciaExterna });
      return actual;
    }
    const nuevo = estadoDesdeMP(pagoMP.status);
    if (nuevo === "AUTORIZADO" && pagoMP.monto !== pago.get("monto")) {
      logger.error("Mercado Pago: monto aprobado distinto al cobrado; no se autoriza", {
        pagoId,
        esperado: pago.get("monto"),
        recibido: pagoMP.monto,
      });
      return actual;
    }
    if (!transicionValida(actual, nuevo)) return actual;

    const reservaRef = db.collection("reservas").doc(pago.get("reservaId") as string);
    const reserva = await tx.get(reservaRef);

    tx.update(pagoRef, {
      estado: nuevo,
      idTransaccionPasarela: pagoMP.id,
      metodo: metodoDesde(pagoMP),
      actualizadoEn: FieldValue.serverTimestamp(),
    });
    // Un intento viejo (otro pagoId) no debe pisar el estado del intento vigente.
    if (reserva.exists && (reserva.get("pago") as { id?: string } | undefined)?.id === pagoId) {
      tx.update(reservaRef, { "pago.estado": nuevo, actualizadoEn: FieldValue.serverTimestamp() });
    }
    return nuevo;
  });
}

export interface PeticionWebhook {
  headers: Record<string, string | string[] | undefined>;
  /** Query string: `pagoId` (lo pusimos en `notification_url`) y `data.id`. */
  query: Record<string, unknown>;
  body: unknown;
}

function cabecera(headers: PeticionWebhook["headers"], nombre: string): string | undefined {
  const valor = headers[nombre];
  return Array.isArray(valor) ? valor[0] : valor;
}

/**
 * `webhookMercadoPago`. Orden: (1) validar la firma ANTES de mirar el contenido, (2) leer el
 * pago real a Mercado Pago con el token del vendedor (la notificacion solo trae un id; nada de
 * ella se toma como verdad), (3) aplicarlo. A diferencia de un servidor tradicional, en Cloud
 * Functions la CPU se congela al responder, asi que se procesa ANTES de contestar 200; si falla
 * algo transitorio se responde 500 para que Mercado Pago reintente (el proceso es idempotente).
 */
export async function webhookHandler(
  db: Firestore,
  deps: DepsPago,
  secreto: string,
  peticion: PeticionWebhook,
  ahora: Date,
): Promise<RespuestaHttp> {
  const cuerpo = esObjeto(peticion.body) ? peticion.body : {};
  const dataIdCuerpo = esObjeto(cuerpo.data) && cuerpo.data.id !== undefined ? String(cuerpo.data.id) : undefined;
  const dataIdQuery = typeof peticion.query["data.id"] === "string" ? peticion.query["data.id"] : undefined;
  const dataId = dataIdQuery ?? dataIdCuerpo;

  if (
    !firmaWebhookValida({
      xSignature: cabecera(peticion.headers, "x-signature"),
      xRequestId: cabecera(peticion.headers, "x-request-id"),
      dataId,
      secreto,
    })
  ) {
    // Diagnostico sin datos sensibles: que llego y de que largo es el secreto configurado
    // (la clave de Webhooks del panel mide 64 caracteres; otro largo = secreto mal cargado).
    logger.warn("Mercado Pago: firma de webhook invalida", {
      tieneFirma: cabecera(peticion.headers, "x-signature") !== undefined,
      tieneRequestId: cabecera(peticion.headers, "x-request-id") !== undefined,
      dataIdDesde: dataIdQuery !== undefined ? "query" : dataIdCuerpo !== undefined ? "cuerpo" : "ninguno",
      largoSecreto: secreto.length,
    });
    return { status: 401 };
  }

  // Solo interesan los pagos; el resto de los temas se acusa recibo sin hacer nada.
  const tipo = cuerpo.type ?? peticion.query.type;
  if (tipo !== "payment" || dataId === undefined) return { status: 200 };

  const pagoId = peticion.query.pagoId;
  if (typeof pagoId !== "string" || !ID_SEGURO.test(pagoId)) return { status: 200 };

  try {
    const pago = await db.collection(COLECCION_PAGOS).doc(pagoId).get();
    if (!pago.exists) return { status: 200 }; // notificacion de otro ambiente o pago desconocido
    const token = await tokenVigente(db, deps.pasarela, deps.clave, pago.get("profesionalId") as string, ahora);
    const pagoMP = await deps.pasarela.obtenerPago(token, dataId);
    await aplicarPago(db, pagoId, pagoMP);
    return { status: 200 };
  } catch (e) {
    logger.error("Mercado Pago: fallo procesar webhook", { pagoId, mensaje: e instanceof Error ? e.message : String(e) });
    return { status: 500 };
  }
}

export interface ResultadoEstadoPago {
  estado: EstadoPago;
}

/**
 * `estadoPago`: la app lo llama al volver de Mercado Pago. Si el pago sigue `PENDIENTE` relee
 * Mercado Pago (por si el webhook aun no llega); nunca devuelve algo que no venga de Firestore o
 * de Mercado Pago. Solo el cliente o el profesional de la reserva pueden consultarlo.
 */
export async function estadoPagoHandler(
  db: Firestore,
  deps: DepsPago,
  uid: string | undefined,
  data: unknown,
  ahora: Date,
): Promise<ResultadoEstadoPago> {
  if (uid === undefined || uid === "") {
    throw errorDeNegocio("unauthenticated", "SIN_SESION", "Debes iniciar sesión para consultar el pago.");
  }
  if (!esObjeto(data)) throw invalido("el cuerpo debe ser un objeto");
  const pagoId = idObligatorio(data.pagoId, "pagoId");

  const pago = await db.collection(COLECCION_PAGOS).doc(pagoId).get();
  if (!pago.exists || (pago.get("clienteId") !== uid && pago.get("profesionalId") !== uid)) throw pagoNoEncontrado();

  const estado = pago.get("estado") as EstadoPago;
  if (estado !== "PENDIENTE") return { estado };

  const token = await tokenVigente(db, deps.pasarela, deps.clave, pago.get("profesionalId") as string, ahora);
  const pagoMP = await deps.pasarela.buscarPagoPorReferencia(token, pagoId);
  if (pagoMP === null) return { estado };
  return { estado: await aplicarPago(db, pagoId, pagoMP) };
}

/**
 * `retornoPago`: Mercado Pago solo acepta `back_urls` HTTPS, asi que vuelve aqui y esta
 * funcion salta a la app por deep link. Solo reenvia el `pagoId` (validado): el resultado real
 * lo consulta la app a `estadoPago`, jamas se confia en los parametros de esta URL.
 */
export function retornoPagoHandler(query: Record<string, unknown>): RespuestaHttp {
  const externa = query.external_reference;
  const destino =
    typeof externa === "string" && ID_SEGURO.test(externa)
      ? `${ESQUEMA_APP}://pago/resultado?pagoId=${externa}`
      : `${ESQUEMA_APP}://pago/resultado`;
  return { status: 302, redirect: destino };
}
