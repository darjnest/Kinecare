import { FieldValue, Timestamp, type Firestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import {
  COLECCION_PROFESIONALES,
  COLECCION_SOLICITUDES_VERIFICACION,
  COLECCION_USUARIOS,
  MAX_SOLICITUDES_POR_PROFESIONAL,
} from "../constantes.js";
import {
  puedeAplicarse,
  requiereComparacionDeRut,
  resolucionDesdeDidit,
  type EstadoSolicitud,
  type MotivoVerificacion,
} from "./estados.js";
import { camposInsigniaIdentidad, estadoInsigniaIdentidad } from "./insignias.js";
import type { DecisionProveedor } from "./proveedor.js";
import { compararRut } from "./rut.js";

export interface ResultadoAplicacion {
  estado: EstadoSolicitud;
  motivo: MotivoVerificacion | null;
}

export interface EntradaAplicacion {
  /** `session_id` de Didit = id del documento `solicitudesVerificacion/{id}`. */
  solicitudId: string;
  decision: DecisionProveedor;
  /** `event_id` del webhook (estable entre reintentos); ausente cuando la relectura la inicia la app. */
  eventoId?: string;
  ahora: Date;
}

/**
 * Aplica a Firestore el veredicto que el proveedor reporta de una sesion. Es idempotente y tolera
 * eventos repetidos o desordenados: un evento ya visto (`ultimoEventoId`) es no-op y un estado final
 * no retrocede (ver `puedeAplicarse`). Devuelve `null` si no existe una solicitud con ese id (sesion de
 * otro ambiente o ajena a KineCare).
 *
 * Solo escribe el veredicto (estado y codigos gruesos); nada de la decision se persiste salvo
 * `rutCoincide`, un booleano. Politica de la insignia (`profesionales/{uid}.insignias`):
 * - `APROBADO` siempre se aplica;
 * - sobre una insignia ya `APROBADO` solo cambia por `Kyc Expired` de la propia solicitud aprobada
 *   (otra sesion fallida o vieja no puede quitarla);
 * - un resultado que NO es aprobacion ni pendiente no pisa la insignia si el profesional tiene otra
 *   solicitud aun `PENDIENTE` (p. ej. una "In Review" y un segundo intento): sigue "en curso".
 */
export async function aplicarDecision(db: Firestore, entrada: EntradaAplicacion): Promise<ResultadoAplicacion | null> {
  const { solicitudId, decision, eventoId, ahora } = entrada;
  const solicitudes = db.collection(COLECCION_SOLICITUDES_VERIFICACION);
  const solicitudRef = solicitudes.doc(solicitudId);

  return db.runTransaction(async (tx) => {
    const solicitud = await tx.get(solicitudRef);
    if (!solicitud.exists) return null;
    const actual = solicitud.get("estado") as EstadoSolicitud;
    const motivoActual = (solicitud.get("motivo") as MotivoVerificacion | undefined) ?? null;
    const sinCambios: ResultadoAplicacion = { estado: actual, motivo: motivoActual };

    if (eventoId !== undefined && solicitud.get("ultimoEventoId") === eventoId) return sinCambios;

    const profesionalId = solicitud.get("profesionalId") as string;
    const profesionalRef = db.collection(COLECCION_PROFESIONALES).doc(profesionalId);

    // Todas las lecturas antes de cualquier escritura (requisito de las transacciones).
    let rutCoincide: boolean | null = null;
    if (requiereComparacionDeRut(decision.estado)) {
      const usuario = await tx.get(db.collection(COLECCION_USUARIOS).doc(profesionalId));
      rutCoincide = compararRut(decision.rut, usuario.get("rut"));
    }
    const resolucion = resolucionDesdeDidit(decision.estado, rutCoincide);
    if (resolucion === null) {
      logger.warn("Verificacion: estado de sesion desconocido; no se cambia nada", { solicitudId, estadoProveedor: decision.estado });
      return sinCambios;
    }
    if (!puedeAplicarse(actual, resolucion)) return sinCambios;

    const profesional = await tx.get(profesionalRef);
    const insigniaActual = profesional.exists ? estadoInsigniaIdentidad(profesional.get("insignias")) : undefined;

    let escribirInsignia = profesional.exists;
    if (profesional.exists && resolucion.insignia !== "APROBADO") {
      if (insigniaActual === "APROBADO") {
        escribirInsignia = actual === "APROBADO"; // solo `Kyc Expired` sobre la propia aprobacion
      } else if (resolucion.insignia !== "PENDIENTE") {
        // Consulta solo por igualdad (sin indice compuesto); el resto se filtra en memoria.
        const propias = await tx.get(solicitudes.where("profesionalId", "==", profesionalId).limit(MAX_SOLICITUDES_POR_PROFESIONAL));
        escribirInsignia = !propias.docs.some((d) => d.id !== solicitudId && d.get("tipo") === "IDENTIDAD" && d.get("estado") === "PENDIENTE");
      }
    }

    tx.update(solicitudRef, {
      estado: resolucion.solicitud,
      estadoProveedor: decision.estado,
      motivo: resolucion.motivo ?? FieldValue.delete(),
      rutCoincide: resolucion.rutCoincide,
      ...(resolucion.solicitud !== "PENDIENTE" ? { fechaResolucion: Timestamp.fromDate(ahora) } : {}),
      ...(eventoId !== undefined ? { ultimoEventoId: eventoId } : {}),
    });
    if (escribirInsignia) {
      tx.update(profesionalRef, camposInsigniaIdentidad(profesional.get("insignias"), resolucion.insignia, ahora));
    } else if (!profesional.exists) {
      logger.error("Verificacion: la solicitud no tiene perfil profesional; solo se actualiza la solicitud", { solicitudId });
    }
    return { estado: resolucion.solicitud, motivo: resolucion.motivo };
  });
}
