import { AggregateField, type Firestore } from "firebase-admin/firestore";
import { COLECCION_PROFESIONALES, COLECCION_RESENAS } from "./constantes.js";

/** Lo que importa de una reseña para decidir si hay que recalcular. */
export interface ResenaParaCalificar {
  profesionalId: string;
  calificacion: number;
}

export interface ResultadoCalificacion {
  /** Promedio redondeado a 2 decimales; 0 si el profesional no tiene reseñas. */
  calificacionPromedio: number;
  totalResenas: number;
}

/** Extrae `profesionalId` y `calificacion` de un documento de reseña, o `null` si no es usable. */
export function leerResena(datos: Record<string, unknown> | undefined): ResenaParaCalificar | null {
  if (datos === undefined) return null;
  const { profesionalId, calificacion } = datos;
  if (typeof profesionalId !== "string" || profesionalId === "") return null;
  if (typeof calificacion !== "number" || !Number.isFinite(calificacion)) return null;
  return { profesionalId, calificacion };
}

/**
 * Profesionales cuyo promedio hay que recalcular tras un cambio en `resenas/{id}`.
 *
 * Crear o borrar una reseña afecta al profesional de esa reseña; editarla solo importa si cambió
 * la calificación o el profesional. Responder (`respuestaProfesional`) es una edición que no
 * cambia ninguno de los dos, y es el caso más frecuente después de crear: no debe costar una
 * agregación ni una escritura.
 */
export function profesionalesAfectados(
  antes: ResenaParaCalificar | null,
  despues: ResenaParaCalificar | null,
): string[] {
  if (antes === null && despues === null) return [];
  if (antes !== null && despues !== null && antes.profesionalId === despues.profesionalId) {
    return antes.calificacion === despues.calificacion ? [] : [despues.profesionalId];
  }
  return [...new Set([antes?.profesionalId, despues?.profesionalId])].filter((id): id is string => id !== undefined);
}

/** Redondeo a 2 decimales: evita guardar 4.333333333333333 y mantiene el orden de la búsqueda estable. */
export function redondearPromedio(promedio: number): number {
  return Math.round(promedio * 100) / 100;
}

/**
 * Recalcula `calificacionPromedio` y `totalResenas` de un profesional a partir de sus reseñas
 * (fuente de verdad), en vez de incrementar el valor guardado: es idempotente, así que un evento
 * repetido, reintentado o desordenado converge al mismo resultado, y también corrige valores
 * sembrados a mano. Usa agregaciones de Firestore (no descarga las reseñas) con un filtro de
 * igualdad, que no necesita índice compuesto.
 *
 * Devuelve `null` si el perfil no existe (reseña huérfana): no se crea un documento a medias y no
 * tiene sentido reintentar. No escribe si los valores ya coinciden.
 */
export async function recalcularCalificacion(
  db: Firestore,
  profesionalId: string,
): Promise<ResultadoCalificacion | null> {
  const perfil = db.collection(COLECCION_PROFESIONALES).doc(profesionalId);
  const resenas = db
    .collection(COLECCION_RESENAS)
    .where("profesionalId", "==", profesionalId)
    .aggregate({ total: AggregateField.count(), promedio: AggregateField.average("calificacion") });

  // Todo dentro de la transacción: si dos reseñas llegan a la vez, ambas agregan y escriben en
  // serie (la segunda se reintenta), así nunca gana un promedio calculado con datos más viejos.
  return db.runTransaction(async (tx) => {
    const [actual, agregado] = await Promise.all([tx.get(perfil), tx.get(resenas)]);
    if (!actual.exists) return null;

    const { total, promedio } = agregado.data();
    const resultado: ResultadoCalificacion = {
      calificacionPromedio: total === 0 || promedio === null ? 0 : redondearPromedio(promedio),
      totalResenas: total,
    };

    const datos = actual.data() ?? {};
    if (
      datos.calificacionPromedio !== resultado.calificacionPromedio ||
      datos.totalResenas !== resultado.totalResenas
    ) {
      tx.update(perfil, { ...resultado });
    }
    return resultado;
  });
}

/** Punto de entrada del trigger: recalcula a cada profesional afectado por el cambio. */
export async function calificacionTriggerHandler(
  db: Firestore,
  antes: Record<string, unknown> | undefined,
  despues: Record<string, unknown> | undefined,
): Promise<string[]> {
  const ids = profesionalesAfectados(leerResena(antes), leerResena(despues));
  for (const id of ids) await recalcularCalificacion(db, id);
  return ids;
}
