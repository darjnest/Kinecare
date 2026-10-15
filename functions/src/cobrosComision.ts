import { FieldValue, Timestamp, type Firestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { aEntradas, aplicarResultadoAEntradas, consultaPendientes, estaLibre, leerEntradas, reservarEntradas, sumar } from "./comisiones.js";
import { COLECCION_COBROS_COMISION, RESERVA_COMISION_VIGENCIA_HORAS } from "./constantes.js";
import { errorDeNegocio } from "./errores.js";
import type { DepsPago } from "./iniciarPago.js";
import { estadoDesdeMP, transicionValida, type EstadoPago } from "./mercadopago/estados.js";
import type { PagoMP } from "./mercadopago/pasarela.js";

export interface ResultadoPagarSaldoComision {
  cobroId: string;
  initPoint: string;
  /** CLP que se cobran: la suma de las comisiones reservadas para este cobro. */
  monto: number;
}

interface CobroPreparado {
  cobroId: string;
  monto: number;
  initPointExistente: string | null;
}

const MS_HORA = 3_600_000;

/**
 * Logica de `pagarSaldoComision`: el profesional paga voluntariamente las comisiones que adeuda por
 * atenciones en efectivo, con Checkout Pro y **a la cuenta de Mercado Pago de la plataforma** (sin
 * `marketplace_fee`: todo el monto es de KineCare). El monto sale de las entradas `PENDIENTE` libres en
 * Firestore, nunca del cliente (que ni siquiera envia datos). Las entradas quedan reservadas por el cobro
 * para que un descuento en un pago con tarjeta no las cobre a la vez. Un reintento dentro de la vigencia
 * reutiliza el mismo cobro y la misma preferencia (`idempotencyKey = cobroId`).
 */
export async function pagarSaldoComisionHandler(
  db: Firestore,
  deps: DepsPago,
  uid: string | undefined,
  ahora: Date,
): Promise<ResultadoPagarSaldoComision> {
  if (uid === undefined || uid === "") {
    throw errorDeNegocio("unauthenticated", "SIN_SESION", "Debes iniciar sesión para pagar tus comisiones.");
  }
  const usuario = await db.collection("usuarios").doc(uid).get();
  if (!usuario.exists || usuario.get("rol") !== "PROFESIONAL") {
    throw errorDeNegocio("permission-denied", "ROL_INVALIDO", "Solo un profesional puede pagar comisiones.");
  }
  if (deps.tokenPlataforma === undefined || deps.tokenPlataforma === "") {
    logger.error("Mercado Pago: falta el Access Token de la plataforma; no se puede cobrar comisiones");
    throw errorDeNegocio("unavailable", "PASARELA_NO_DISPONIBLE", "No pudimos comunicarnos con Mercado Pago. Intenta de nuevo.");
  }

  const cobros = db.collection(COLECCION_COBROS_COMISION);

  const preparado = await db.runTransaction<CobroPreparado>(async (tx) => {
    // Reintento: un cobro propio todavia vigente y sin resolver se reutiliza.
    const abiertos = await tx.get(cobros.where("profesionalId", "==", uid).where("estado", "==", "PENDIENTE" satisfies EstadoPago));
    const vigente = abiertos.docs.find((doc) => {
      const creado = doc.get("creadoEn");
      return creado instanceof Timestamp && ahora.getTime() - creado.toMillis() < RESERVA_COMISION_VIGENCIA_HORAS * MS_HORA;
    });
    if (vigente !== undefined) {
      return {
        cobroId: vigente.id,
        monto: vigente.get("monto") as number,
        initPointExistente: (vigente.get("initPoint") as string | undefined) ?? null,
      };
    }

    const libres = aEntradas((await tx.get(consultaPendientes(db, uid))).docs).filter((e) => estaLibre(e, ahora));
    const monto = sumar(libres);
    if (monto <= 0) {
      throw errorDeNegocio("failed-precondition", "SIN_SALDO_PENDIENTE", "No tienes comisiones pendientes de pago.");
    }

    const nuevo = cobros.doc();
    tx.set(nuevo, {
      profesionalId: uid,
      monto,
      entradas: libres.map((e) => e.id),
      estado: "PENDIENTE" satisfies EstadoPago,
      idTransaccionPasarela: null,
      preferenceId: null,
      initPoint: null,
      creadoEn: FieldValue.serverTimestamp(),
      actualizadoEn: FieldValue.serverTimestamp(),
    });
    reservarEntradas(tx, libres, nuevo.id);
    return { cobroId: nuevo.id, monto, initPointExistente: null };
  });

  if (preparado.initPointExistente !== null) {
    return { cobroId: preparado.cobroId, initPoint: preparado.initPointExistente, monto: preparado.monto };
  }

  const { preferenceId, initPoint } = await deps.pasarela.crearPreferencia(deps.tokenPlataforma, {
    titulo: "Comisiones KineCare",
    itemId: preparado.cobroId,
    monto: preparado.monto,
    referenciaExterna: preparado.cobroId,
    notificationUrl: `${deps.urlBase}/webhookMercadoPago?cobroId=${preparado.cobroId}`,
    backUrl: `${deps.urlBase}/retornoPago?tipo=comision`,
    claveIdempotencia: preparado.cobroId,
  });

  await cobros.doc(preparado.cobroId).update({ preferenceId, initPoint, actualizadoEn: FieldValue.serverTimestamp() });
  return { cobroId: preparado.cobroId, initPoint, monto: preparado.monto };
}

/**
 * Aplica a Firestore lo que Mercado Pago reporta de un cobro de comision. Mismas defensas que
 * `aplicarPago`: idempotente, tolera notificaciones repetidas o desordenadas, y no autoriza si
 * `external_reference` o el monto no coinciden (queda en el log). Al autorizarse, las comisiones del
 * cobro pasan a `PAGADA`; si se rechaza, se liberan; si se reembolsa, vuelven a `PENDIENTE`.
 */
export async function aplicarCobroComision(db: Firestore, cobroId: string, pagoMP: PagoMP): Promise<EstadoPago> {
  const cobroRef = db.collection(COLECCION_COBROS_COMISION).doc(cobroId);
  return db.runTransaction(async (tx) => {
    const cobro = await tx.get(cobroRef);
    if (!cobro.exists) throw errorDeNegocio("not-found", "PAGO_NO_ENCONTRADO", "No encontramos ese pago.");
    const actual = cobro.get("estado") as EstadoPago;

    if (pagoMP.referenciaExterna !== cobroId) {
      logger.error("Mercado Pago: external_reference no coincide con el cobro de comision", {
        cobroId,
        recibido: pagoMP.referenciaExterna,
      });
      return actual;
    }
    const nuevo = estadoDesdeMP(pagoMP.status);
    if (nuevo === "AUTORIZADO" && pagoMP.monto !== cobro.get("monto")) {
      logger.error("Mercado Pago: monto aprobado distinto al cobrado en un cobro de comision; no se autoriza", {
        cobroId,
        esperado: cobro.get("monto"),
        recibido: pagoMP.monto,
      });
      return actual;
    }
    if (!transicionValida(actual, nuevo)) return actual;

    const entradas = await leerEntradas(tx, db, (cobro.get("entradas") as string[] | undefined) ?? []);
    tx.update(cobroRef, { estado: nuevo, idTransaccionPasarela: pagoMP.id, actualizadoEn: FieldValue.serverTimestamp() });
    aplicarResultadoAEntradas(tx, entradas, cobroId, nuevo, "PAGO_DIRECTO");
    return nuevo;
  });
}
