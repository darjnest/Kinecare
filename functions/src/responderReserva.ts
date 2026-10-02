import { FieldValue, Timestamp, type Firestore } from "firebase-admin/firestore";
import { errorDeNegocio } from "./errores.js";
import { idObligatorio, invalido, esObjeto } from "./validacion.js";

const RESPUESTAS = ["ACEPTAR", "RECHAZAR"] as const;
type Respuesta = (typeof RESPUESTAS)[number];

/** Estado en que queda la reserva segun la respuesta del profesional. */
const ESTADO_SEGUN_RESPUESTA = {
  ACEPTAR: "CONFIRMADA",
  RECHAZAR: "RECHAZADA",
} as const satisfies Record<Respuesta, string>;

export interface SolicitudRespuesta {
  reservaId: string;
  respuesta: Respuesta;
}

export interface ResultadoResponderReserva {
  estado: (typeof ESTADO_SEGUN_RESPUESTA)[Respuesta];
}

/** Valida la forma del payload. No consulta Firestore. */
export function parseRespuesta(data: unknown): SolicitudRespuesta {
  if (!esObjeto(data)) throw invalido("el cuerpo debe ser un objeto");
  const { respuesta } = data;
  if (!RESPUESTAS.includes(respuesta as Respuesta)) {
    throw invalido(`respuesta debe ser ${RESPUESTAS.join(" o ")}`);
  }
  return { reservaId: idObligatorio(data.reservaId, "reservaId"), respuesta: respuesta as Respuesta };
}

function reservaNoEncontrada() {
  return errorDeNegocio("not-found", "RESERVA_NO_ENCONTRADA", "La solicitud ya no existe.");
}

/**
 * Logica de `responderReserva`: el profesional acepta (`CONFIRMADA`) o rechaza (`RECHAZADA`)
 * una reserva en `SOLICITADA`. Separada del wrapper onCall para probarla contra el emulador.
 *
 * Una reserva de otro profesional responde igual que una inexistente (`RESERVA_NO_ENCONTRADA`):
 * no se confirma la existencia de reservas ajenas. Aceptar exige que la cita no haya empezado
 * (`RESERVA_VENCIDA`); rechazar una vencida si se permite, para que el profesional pueda
 * limpiarla. Leer y escribir en una transaccion evita que dos respuestas simultaneas (o una
 * respuesta y una futura cancelacion) se pisen: la segunda ve el estado ya cambiado y falla con
 * `RESERVA_YA_RESPONDIDA`. No toca `bloqueosAgenda`: `SOLICITADA` y `CONFIRMADA` ocupan la
 * agenda por igual, y `RECHAZADA` solo la libera (crearReserva lee la reserva dentro de su
 * propia transaccion, asi que ve el cambio o se reintenta).
 */
export async function responderReservaHandler(
  db: Firestore,
  uid: string | undefined,
  data: unknown,
  ahora: Date,
): Promise<ResultadoResponderReserva> {
  if (uid === undefined || uid === "") {
    throw errorDeNegocio("unauthenticated", "SIN_SESION", "Debes iniciar sesión para responder solicitudes.");
  }

  const { reservaId, respuesta } = parseRespuesta(data);

  // El rol sale del documento del usuario, nunca del payload ni de claims del cliente.
  const usuario = await db.collection("usuarios").doc(uid).get();
  if (!usuario.exists || usuario.get("rol") !== "PROFESIONAL") {
    throw errorDeNegocio("permission-denied", "ROL_INVALIDO", "Solo un profesional puede responder solicitudes.");
  }

  const reservaRef = db.collection("reservas").doc(reservaId);
  const estado = ESTADO_SEGUN_RESPUESTA[respuesta];

  await db.runTransaction(async (tx) => {
    const reserva = await tx.get(reservaRef);
    if (!reserva.exists || reserva.get("profesionalId") !== uid) {
      throw reservaNoEncontrada();
    }
    if (reserva.get("estado") !== "SOLICITADA") {
      throw errorDeNegocio("failed-precondition", "RESERVA_YA_RESPONDIDA", "Esta solicitud ya fue respondida.");
    }
    const fechaHora = reserva.get("fechaHora");
    if (respuesta === "ACEPTAR" && (!(fechaHora instanceof Timestamp) || fechaHora.toMillis() <= ahora.getTime())) {
      throw errorDeNegocio(
        "failed-precondition",
        "RESERVA_VENCIDA",
        "La hora de esta solicitud ya pasó; no se puede aceptar.",
      );
    }
    tx.update(reservaRef, {
      estado,
      respondidaEn: FieldValue.serverTimestamp(),
      actualizadoEn: FieldValue.serverTimestamp(),
    });
  });

  return { estado };
}
