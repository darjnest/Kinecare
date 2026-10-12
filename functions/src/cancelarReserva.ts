import { FieldValue, Timestamp, type Firestore } from "firebase-admin/firestore";
import { COLECCION_PAGOS, HORAS_REEMBOLSO_TOTAL, MAX_LARGO_MOTIVO_CANCELACION } from "./constantes.js";
import { errorDeNegocio } from "./errores.js";
import { esObjeto, idObligatorio, invalido } from "./validacion.js";

export interface SolicitudCancelar {
  reservaId: string;
  /** null si el usuario no dio motivo. Ya normalizado (trim). */
  motivo: string | null;
}

/**
 * Que pasa con el dinero segun la politica:
 * - `NO_APLICA`: la reserva no tenia un pago `AUTORIZADO`, no hay nada que devolver.
 * - `TOTAL`: corresponde reembolso total (queda `pagos/{id}.reembolsoPendiente = true`).
 * - `NINGUNO`: pago autorizado pero la politica no da reembolso (cliente con < 24 h).
 */
export type ReembolsoCancelacion = "NO_APLICA" | "TOTAL" | "NINGUNO";

export interface ResultadoCancelarReserva {
  estado: "CANCELADA_CLIENTE" | "CANCELADA_PROFESIONAL";
  reembolso: ReembolsoCancelacion;
}

const MS_HORA = 3_600_000;

/** Estados desde los que se puede cancelar (la cita aun no empieza). */
const ESTADOS_CANCELABLES = ["SOLICITADA", "CONFIRMADA"];

export function motivoInvalido() {
  return errorDeNegocio(
    "invalid-argument",
    "MOTIVO_INVALIDO",
    `El motivo debe ser texto de hasta ${MAX_LARGO_MOTIVO_CANCELACION} caracteres.`,
  );
}

/** Valida la forma del payload. No consulta Firestore. */
export function parseCancelar(data: unknown): SolicitudCancelar {
  if (!esObjeto(data)) throw invalido("el cuerpo debe ser un objeto");
  const reservaId = idObligatorio(data.reservaId, "reservaId");
  const { motivo } = data;
  if (motivo === undefined || motivo === null) return { reservaId, motivo: null };
  if (typeof motivo !== "string") throw motivoInvalido();
  const texto = motivo.trim();
  if (texto.length > MAX_LARGO_MOTIVO_CANCELACION) throw motivoInvalido();
  return { reservaId, motivo: texto === "" ? null : texto };
}

/**
 * Politica de reembolso de una cancelacion con pago autorizado:
 * - profesional cancela: total, siempre;
 * - cliente con >= 24 h de anticipacion: total;
 * - cliente con menos: ninguno automatico (solo se registra).
 */
export function reembolsoSegunPolitica(
  quien: "CLIENTE" | "PROFESIONAL",
  fechaHora: Date,
  ahora: Date,
): Exclude<ReembolsoCancelacion, "NO_APLICA"> {
  if (quien === "PROFESIONAL") return "TOTAL";
  return fechaHora.getTime() - ahora.getTime() >= HORAS_REEMBOLSO_TOTAL * MS_HORA ? "TOTAL" : "NINGUNO";
}

/**
 * Logica de `cancelarReserva`: el cliente o el profesional dueños cancelan una reserva
 * `SOLICITADA`/`CONFIRMADA` cuya cita aun no empieza. Separada del wrapper onCall para probarla
 * contra el emulador.
 *
 * Quien cancela se decide comparando el uid de la sesion con `clienteId`/`profesionalId` de la
 * reserva (nunca por un dato del payload): cliente -> `CANCELADA_CLIENTE`, profesional ->
 * `CANCELADA_PROFESIONAL`. Lee y escribe en una transaccion, asi que una cancelacion simultanea
 * con una respuesta o con otra cancelacion se serializa: la segunda ve el estado nuevo y falla
 * con `ESTADO_INVALIDO`.
 *
 * Agenda: no hace falta tocar `bloqueosAgenda`. Ese documento solo serializa a `crearReserva`;
 * la disponibilidad real sale del `estado` de las reservas, y `CANCELADA_*` no ocupa agenda.
 * `crearReserva` lee las reservas del rango dentro de su propia transaccion, asi que ve la
 * cancelacion o se reintenta.
 *
 * Reembolso: no hay cliente de reembolso de Mercado Pago. Si el pago esta `AUTORIZADO` se marca
 * `pagos/{id}.reembolsoPendiente` (ver `reembolsoSegunPolitica`) y el reembolso real es manual.
 */
export async function cancelarReservaHandler(
  db: Firestore,
  uid: string | undefined,
  data: unknown,
  ahora: Date,
): Promise<ResultadoCancelarReserva> {
  if (uid === undefined || uid === "") {
    throw errorDeNegocio("unauthenticated", "SIN_SESION", "Debes iniciar sesión para cancelar una reserva.");
  }

  const { reservaId, motivo } = parseCancelar(data);
  const reservaRef = db.collection("reservas").doc(reservaId);

  return db.runTransaction(async (tx) => {
    const reserva = await tx.get(reservaRef);
    if (!reserva.exists) {
      throw errorDeNegocio("not-found", "RESERVA_NO_ENCONTRADA", "La reserva ya no existe.");
    }
    const quien =
      reserva.get("clienteId") === uid ? "CLIENTE" : reserva.get("profesionalId") === uid ? "PROFESIONAL" : null;
    if (quien === null) {
      throw errorDeNegocio("permission-denied", "NO_AUTORIZADO", "No puedes cancelar esta reserva.");
    }
    if (!ESTADOS_CANCELABLES.includes(reserva.get("estado") as string)) {
      throw errorDeNegocio(
        "failed-precondition",
        "ESTADO_INVALIDO",
        "Solo se puede cancelar una reserva solicitada o confirmada.",
      );
    }
    const fechaHora = reserva.get("fechaHora");
    if (!(fechaHora instanceof Timestamp) || fechaHora.toMillis() <= ahora.getTime()) {
      throw errorDeNegocio("failed-precondition", "CITA_YA_INICIADA", "La cita ya empezó; no se puede cancelar.");
    }

    // Todas las lecturas antes de la primera escritura (regla de las transacciones de Firestore).
    const pagoId = (reserva.get("pago") as { id?: string | null } | undefined)?.id;
    const pagoRef = typeof pagoId === "string" && pagoId !== "" ? db.collection(COLECCION_PAGOS).doc(pagoId) : null;
    const pago = pagoRef === null ? null : await tx.get(pagoRef);

    let reembolso: ReembolsoCancelacion = "NO_APLICA";
    if (pagoRef !== null && pago !== null && pago.exists && pago.get("estado") === "AUTORIZADO") {
      reembolso = reembolsoSegunPolitica(quien, fechaHora.toDate(), ahora);
      tx.update(pagoRef, {
        reembolsoPendiente: reembolso === "TOTAL",
        reembolsoPolitica: reembolso,
        reembolsoSolicitadoEn: FieldValue.serverTimestamp(),
        actualizadoEn: FieldValue.serverTimestamp(),
      });
    }

    const estado = quien === "CLIENTE" ? "CANCELADA_CLIENTE" : "CANCELADA_PROFESIONAL";
    tx.update(reservaRef, {
      estado,
      canceladaEn: FieldValue.serverTimestamp(),
      canceladaPor: uid,
      motivoCancelacion: motivo,
      actualizadoEn: FieldValue.serverTimestamp(),
    });
    return { estado, reembolso } satisfies ResultadoCancelarReserva;
  });
}
