import { FieldValue, Timestamp, type Firestore } from "firebase-admin/firestore";
import {
  ANTICIPACION_MAXIMA_DIAS,
  ANTICIPACION_MINIMA_MINUTOS,
  COLECCION_BLOQUEOS_AGENDA,
  COMISION_PORCENTAJE,
} from "./constantes.js";
import { errorDeNegocio } from "./errores.js";
import { slotDentroDeDisponibilidad } from "./horario.js";
import { fin, hayReservaSolapada, ventanaDeConsulta, type ReservaExistente } from "./solapamiento.js";
import { direccionCompleta, parseSolicitud, type DireccionSolicitada } from "./validacion.js";

const MS_MINUTO = 60_000;
const MS_DIA = 24 * 60 * MS_MINUTO;
const MODALIDADES = ["DOMICILIO", "CONSULTA", "ONLINE"] as const;

export interface ResultadoCrearReserva {
  reservaId: string;
}

interface ServicioValido {
  modalidad: (typeof MODALIDADES)[number];
  duracionMinutos: number;
  precio: number;
}

function servicioNoDisponible(code: "not-found" | "failed-precondition") {
  return errorDeNegocio(code, "SERVICIO_NO_DISPONIBLE", "El servicio ya no está disponible.");
}

/**
 * Lee los campos del servicio que la reserva copia. Un servicio con datos corruptos
 * (modalidad desconocida, duracion o precio no numericos) se trata como no disponible:
 * mejor rechazar que escribir una reserva incoherente.
 */
function leerServicio(datos: Record<string, unknown>): ServicioValido {
  const { modalidad, duracionMinutos, precio } = datos;
  if (
    !MODALIDADES.includes(modalidad as (typeof MODALIDADES)[number]) ||
    typeof duracionMinutos !== "number" ||
    !Number.isFinite(duracionMinutos) ||
    duracionMinutos <= 0 ||
    typeof precio !== "number" ||
    !Number.isFinite(precio) ||
    precio < 0
  ) {
    throw servicioNoDisponible("failed-precondition");
  }
  return { modalidad: modalidad as ServicioValido["modalidad"], duracionMinutos, precio };
}

function direccionParaGuardar(d: DireccionSolicitada) {
  return {
    calle: d.calle,
    numero: d.numero,
    comuna: d.comuna,
    ciudad: d.ciudad,
    lat: d.lat,
    lng: d.lng,
    indicaciones: d.indicaciones,
  };
}

/**
 * Logica de `crearReserva`, separada del wrapper onCall para poder probarla contra el emulador.
 *
 * Concurrencia (HORARIO_OCUPADO): todo el check-then-write va en una transaccion que ademas
 * lee y reescribe el documento-candado `bloqueosAgenda/{profesionalId}`. La consulta de
 * reservas solapadas es un rango; Firestore bloquea los documentos que la consulta devuelve,
 * pero no esta documentado que bloquee los huecos del rango (un insert fantasma). El candado
 * convierte cualquier par de reservas del mismo profesional en transacciones que escriben el
 * mismo documento, y Firestore las serializa: la segunda ve la reserva de la primera. Se
 * lee el candado ANTES de la consulta para que esta ya observe lo confirmado por la anterior.
 * Costo: las reservas de un mismo profesional se serializan, aceptable a esta escala.
 */
export async function crearReservaHandler(
  db: Firestore,
  uid: string | undefined,
  data: unknown,
  ahora: Date,
): Promise<ResultadoCrearReserva> {
  if (uid === undefined || uid === "") {
    throw errorDeNegocio("unauthenticated", "SIN_SESION", "Debes iniciar sesión para reservar.");
  }

  const solicitud = parseSolicitud(data);

  // El rol sale del documento del usuario, nunca del payload ni de claims del cliente.
  const usuario = await db.collection("usuarios").doc(uid).get();
  if (!usuario.exists || usuario.get("rol") !== "CLIENTE" || uid === solicitud.profesionalId) {
    throw errorDeNegocio("permission-denied", "ROL_INVALIDO", "Solo un cliente puede reservar con otro profesional.");
  }

  const profesionalRef = db.collection("profesionales").doc(solicitud.profesionalId);
  const servicioRef = profesionalRef.collection("servicios").doc(solicitud.servicioId);
  const bloqueoRef = db.collection(COLECCION_BLOQUEOS_AGENDA).doc(solicitud.profesionalId);
  const reservaRef = db.collection("reservas").doc();

  await db.runTransaction(async (tx) => {
    const [profesional, servicioDoc] = await Promise.all([tx.get(profesionalRef), tx.get(servicioRef)]);
    if (!profesional.exists || !servicioDoc.exists) {
      throw servicioNoDisponible("not-found");
    }
    if (servicioDoc.get("activo") === false) {
      throw servicioNoDisponible("failed-precondition");
    }
    const servicio = leerServicio(servicioDoc.data() ?? {});

    let direccion: DireccionSolicitada | null = null;
    if (servicio.modalidad === "DOMICILIO") {
      if (!direccionCompleta(solicitud.direccion)) {
        throw errorDeNegocio(
          "invalid-argument",
          "DIRECCION_REQUERIDA",
          "Para una atención a domicilio debes indicar calle, número y comuna.",
        );
      }
      direccion = solicitud.direccion;
    }

    const inicio = solicitud.fechaHora;
    const masTemprano = ahora.getTime() + ANTICIPACION_MINIMA_MINUTOS * MS_MINUTO;
    const masTarde = ahora.getTime() + ANTICIPACION_MAXIMA_DIAS * MS_DIA;
    if (inicio.getTime() < masTemprano || inicio.getTime() > masTarde) {
      throw errorDeNegocio(
        "failed-precondition",
        "ANTICIPACION_INSUFICIENTE",
        `Debes reservar con al menos ${ANTICIPACION_MINIMA_MINUTOS} minutos y hasta ${ANTICIPACION_MAXIMA_DIAS} días de anticipación.`,
      );
    }

    if (!slotDentroDeDisponibilidad(profesional.get("disponibilidad"), inicio, servicio.duracionMinutos)) {
      throw errorDeNegocio(
        "failed-precondition",
        "FUERA_DE_HORARIO",
        "El profesional no atiende en el horario elegido.",
      );
    }

    // Candado primero (ver comentario de la funcion), luego la consulta de solapes.
    await tx.get(bloqueoRef);
    const finSlot = fin(inicio, servicio.duracionMinutos);
    const { desde, hasta } = ventanaDeConsulta(inicio, finSlot);
    const candidatas = await tx.get(
      db
        .collection("reservas")
        .where("profesionalId", "==", solicitud.profesionalId)
        .where("fechaHora", ">=", Timestamp.fromDate(desde))
        .where("fechaHora", "<", Timestamp.fromDate(hasta)),
    );
    const existentes: ReservaExistente[] = candidatas.docs.map((doc) => ({
      estado: doc.get("estado"),
      inicio: (doc.get("fechaHora") as Timestamp).toDate(),
      duracionMinutos: doc.get("duracionMinutos"),
    }));
    if (hayReservaSolapada(existentes, inicio, finSlot)) {
      throw errorDeNegocio("already-exists", "HORARIO_OCUPADO", "Ese horario ya fue reservado. Elige otro.");
    }

    tx.set(reservaRef, {
      clienteId: uid,
      profesionalId: solicitud.profesionalId,
      servicioId: solicitud.servicioId,
      modalidad: servicio.modalidad,
      fechaHora: Timestamp.fromDate(inicio),
      duracionMinutos: servicio.duracionMinutos,
      direccion: direccion === null ? null : direccionParaGuardar(direccion),
      estado: "SOLICITADA",
      // `id` queda null hasta que iniciarPago (Fase 5) cree el documento en `pagos`.
      pago: { id: null, monto: servicio.precio, estado: "PENDIENTE" },
      comisionPorcentaje: COMISION_PORCENTAJE,
      creadoEn: FieldValue.serverTimestamp(),
      actualizadoEn: FieldValue.serverTimestamp(),
    });
    tx.set(bloqueoRef, {
      profesionalId: solicitud.profesionalId,
      ultimaReservaId: reservaRef.id,
      actualizadoEn: FieldValue.serverTimestamp(),
    });
  }, { maxAttempts: 10 });

  return { reservaId: reservaRef.id };
}
