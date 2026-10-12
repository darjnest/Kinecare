import { Timestamp, type Firestore } from "firebase-admin/firestore";
import { VENTANA_MAXIMA_CUPOS_DIAS } from "./constantes.js";
import { errorDeNegocio } from "./errores.js";
import { duracionEfectiva, fin, hayReservaSolapada, ventanaDeConsulta, type ReservaExistente } from "./solapamiento.js";
import { esObjeto, idObligatorio, invalido, parseFechaHora } from "./validacion.js";

const MS_DIA = 24 * 60 * 60_000;

export interface SolicitudCupos {
  profesionalId: string;
  desde: Date;
  hasta: Date;
}

/** Tramo ocupado, sin ningun dato de la reserva ni de quien la hizo. Instantes ISO-8601 en UTC. */
export interface CupoOcupado {
  inicio: string;
  fin: string;
}

export interface ResultadoCupos {
  cupos: CupoOcupado[];
}

/** Valida la forma del payload. No consulta Firestore. */
export function parseCupos(data: unknown): SolicitudCupos {
  if (!esObjeto(data)) throw invalido("el cuerpo debe ser un objeto");
  const profesionalId = idObligatorio(data.profesionalId, "profesionalId");
  const desde = parseFechaHora(data.desde);
  const hasta = parseFechaHora(data.hasta);
  if (hasta.getTime() <= desde.getTime()) throw invalido("hasta debe ser posterior a desde");
  if (hasta.getTime() - desde.getTime() > VENTANA_MAXIMA_CUPOS_DIAS * MS_DIA) {
    throw invalido(`la ventana no puede superar ${VENTANA_MAXIMA_CUPOS_DIAS} días`);
  }
  return { profesionalId, desde, hasta };
}

/**
 * Logica de `obtenerCuposOcupados`: los tramos `[inicio, fin)` en que el profesional ya tiene una
 * reserva activa (`SOLICITADA`/`CONFIRMADA`/`EN_CURSO`) y que se solapan con `[desde, hasta)`.
 *
 * Existe porque un cliente no puede leer reservas ajenas (Security Rules): la app necesita saber
 * que horarios estan tomados para no ofrecerlos, pero sin ver quien los reservo. Devuelve solo
 * `inicio`/`fin`. Es una ayuda de UI: la verdad la sigue decidiendo `crearReserva`
 * (`HORARIO_OCUPADO`) dentro de su transaccion.
 *
 * Un profesional inexistente devuelve lista vacia (no se revela nada). Solo un CLIENTE (rol leido
 * de `usuarios/{uid}`, nunca del payload) puede consultar.
 */
export async function obtenerCuposOcupadosHandler(
  db: Firestore,
  uid: string | undefined,
  data: unknown,
): Promise<ResultadoCupos> {
  if (uid === undefined || uid === "") {
    throw errorDeNegocio("unauthenticated", "SIN_SESION", "Debes iniciar sesión para ver la disponibilidad.");
  }
  const { profesionalId, desde, hasta } = parseCupos(data);

  const usuario = await db.collection("usuarios").doc(uid).get();
  if (!usuario.exists || usuario.get("rol") !== "CLIENTE") {
    throw errorDeNegocio("permission-denied", "ROL_INVALIDO", "Solo un cliente puede consultar la disponibilidad.");
  }

  // Misma ventana que crearReserva (una reserva dura <= 24 h): usa el indice
  // reservas(profesionalId, fechaHora). El filtro de estado y de solape va en memoria.
  const { desde: desdeConsulta } = ventanaDeConsulta(desde, hasta);
  const snap = await db
    .collection("reservas")
    .where("profesionalId", "==", profesionalId)
    .where("fechaHora", ">=", Timestamp.fromDate(desdeConsulta))
    .where("fechaHora", "<", Timestamp.fromDate(hasta))
    .orderBy("fechaHora", "asc")
    .get();

  const cupos: CupoOcupado[] = [];
  for (const doc of snap.docs) {
    const existente: ReservaExistente = {
      estado: doc.get("estado"),
      inicio: (doc.get("fechaHora") as Timestamp).toDate(),
      duracionMinutos: doc.get("duracionMinutos"),
    };
    // Reutiliza el criterio de crearReserva: ocupa agenda y se solapa con la ventana pedida.
    const finReserva = fin(existente.inicio, duracionEfectiva(existente.duracionMinutos));
    if (hayReservaSolapada([existente], desde, hasta)) {
      cupos.push({ inicio: existente.inicio.toISOString(), fin: finReserva.toISOString() });
    }
  }
  return { cupos };
}
