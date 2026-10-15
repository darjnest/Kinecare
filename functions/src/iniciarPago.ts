import { FieldValue, Timestamp, type Firestore } from "firebase-admin/firestore";
import {
  aEntradas,
  consultaPendientes,
  estaLibre,
  margenDeDescuento,
  reservarEntradas,
  seleccionarParaDescuento,
} from "./comisiones.js";
import { COLECCION_PAGOS, COMISION_PORCENTAJE } from "./constantes.js";
import { errorDeNegocio } from "./errores.js";
import { tokenVigente } from "./mercadopago/cuentas.js";
import { calcularComision, type EstadoPago } from "./mercadopago/estados.js";
import type { Pasarela } from "./mercadopago/pasarela.js";
import { esObjeto, idObligatorio, invalido } from "./validacion.js";

export interface DepsPago {
  pasarela: Pasarela;
  clave: Buffer;
  /** Origen publico HTTPS de las funciones, ej. `https://us-central1-<proyecto>.cloudfunctions.net`. */
  urlBase: string;
  /**
   * Access Token de la cuenta de Mercado Pago de la plataforma. Solo lo usan los cobros de comision
   * adeudada (`pagarSaldoComision` y su webhook); los pagos de reservas usan el token del profesional.
   */
  tokenPlataforma?: string;
}

export interface ResultadoIniciarPago {
  pagoId: string;
  initPoint: string;
}

/** Valida la forma del payload. El cliente solo dice QUE reserva paga; nunca monto, comision ni vendedor. */
export function parseIniciarPago(data: unknown): { reservaId: string } {
  if (!esObjeto(data)) throw invalido("el cuerpo debe ser un objeto");
  return { reservaId: idObligatorio(data.reservaId, "reservaId") };
}

function reservaNoEncontrada() {
  return errorDeNegocio("not-found", "RESERVA_NO_ENCONTRADA", "La reserva ya no existe.");
}

interface PagoPreparado {
  pagoId: string;
  profesionalId: string;
  servicioId: string;
  monto: number;
  comisionPorcentaje: number;
  /** Comision adeudada (efectivo) que se suma al `marketplace_fee` de este pago. */
  comisionSaldo: number;
  initPointExistente: string | null;
}

/**
 * Logica de `iniciarPago`: el cliente paga una reserva `CONFIRMADA` con Checkout Pro.
 *
 * Todo lo sensible sale del servidor: el monto es el `pago.monto` que `crearReserva` copio del
 * servicio, la comision la fija la reserva y el vendedor es su profesional (cuyo token OAuth se
 * usa; sin cuenta conectada se falla, jamas se cobra a la cuenta de la plataforma). Un reintento
 * mientras el pago sigue `PENDIENTE` reutiliza el mismo documento y la misma preferencia
 * (`idempotencyKey = pagoId`), asi un doble toque no genera dos cobros.
 */
export async function iniciarPagoHandler(
  db: Firestore,
  deps: DepsPago,
  uid: string | undefined,
  data: unknown,
  ahora: Date,
): Promise<ResultadoIniciarPago> {
  if (uid === undefined || uid === "") {
    throw errorDeNegocio("unauthenticated", "SIN_SESION", "Debes iniciar sesión para pagar.");
  }
  const { reservaId } = parseIniciarPago(data);

  const usuario = await db.collection("usuarios").doc(uid).get();
  if (!usuario.exists || usuario.get("rol") !== "CLIENTE") {
    throw errorDeNegocio("permission-denied", "ROL_INVALIDO", "Solo el cliente de la reserva puede pagarla.");
  }

  const reservaRef = db.collection("reservas").doc(reservaId);
  const pagos = db.collection(COLECCION_PAGOS);

  const preparado = await db.runTransaction<PagoPreparado>(async (tx) => {
    const reserva = await tx.get(reservaRef);
    // Una reserva ajena responde igual que una inexistente: no se confirma su existencia.
    if (!reserva.exists || reserva.get("clienteId") !== uid) throw reservaNoEncontrada();
    if (reserva.get("estado") !== "CONFIRMADA") {
      throw errorDeNegocio(
        "failed-precondition",
        "RESERVA_NO_PAGABLE",
        "Solo puedes pagar una reserva que el profesional ya confirmó.",
      );
    }

    const fechaHora = reserva.get("fechaHora");
    if (!(fechaHora instanceof Timestamp) || fechaHora.toMillis() <= ahora.getTime()) {
      // Una cita confirmada que nadie atendio no se cobra: no hay flujo de reembolso todavia.
      throw errorDeNegocio("failed-precondition", "RESERVA_NO_PAGABLE", "La hora de esta cita ya pasó; no se puede pagar.");
    }

    const pagoRef = reserva.get("pago") as { id: string | null; monto: number; estado: EstadoPago } | undefined;
    if (pagoRef === undefined) throw errorDeNegocio("failed-precondition", "MONTO_INVALIDO", "La reserva no tiene monto a cobrar.");
    if (pagoRef.estado === "AUTORIZADO" || pagoRef.estado === "REEMBOLSADO") {
      throw errorDeNegocio("failed-precondition", "PAGO_YA_REALIZADO", "Esta reserva ya está pagada.");
    }

    const comisionPorcentaje =
      typeof reserva.get("comisionPorcentaje") === "number" ? (reserva.get("comisionPorcentaje") as number) : COMISION_PORCENTAJE;
    calcularComision(pagoRef.monto, comisionPorcentaje); // valida monto y comision antes de escribir nada

    const base = {
      profesionalId: reserva.get("profesionalId") as string,
      servicioId: reserva.get("servicioId") as string,
      monto: pagoRef.monto,
      comisionPorcentaje,
    };

    // Reintento: si el pago anterior sigue PENDIENTE se reutiliza (y su initPoint, si ya existe).
    if (pagoRef.id !== null) {
      const previo = await tx.get(pagos.doc(pagoRef.id));
      if (previo.exists && previo.get("estado") === "PENDIENTE") {
        return {
          ...base,
          pagoId: previo.id,
          comisionSaldo: (previo.get("comisionSaldoDescontada") as number | undefined) ?? 0,
          initPointExistente: (previo.get("initPoint") as string | undefined) ?? null,
        };
      }
    }

    // Comision adeudada por atenciones en efectivo: se descuenta de este pago hasta donde alcance el
    // tope del fee y se reserva para que otro pago simultaneo no cobre las mismas entradas.
    const comisionBase = calcularComision(base.monto, comisionPorcentaje);
    const pendientes = aEntradas((await tx.get(consultaPendientes(db, base.profesionalId))).docs);
    const elegidas = seleccionarParaDescuento(
      pendientes.filter((e) => estaLibre(e, ahora)),
      margenDeDescuento(base.monto, comisionBase),
    );
    const comisionSaldo = elegidas.reduce((acc, e) => acc + e.monto, 0);

    const nuevo = pagos.doc();
    tx.set(nuevo, {
      reservaId,
      clienteId: uid,
      profesionalId: base.profesionalId,
      monto: base.monto,
      comision: comisionBase,
      comisionSaldoDescontada: comisionSaldo,
      entradasSaldo: elegidas.map((e) => e.id),
      metodo: null,
      estado: "PENDIENTE" satisfies EstadoPago,
      idTransaccionPasarela: null,
      preferenceId: null,
      initPoint: null,
      creadoEn: FieldValue.serverTimestamp(),
      actualizadoEn: FieldValue.serverTimestamp(),
    });
    tx.update(reservaRef, {
      "pago.id": nuevo.id,
      "pago.estado": "PENDIENTE",
      actualizadoEn: FieldValue.serverTimestamp(),
    });
    reservarEntradas(tx, elegidas, nuevo.id);
    return { ...base, pagoId: nuevo.id, comisionSaldo, initPointExistente: null };
  });

  if (preparado.initPointExistente !== null) {
    return { pagoId: preparado.pagoId, initPoint: preparado.initPointExistente };
  }

  const token = await tokenVigente(db, deps.pasarela, deps.clave, preparado.profesionalId, ahora);
  const servicio = await db
    .collection("profesionales")
    .doc(preparado.profesionalId)
    .collection("servicios")
    .doc(preparado.servicioId)
    .get();
  const nombre = servicio.get("nombre");

  const { preferenceId, initPoint } = await deps.pasarela.crearPreferencia(token, {
    titulo: typeof nombre === "string" && nombre !== "" ? nombre : "Atención KineCare",
    itemId: reservaId,
    monto: preparado.monto,
    comision: calcularComision(preparado.monto, preparado.comisionPorcentaje) + preparado.comisionSaldo,
    referenciaExterna: preparado.pagoId,
    notificationUrl: `${deps.urlBase}/webhookMercadoPago?pagoId=${preparado.pagoId}`,
    backUrl: `${deps.urlBase}/retornoPago`,
    claveIdempotencia: preparado.pagoId,
  });

  await pagos.doc(preparado.pagoId).update({ preferenceId, initPoint, actualizadoEn: FieldValue.serverTimestamp() });
  return { pagoId: preparado.pagoId, initPoint };
}
