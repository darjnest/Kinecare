import { Timestamp, type Firestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { SUBCOLECCION_DISPOSITIVOS, ZONA_HORARIA } from "./constantes.js";

/** Valor de `data.tipo` del push; la app lo usa para decidir a que pantalla navegar. */
export type TipoNotificacion =
  | "NUEVA_SOLICITUD"
  | "RESERVA_CONFIRMADA"
  | "RESERVA_RECHAZADA"
  | "SOLICITUD_EXPIRADA"
  | "RESERVA_CANCELADA"
  | "DEJAR_RESENA";

/** Mensaje ya armado y su destinatario (uid del usuario, no token). */
export interface NotificacionReserva {
  destinatarioUid: string;
  titulo: string;
  cuerpo: string;
  /** Solo strings (FCM): `tipo` y `reservaId`. */
  data: { tipo: TipoNotificacion; reservaId: string };
}

/** Lo que se envia a FCM por un conjunto de tokens. */
export interface MensajePush {
  titulo: string;
  cuerpo: string;
  data: Record<string, string>;
}

/** Frontera con FCM: los tests la reemplazan por un doble; produccion usa `mensajeria.ts`. */
export interface Mensajeria {
  /** Envia a todos los tokens y devuelve los que FCM declaro invalidos/no registrados. */
  enviar(tokens: string[], mensaje: MensajePush): Promise<{ tokensInvalidos: string[] }>;
}

const MESES = [
  "enero", "febrero", "marzo", "abril", "mayo", "junio",
  "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
];

/** "viernes 16 de octubre a las 15:00", en hora de Chile (como se muestran las citas). */
export function fechaLegible(fecha: Date): string {
  const partes = new Intl.DateTimeFormat("es-CL", {
    timeZone: ZONA_HORARIA,
    weekday: "long",
    day: "numeric",
    month: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(fecha);
  const get = (tipo: string) => partes.find((p) => p.type === tipo)?.value ?? "";
  const mes = MESES[Number(get("month")) - 1] ?? "";
  return `${get("weekday")} ${Number(get("day"))} de ${mes} a las ${get("hour")}:${get("minute")}`;
}

function texto(valor: unknown): string | null {
  return typeof valor === "string" && valor !== "" ? valor : null;
}

/**
 * Decide si un cambio en `reservas/{id}` merece un push y a quien. Pura (sin Firestore ni FCM).
 *
 * Solo notifica cuando el `estado` cambia (o la reserva nace `SOLICITADA`): editar otros campos,
 * como `pago`, no genera avisos, ni tampoco borrar el documento. `EN_CURSO` no avisa.
 * Destinatario siempre la contraparte de quien provoco el cambio:
 * - nace `SOLICITADA` -> profesional;
 * - `CONFIRMADA`, `RECHAZADA` (o expirada: `expirada == true`), `CANCELADA_PROFESIONAL` -> cliente;
 * - `CANCELADA_CLIENTE` -> profesional;
 * - `COMPLETADA` -> cliente (pedir reseña).
 */
export function armarNotificacion(
  reservaId: string,
  antes: Record<string, unknown> | undefined,
  despues: Record<string, unknown> | undefined,
): NotificacionReserva | null {
  if (despues === undefined) return null;
  const estado = despues.estado;
  if (antes !== undefined && antes.estado === estado) return null;

  const clienteId = texto(despues.clienteId);
  const profesionalId = texto(despues.profesionalId);
  if (clienteId === null || profesionalId === null) return null;

  const fechaHora = despues.fechaHora;
  const cuando = fechaHora instanceof Timestamp ? fechaLegible(fechaHora.toDate()) : null;
  const deLaCita = cuando === null ? "tu atención" : `la atención del ${cuando}`;

  const armar = (
    destinatarioUid: string,
    tipo: TipoNotificacion,
    titulo: string,
    cuerpo: string,
  ): NotificacionReserva => ({ destinatarioUid, titulo, cuerpo, data: { tipo, reservaId } });

  switch (estado) {
    case "SOLICITADA":
      // Solo cuando la reserva nace; una vuelta atras a SOLICITADA no existe en el dominio.
      if (antes !== undefined) return null;
      return armar(
        profesionalId,
        "NUEVA_SOLICITUD",
        "Nueva solicitud",
        cuando === null
          ? "Tienes una nueva solicitud de atención. Respóndela cuanto antes."
          : `Tienes una nueva solicitud para el ${cuando}. Respóndela cuanto antes.`,
      );
    case "CONFIRMADA":
      return armar(
        clienteId,
        "RESERVA_CONFIRMADA",
        "Reserva confirmada",
        `El profesional confirmó ${deLaCita}. Ya puedes pagarla desde Mis Citas.`,
      );
    case "RECHAZADA":
      if (despues.expirada === true) {
        return armar(
          clienteId,
          "SOLICITUD_EXPIRADA",
          "Solicitud sin respuesta",
          `Tu solicitud para ${cuando === null ? "esa hora" : `el ${cuando}`} no fue respondida a tiempo. Puedes reservar otro horario.`,
        );
      }
      return armar(
        clienteId,
        "RESERVA_RECHAZADA",
        "Solicitud rechazada",
        `El profesional no pudo tomar ${deLaCita}. Puedes buscar otro horario u otro profesional.`,
      );
    case "CANCELADA_PROFESIONAL":
      return armar(
        clienteId,
        "RESERVA_CANCELADA",
        "Reserva cancelada",
        `El profesional canceló ${deLaCita}. Revisa el detalle en Mis Citas.`,
      );
    case "CANCELADA_CLIENTE":
      return armar(
        profesionalId,
        "RESERVA_CANCELADA",
        "Reserva cancelada",
        `El cliente canceló ${deLaCita}.`,
      );
    case "COMPLETADA":
      return armar(
        clienteId,
        "DEJAR_RESENA",
        "¿Cómo fue tu atención?",
        "Deja tu reseña y ayuda a otras personas a elegir.",
      );
    default:
      return null;
  }
}

export interface ResultadoNotificacion {
  /** Tokens a los que se intento enviar. */
  destinos: number;
  /** Tokens invalidos que se borraron de `dispositivos`. */
  tokensBorrados: number;
}

/**
 * Trigger de `reservas/{id}`: arma la notificacion, busca los dispositivos del destinatario,
 * envia y borra los tokens que FCM rechazo. Nunca lanza por una falla de FCM: el trigger no
 * reintenta (un reintento duplicaria avisos) y perder un push no debe romper nada mas.
 */
export async function notificarCambioReserva(
  db: Firestore,
  mensajeria: Mensajeria,
  reservaId: string,
  antes: Record<string, unknown> | undefined,
  despues: Record<string, unknown> | undefined,
): Promise<ResultadoNotificacion> {
  const vacio = { destinos: 0, tokensBorrados: 0 };
  const notificacion = armarNotificacion(reservaId, antes, despues);
  if (notificacion === null) return vacio;

  const dispositivos = db
    .collection("usuarios")
    .doc(notificacion.destinatarioUid)
    .collection(SUBCOLECCION_DISPOSITIVOS);
  const tokens = (await dispositivos.get()).docs.map((d) => d.id);
  if (tokens.length === 0) return vacio;

  let invalidos: string[];
  try {
    ({ tokensInvalidos: invalidos } = await mensajeria.enviar(tokens, {
      titulo: notificacion.titulo,
      cuerpo: notificacion.cuerpo,
      data: notificacion.data,
    }));
  } catch (e) {
    logger.error("No se pudo enviar el push de la reserva", { reservaId, tipo: notificacion.data.tipo, error: String(e) });
    return { destinos: tokens.length, tokensBorrados: 0 };
  }

  // Solo se borran tokens que de verdad estaban registrados para este usuario.
  const aBorrar = invalidos.filter((t) => tokens.includes(t));
  await Promise.all(aBorrar.map((t) => dispositivos.doc(t).delete()));
  return { destinos: tokens.length, tokensBorrados: aBorrar.length };
}
