import { FieldValue, type Firestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { anularEntradaSiHayPagoConTarjeta, aplicarResultadoAEntradas, leerEntradas } from "./comisiones.js";
import { COLECCION_COBROS_COMISION, COLECCION_COMISIONES, COLECCION_PAGOS, ESQUEMA_APP, ESTADOS_RESERVA_SIN_ATENCION } from "./constantes.js";
import { aplicarCobroComision } from "./cobrosComision.js";
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

type TipoMetodo = "TARJETA_CREDITO" | "TARJETA_DEBITO" | "TRANSFERENCIA" | "DINERO_EN_CUENTA";

export function metodoDesde(pago: PagoMP): { tipo: TipoMetodo; ultimosDigitos: string | null } | null {
  switch (pago.tipo) {
    case "credit_card":
      return { tipo: "TARJETA_CREDITO", ultimosDigitos: pago.ultimosDigitos };
    case "debit_card":
      return { tipo: "TARJETA_DEBITO", ultimosDigitos: pago.ultimosDigitos };
    case "bank_transfer":
      return { tipo: "TRANSFERENCIA", ultimosDigitos: null };
    case "account_money":
      return { tipo: "DINERO_EN_CUENTA", ultimosDigitos: null };
    default:
      return null;
  }
}

/**
 * Aplica a Firestore lo que Mercado Pago reporta de un pago. Es idempotente y tolera
 * notificaciones repetidas o desordenadas (ver `transicionValida`). Devuelve el estado final
 * del pago. Un pago que Mercado Pago da por aprobado con un monto distinto al cobrado NO se
 * autoriza: se deja constancia en el log para revisarlo a mano.
 *
 * Pago tardio: si el cobro se aprueba cuando la reserva ya fue cancelada o rechazada (el cliente
 * dejo abierta la preferencia de Checkout Pro y pago despues), el pago se registra `AUTORIZADO`
 * porque el dinero si se cobro, pero queda con reembolso TOTAL pendiente (`reembolsoMotivo:
 * "PAGO_TARDIO"`), sin importar la politica de anticipacion: cobrar algo que nadie va a atender
 * nunca corresponde. Cancelar y aplicar el pago leen y escriben ambos documentos en transaccion,
 * asi que se serializan: gana uno y el otro ve el resultado (si cancela primero, esto lo marca;
 * si el pago entra primero, `cancelarReserva` ve `AUTORIZADO` y aplica su politica).
 *
 * Comision adeudada: las entradas que el pago descontaba (`entradasSaldo`) pasan a `PAGADA` al
 * autorizarse y se liberan si se rechaza. Si la atencion ya estaba completada (comision en efectivo
 * pendiente) y el pago con tarjeta llega tarde, esa entrada se anula: el fee ya cobra la comision.
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
    const entradas = await leerEntradas(tx, db, (pago.get("entradasSaldo") as string[] | undefined) ?? []);
    const entradaDeLaReserva = await tx.get(db.collection(COLECCION_COMISIONES).doc(pago.get("reservaId") as string));

    const pagoTardio =
      nuevo === "AUTORIZADO" &&
      reserva.exists &&
      (ESTADOS_RESERVA_SIN_ATENCION as readonly string[]).includes(reserva.get("estado") as string);
    if (pagoTardio) {
      logger.warn("Mercado Pago: pago aprobado sobre una reserva sin atencion; queda con reembolso pendiente", {
        pagoId,
        reservaId: reservaRef.id,
        estadoReserva: reserva.get("estado"),
      });
    }

    tx.update(pagoRef, {
      estado: nuevo,
      idTransaccionPasarela: pagoMP.id,
      metodo: metodoDesde(pagoMP),
      ...(pagoTardio
        ? {
            reembolsoPendiente: true,
            reembolsoPolitica: "TOTAL",
            reembolsoMotivo: "PAGO_TARDIO",
            reembolsoSolicitadoEn: FieldValue.serverTimestamp(),
          }
        : {}),
      actualizadoEn: FieldValue.serverTimestamp(),
    });
    aplicarResultadoAEntradas(tx, entradas, pagoId, nuevo, "DESCUENTO");
    if (nuevo === "AUTORIZADO") anularEntradaSiHayPagoConTarjeta(tx, entradaDeLaReserva);
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

  // Pago de comision adeudada: va a la cuenta de la plataforma, no a la de un profesional.
  const cobroId = peticion.query.cobroId;
  if (typeof cobroId === "string" && ID_SEGURO.test(cobroId)) {
    return webhookCobroComision(db, deps, cobroId, dataId);
  }

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

async function webhookCobroComision(db: Firestore, deps: DepsPago, cobroId: string, dataId: string): Promise<RespuestaHttp> {
  try {
    if (deps.tokenPlataforma === undefined || deps.tokenPlataforma === "") {
      logger.error("Mercado Pago: falta el Access Token de la plataforma para procesar un cobro de comision", { cobroId });
      return { status: 500 };
    }
    const cobro = await db.collection(COLECCION_COBROS_COMISION).doc(cobroId).get();
    if (!cobro.exists) return { status: 200 }; // notificacion de otro ambiente o cobro desconocido
    const pagoMP = await deps.pasarela.obtenerPago(deps.tokenPlataforma, dataId);
    await aplicarCobroComision(db, cobroId, pagoMP);
    return { status: 200 };
  } catch (e) {
    logger.error("Mercado Pago: fallo procesar webhook de cobro de comision", {
      cobroId,
      mensaje: e instanceof Error ? e.message : String(e),
    });
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
 * lo consulta la app a `estadoPago`, jamas se confia en los parametros de esta URL. Con
 * `?tipo=comision` (pago de comision adeudada) vuelve a `kinecare://comision/resultado`, sin ids.
 */
export function retornoPagoHandler(query: Record<string, unknown>): RespuestaHttp {
  if (query.tipo === "comision") return { status: 302, redirect: `${ESQUEMA_APP}://comision/resultado` };
  const externa = query.external_reference;
  const destino =
    typeof externa === "string" && ID_SEGURO.test(externa)
      ? `${ESQUEMA_APP}://pago/resultado?pagoId=${externa}`
      : `${ESQUEMA_APP}://pago/resultado`;
  return { status: 302, redirect: destino };
}
