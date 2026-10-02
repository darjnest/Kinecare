import { ZONA_HORARIA } from "./constantes.js";

export type DiaSemana = "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";

const DIAS: Record<string, DiaSemana> = {
  Monday: "MONDAY",
  Tuesday: "TUESDAY",
  Wednesday: "WEDNESDAY",
  Thursday: "THURSDAY",
  Friday: "FRIDAY",
  Saturday: "SATURDAY",
  Sunday: "SUNDAY",
};

export interface InstanteLocal {
  /** Fecha calendario local, "yyyy-MM-dd" (comparable como string). */
  fecha: string;
  /** Minutos desde la medianoche local (admite fraccion por los segundos). */
  minutos: number;
  diaSemana: DiaSemana;
}

const formateador = new Intl.DateTimeFormat("en-US", {
  timeZone: ZONA_HORARIA,
  hourCycle: "h23",
  weekday: "long",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
});

/** Descompone un instante UTC en hora de pared de America/Santiago (con horario de verano). */
export function aHoraLocal(instante: Date): InstanteLocal {
  const partes: Record<string, string> = {};
  for (const p of formateador.formatToParts(instante)) partes[p.type] = p.value;
  const diaSemana = DIAS[partes.weekday];
  if (diaSemana === undefined) throw new Error(`Dia de semana inesperado: ${partes.weekday}`);
  return {
    fecha: `${partes.year}-${partes.month}-${partes.day}`,
    minutos: Number(partes.hour) * 60 + Number(partes.minute) + Number(partes.second) / 60,
    diaSemana,
  };
}

/**
 * "HH:mm" (o "HH:mm:ss") -> minutos desde medianoche, con los segundos como fraccion.
 * Acepta "24:00"/"24:00:00" solo como fin de dia. null si no es valida.
 * Los segundos se toleran porque el cliente Android lee la disponibilidad con
 * `LocalTime.parse`, que acepta ambos formatos: si el backend solo aceptara "HH:mm",
 * la app ofreceria horarios que crearReserva rechaza (FUERA_DE_HORARIO).
 */
export function parseHoraMinutos(valor: unknown): number | null {
  if (typeof valor !== "string") return null;
  if (valor === "24:00" || valor === "24:00:00") return 1440;
  const m = /^([01]\d|2[0-3]):([0-5]\d)(?::([0-5]\d))?$/.exec(valor);
  return m === null ? null : Number(m[1]) * 60 + Number(m[2]) + Number(m[3] ?? 0) / 60;
}

/**
 * Indica si el slot [inicio, inicio + duracion) cabe completo, en hora de Chile, dentro de
 * alguna entrada `activo == true` de `disponibilidad` para ese dia de la semana.
 * El slot no puede cruzar la medianoche local. Entradas mal formadas se ignoran.
 */
export function slotDentroDeDisponibilidad(disponibilidad: unknown, inicio: Date, duracionMinutos: number): boolean {
  if (!Array.isArray(disponibilidad)) return false;
  if (!Number.isFinite(duracionMinutos) || duracionMinutos <= 0 || duracionMinutos > 24 * 60) return false;

  const desde = aHoraLocal(inicio);
  const hasta = aHoraLocal(new Date(inicio.getTime() + duracionMinutos * 60_000));

  let finMinutos: number;
  if (hasta.fecha === desde.fecha) {
    finMinutos = hasta.minutos;
  } else if (hasta.fecha > desde.fecha && hasta.minutos === 0) {
    // Termina exactamente a medianoche: no cruza, ocupa hasta el fin del dia.
    finMinutos = 1440;
  } else {
    return false;
  }

  return disponibilidad.some((entrada) => {
    if (typeof entrada !== "object" || entrada === null) return false;
    const d = entrada as Record<string, unknown>;
    if (d.activo !== true || d.diaSemana !== desde.diaSemana) return false;
    const horaInicio = parseHoraMinutos(d.horaInicio);
    const horaFin = parseHoraMinutos(d.horaFin);
    if (horaInicio === null || horaFin === null) return false;
    return desde.minutos >= horaInicio && finMinutos <= horaFin;
  });
}
