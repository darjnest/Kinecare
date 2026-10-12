import { FieldValue, Timestamp, type Firestore } from "firebase-admin/firestore";

/**
 * `EstadoReserva` (docs/DOMAIN.md y el enum de `:core:common`) no tiene `EXPIRADA`, y un estado
 * desconocido romperia el mapeo de la app Android. Mientras el enum no lo incorpore, una
 * solicitud sin respuesta cuya hora pasó queda en `RECHAZADA` con `expirada: true` (y
 * `expiradaEn`): la app que no conoce el campo la ve como rechazada, y la que sí, como "Expirada".
 * Cuando exista `EXPIRADA` en el dominio basta cambiar esta constante.
 */
export const ESTADO_AL_EXPIRAR = "RECHAZADA";

/** Docs que se leen por pasada de la consulta y por transaccion. */
export const TAMANO_LOTE = 100;
/** Tope de lotes por ejecucion (15 min entre ejecuciones; el resto lo toma la siguiente). */
export const MAX_LOTES = 20;

export interface ResultadoExpiracion {
  expiradas: number;
  lotes: number;
}

/**
 * Pasa a `ESTADO_AL_EXPIRAR` (+ `expirada: true`) las reservas `SOLICITADA` cuya `fechaHora` ya
 * pasó. Separada del wrapper `onSchedule` para probarla contra el emulador.
 *
 * - **Idempotente:** la consulta solo devuelve `SOLICITADA`, y cada lote vuelve a leer sus
 *   documentos dentro de una transaccion y salta los que ya no estan `SOLICITADA` o cuya cita ya
 *   no esta vencida. Si el profesional acepta/rechaza (o el cliente cancela) justo entonces, esa
 *   respuesta gana y la expiracion no la pisa.
 * - **Por lotes:** `TAMANO_LOTE` documentos por consulta/transaccion, hasta `MAX_LOTES` lotes por
 *   ejecucion. Una reserva sin `fechaHora` no entra a la consulta (nunca se expira a ciegas).
 * - Requiere el indice compuesto `reservas(estado ASC, fechaHora ASC)`.
 * - No toca pagos (una `SOLICITADA` nunca se paga) ni `bloqueosAgenda` (una cita pasada no ocupa
 *   agenda futura).
 */
export async function expirarSolicitudesHandler(
  db: Firestore,
  ahora: Date,
  tamanoLote: number = TAMANO_LOTE,
  maxLotes: number = MAX_LOTES,
): Promise<ResultadoExpiracion> {
  const corte = Timestamp.fromDate(ahora);
  let expiradas = 0;
  let lotes = 0;

  while (lotes < maxLotes) {
    const candidatas = await db
      .collection("reservas")
      .where("estado", "==", "SOLICITADA")
      .where("fechaHora", "<", corte)
      .orderBy("fechaHora", "asc")
      .limit(tamanoLote)
      .get();
    if (candidatas.empty) break;
    lotes += 1;

    const expiradasEnLote = await db.runTransaction(async (tx) => {
      const actuales = await tx.getAll(...candidatas.docs.map((d) => d.ref));
      let n = 0;
      for (const doc of actuales) {
        const fechaHora = doc.get("fechaHora");
        if (!doc.exists || doc.get("estado") !== "SOLICITADA") continue;
        if (!(fechaHora instanceof Timestamp) || fechaHora.toMillis() >= ahora.getTime()) continue;
        tx.update(doc.ref, {
          estado: ESTADO_AL_EXPIRAR,
          expirada: true,
          expiradaEn: FieldValue.serverTimestamp(),
          actualizadoEn: FieldValue.serverTimestamp(),
        });
        n += 1;
      }
      return n;
    });
    expiradas += expiradasEnLote;
    // Lote incompleto: no quedan mas. Lote que no avanzo (todo concurrente): evita girar en vacio.
    if (candidatas.size < tamanoLote || expiradasEnLote === 0) break;
  }
  return { expiradas, lotes };
}
