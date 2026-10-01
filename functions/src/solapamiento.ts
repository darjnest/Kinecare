import {
  DURACION_MAXIMA_MINUTOS,
  DURACION_POR_DEFECTO_MINUTOS,
  ESTADOS_QUE_OCUPAN_AGENDA,
} from "./constantes.js";

const MS_MINUTO = 60_000;

export interface ReservaExistente {
  estado: unknown;
  inicio: Date;
  duracionMinutos: unknown;
}

/** Intervalos semiabiertos [inicio, fin): dos citas contiguas no se solapan. */
export function intervalosSeSolapan(inicioA: Date, finA: Date, inicioB: Date, finB: Date): boolean {
  return inicioA < finB && inicioB < finA;
}

export function fin(inicio: Date, duracionMinutos: number): Date {
  return new Date(inicio.getTime() + duracionMinutos * MS_MINUTO);
}

/**
 * Ventana de `fechaHora` a consultar para encontrar todas las reservas que podrian solaparse
 * con [inicio, fin): cualquiera que empiece antes de `inicio - duracion maxima` ya termino.
 */
export function ventanaDeConsulta(inicio: Date, finSlot: Date): { desde: Date; hasta: Date } {
  return { desde: new Date(inicio.getTime() - DURACION_MAXIMA_MINUTOS * MS_MINUTO), hasta: finSlot };
}

function duracionEfectiva(valor: unknown): number {
  return typeof valor === "number" && Number.isFinite(valor) && valor > 0 ? valor : DURACION_POR_DEFECTO_MINUTOS;
}

/** true si alguna reserva en estado activo se solapa con [inicio, finSlot). */
export function hayReservaSolapada(existentes: ReservaExistente[], inicio: Date, finSlot: Date): boolean {
  return existentes.some((r) => {
    if (!(ESTADOS_QUE_OCUPAN_AGENDA as readonly unknown[]).includes(r.estado)) return false;
    return intervalosSeSolapan(inicio, finSlot, r.inicio, fin(r.inicio, duracionEfectiva(r.duracionMinutos)));
  });
}
