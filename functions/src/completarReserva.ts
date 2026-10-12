import { FieldValue, Timestamp, type Firestore } from "firebase-admin/firestore";
import { errorDeNegocio } from "./errores.js";
import { idObligatorio, esObjeto, invalido } from "./validacion.js";

export interface SolicitudCompletar {
  reservaId: string;
}

export interface ResultadoCompletarReserva {
  estado: "COMPLETADA";
}

/** Valida la forma del payload. No consulta Firestore. */
export function parseCompletar(data: unknown): SolicitudCompletar {
  if (!esObjeto(data)) throw invalido("el cuerpo debe ser un objeto");
  return { reservaId: idObligatorio(data.reservaId, "reservaId") };
}

/** Estados desde los que una atencion se puede dar por realizada. */
const ESTADOS_COMPLETABLES = ["CONFIRMADA", "EN_CURSO"];

/**
 * Logica de `completarReserva`: el profesional dueño marca como `COMPLETADA` una atencion
 * `CONFIRMADA` (o `EN_CURSO`) cuya hora ya llego. Separada del wrapper onCall para probarla contra
 * el emulador.
 *
 * Es el paso que habilita dejar una reseña, el "por liquidar" del dashboard y las liquidaciones.
 * Solo el profesional puede completarla (el cliente no: podria dar por hecha una atencion que no
 * ocurrio). Una reserva de otro profesional responde igual que una inexistente
 * (`RESERVA_NO_ENCONTRADA`). Completar una cita futura se rechaza (`CITA_NO_INICIADA`). Lee y
 * escribe en una transaccion: si una cancelacion o una respuesta simultanea cambia el estado, esta
 * ve el estado nuevo y falla con `RESERVA_NO_COMPLETABLE` en vez de pisarlo. Es idempotente solo
 * en el sentido de que repetirla falla limpio; no toca pagos ni `bloqueosAgenda`.
 */
export async function completarReservaHandler(
  db: Firestore,
  uid: string | undefined,
  data: unknown,
  ahora: Date,
): Promise<ResultadoCompletarReserva> {
  if (uid === undefined || uid === "") {
    throw errorDeNegocio("unauthenticated", "SIN_SESION", "Debes iniciar sesión para completar una atención.");
  }

  const { reservaId } = parseCompletar(data);

  // El rol sale del documento del usuario, nunca del payload ni de claims del cliente.
  const usuario = await db.collection("usuarios").doc(uid).get();
  if (!usuario.exists || usuario.get("rol") !== "PROFESIONAL") {
    throw errorDeNegocio("permission-denied", "ROL_INVALIDO", "Solo un profesional puede completar una atención.");
  }

  const reservaRef = db.collection("reservas").doc(reservaId);

  await db.runTransaction(async (tx) => {
    const reserva = await tx.get(reservaRef);
    if (!reserva.exists || reserva.get("profesionalId") !== uid) {
      throw errorDeNegocio("not-found", "RESERVA_NO_ENCONTRADA", "La atención ya no existe.");
    }
    if (!ESTADOS_COMPLETABLES.includes(reserva.get("estado") as string)) {
      throw errorDeNegocio(
        "failed-precondition",
        "RESERVA_NO_COMPLETABLE",
        "Solo se puede completar una atención confirmada.",
      );
    }
    const fechaHora = reserva.get("fechaHora");
    if (!(fechaHora instanceof Timestamp) || fechaHora.toMillis() > ahora.getTime()) {
      throw errorDeNegocio("failed-precondition", "CITA_NO_INICIADA", "La atención todavía no empieza.");
    }
    tx.update(reservaRef, {
      estado: "COMPLETADA",
      completadaEn: FieldValue.serverTimestamp(),
      actualizadoEn: FieldValue.serverTimestamp(),
    });
  });

  return { estado: "COMPLETADA" };
}
