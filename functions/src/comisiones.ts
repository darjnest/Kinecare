import {
  FieldValue,
  Timestamp,
  type DocumentReference,
  type DocumentSnapshot,
  type Firestore,
  type Query,
  type QueryDocumentSnapshot,
  type Transaction,
} from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import {
  COLECCION_COMISIONES,
  COMISION_PORCENTAJE,
  MAX_FEE_PORCENTAJE_DEL_MONTO,
  RESERVA_COMISION_VIGENCIA_HORAS,
  TOPE_SALDO_COMISION_CLP,
} from "./constantes.js";
import { calcularComision, type EstadoPago } from "./mercadopago/estados.js";

/**
 * Comision de las atenciones que no se pagaron por la app (efectivo). La plataforma no retiene ese
 * dinero, asi que cada atencion completada sin pago `AUTORIZADO` deja una entrada
 * `comisionesPendientes/{reservaId}` (id = reservaId: completar una reserva dos veces no la duplica) que
 * el profesional adeuda. Se salda de dos formas: descontandola del `marketplace_fee` de un pago con
 * tarjeta (`via = DESCUENTO`) o con un pago directo a la plataforma (`via = PAGO_DIRECTO`, ver
 * `cobrosComision.ts`). Solo se escribe con Admin SDK.
 */
export type EstadoComision = "PENDIENTE" | "PAGADA" | "CONDONADA" | "ANULADA";
export type ViaPagoComision = "DESCUENTO" | "PAGO_DIRECTO";

const MS_HORA = 3_600_000;

/** Entrada leida de Firestore, con lo necesario para decidir cuales se cobran. */
export interface EntradaComision {
  id: string;
  ref: DocumentReference;
  monto: number;
  creadoEn: number;
  /** Id del pago o cobro que la tiene reservada, o null si esta libre. */
  reservadaPor: string | null;
  reservadaEn: number | null;
}

/**
 * Comision en CLP de una atencion, o `null` si no hay nada que cobrar (monto no valido, servicio
 * gratuito o comision de 0). Completar una atencion nunca debe fallar por esto, por eso no lanza.
 */
export function comisionDeAtencion(monto: unknown, porcentaje: unknown): number | null {
  if (typeof monto !== "number" || typeof porcentaje !== "number") return null;
  try {
    const comision = calcularComision(monto, porcentaje);
    return comision > 0 ? comision : null;
  } catch {
    return null;
  }
}

/**
 * Deja constancia de la comision adeudada al completar una atencion que no se pago por la app. Va dentro
 * de la transaccion de `completarReserva`: no lee, solo escribe, asi respeta "lecturas antes que escrituras".
 */
export function registrarComisionDeAtencion(tx: Transaction, db: Firestore, reserva: DocumentSnapshot): void {
  const pago = reserva.get("pago") as { estado?: string; monto?: unknown } | undefined;
  if (pago?.estado === "AUTORIZADO") return; // la comision ya se cobro como marketplace_fee

  const porcentaje =
    typeof reserva.get("comisionPorcentaje") === "number" ? (reserva.get("comisionPorcentaje") as number) : COMISION_PORCENTAJE;
  const monto = comisionDeAtencion(pago?.monto, porcentaje);
  if (monto === null) {
    logger.warn("Comision: atencion completada sin monto cobrable; no se registra comision", { reservaId: reserva.id });
    return;
  }
  tx.set(db.collection(COLECCION_COMISIONES).doc(reserva.id), {
    profesionalId: reserva.get("profesionalId") as string,
    reservaId: reserva.id,
    monto,
    montoAtencion: pago?.monto as number,
    estado: "PENDIENTE" satisfies EstadoComision,
    origen: "EFECTIVO",
    reservadaPor: null,
    reservadaEn: null,
    creadoEn: FieldValue.serverTimestamp(),
    actualizadoEn: FieldValue.serverTimestamp(),
  });
}

/** Consulta de las entradas `PENDIENTE` de un profesional (dos igualdades: no necesita indice compuesto). */
export function consultaPendientes(db: Firestore, profesionalId: string): Query {
  return db
    .collection(COLECCION_COMISIONES)
    .where("profesionalId", "==", profesionalId)
    .where("estado", "==", "PENDIENTE" satisfies EstadoComision);
}

function milis(valor: unknown): number | null {
  return valor instanceof Timestamp ? valor.toMillis() : null;
}

export function aEntradas(docs: readonly QueryDocumentSnapshot[]): EntradaComision[] {
  return docs.map((doc) => ({
    id: doc.id,
    ref: doc.ref,
    monto: doc.get("monto") as number,
    creadoEn: milis(doc.get("creadoEn")) ?? 0,
    reservadaPor: (doc.get("reservadaPor") as string | null | undefined) ?? null,
    reservadaEn: milis(doc.get("reservadaEn")),
  }));
}

/** Libre = nadie la reservo, o la reserva de un pago/cobro sin resolver ya vencio. */
export function estaLibre(entrada: EntradaComision, ahora: Date): boolean {
  if (entrada.reservadaPor === null) return true;
  if (entrada.reservadaEn === null) return true;
  return ahora.getTime() - entrada.reservadaEn >= RESERVA_COMISION_VIGENCIA_HORAS * MS_HORA;
}

/** Suma de lo adeudado, incluida la parte reservada por un pago o cobro en curso. */
export function sumar(entradas: readonly EntradaComision[]): number {
  return entradas.reduce((acc, e) => acc + e.monto, 0);
}

/** Lo adeudado por un profesional (CLP): base del bloqueo por saldo. */
export async function saldoComisionPendiente(db: Firestore, profesionalId: string): Promise<number> {
  const snapshot = await consultaPendientes(db, profesionalId).get();
  return sumar(aEntradas(snapshot.docs));
}

/** `true` si el profesional alcanzo el tope: no puede aceptar ni recibir solicitudes nuevas. */
export async function profesionalBloqueadoPorSaldo(db: Firestore, profesionalId: string): Promise<boolean> {
  return (await saldoComisionPendiente(db, profesionalId)) >= TOPE_SALDO_COMISION_CLP;
}

/**
 * Cuanto se puede sumar al `marketplace_fee` de un pago sin que el fee total pase de la mitad del monto
 * (la comision base ya cuenta dentro de ese tope).
 */
export function margenDeDescuento(monto: number, comisionBase: number): number {
  return Math.max(0, Math.floor(monto * MAX_FEE_PORCENTAJE_DEL_MONTO) - comisionBase);
}

/**
 * Elige, de las mas antiguas a las mas nuevas, las entradas libres que caben en `margen`. Una entrada
 * nunca se parte: o se descuenta entera o queda para otro pago.
 */
export function seleccionarParaDescuento(libres: readonly EntradaComision[], margen: number): EntradaComision[] {
  const elegidas: EntradaComision[] = [];
  let acumulado = 0;
  for (const entrada of [...libres].sort((a, b) => a.creadoEn - b.creadoEn)) {
    if (acumulado + entrada.monto > margen) continue;
    elegidas.push(entrada);
    acumulado += entrada.monto;
  }
  return elegidas;
}

/** Reserva las entradas para que un pago o cobro en curso no compita con otro por las mismas. */
export function reservarEntradas(tx: Transaction, entradas: readonly EntradaComision[], duenoId: string): void {
  for (const entrada of entradas) {
    tx.update(entrada.ref, {
      reservadaPor: duenoId,
      reservadaEn: FieldValue.serverTimestamp(),
      actualizadoEn: FieldValue.serverTimestamp(),
    });
  }
}

/** Lee las entradas por id (dentro de la transaccion, antes de escribir). Las que no existen se omiten. */
export async function leerEntradas(tx: Transaction, db: Firestore, ids: readonly string[]): Promise<DocumentSnapshot[]> {
  const docs = await Promise.all(ids.map((id) => tx.get(db.collection(COLECCION_COMISIONES).doc(id))));
  return docs.filter((doc) => doc.exists);
}

/**
 * Refleja en las entradas el resultado de un pago o cobro (`duenoId`):
 * - `AUTORIZADO`: pasan a `PAGADA` las que sigan `PENDIENTE` y libres o reservadas por el mismo dueño.
 *   Una que otro pago ya se llevo se deja y se registra: el cobro de mas se resuelve a mano.
 * - `RECHAZADO`: se liberan las que el dueño tenia reservadas.
 * - `REEMBOLSADO`: las que este dueño habia saldado vuelven a `PENDIENTE` (el fee se devolvio al cliente).
 */
export function aplicarResultadoAEntradas(
  tx: Transaction,
  entradas: readonly DocumentSnapshot[],
  duenoId: string,
  nuevo: EstadoPago,
  via: ViaPagoComision,
): void {
  for (const entrada of entradas) {
    const estado = entrada.get("estado") as EstadoComision;
    const reservadaPor = (entrada.get("reservadaPor") as string | null | undefined) ?? null;
    const base = { actualizadoEn: FieldValue.serverTimestamp() };

    if (nuevo === "AUTORIZADO") {
      if (estado === "PENDIENTE" && (reservadaPor === null || reservadaPor === duenoId)) {
        tx.update(entrada.ref, {
          estado: "PAGADA" satisfies EstadoComision,
          pagadaVia: via,
          pagadaPor: duenoId,
          pagadaEn: FieldValue.serverTimestamp(),
          reservadaPor: null,
          reservadaEn: null,
          ...base,
        });
      } else if (!(estado === "PAGADA" && entrada.get("pagadaPor") === duenoId)) {
        logger.warn("Comision: entrada ya no esta disponible para saldarse con este pago; revisar a mano", {
          entradaId: entrada.id,
          duenoId,
          estado,
          reservadaPor,
        });
      }
    } else if (nuevo === "RECHAZADO") {
      if (estado === "PENDIENTE" && reservadaPor === duenoId) {
        tx.update(entrada.ref, { reservadaPor: null, reservadaEn: null, ...base });
      }
    } else if (nuevo === "REEMBOLSADO") {
      if (estado === "PAGADA" && entrada.get("pagadaPor") === duenoId) {
        tx.update(entrada.ref, {
          estado: "PENDIENTE" satisfies EstadoComision,
          pagadaVia: FieldValue.delete(),
          pagadaPor: FieldValue.delete(),
          pagadaEn: FieldValue.delete(),
          reservadaPor: null,
          reservadaEn: null,
          ...base,
        });
      }
    }
  }
}

/**
 * Si el cliente termino pagando con tarjeta una atencion que ya se habia completado (dejo abierto
 * Checkout Pro), la comision ya viaja como `marketplace_fee`: se anula la entrada para no cobrarla dos veces.
 */
export function anularEntradaSiHayPagoConTarjeta(tx: Transaction, entrada: DocumentSnapshot): void {
  if (!entrada.exists || entrada.get("estado") !== "PENDIENTE") return;
  tx.update(entrada.ref, {
    estado: "ANULADA" satisfies EstadoComision,
    motivoAnulacion: "PAGADA_CON_TARJETA",
    reservadaPor: null,
    reservadaEn: null,
    actualizadoEn: FieldValue.serverTimestamp(),
  });
}
