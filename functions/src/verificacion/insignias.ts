import { Timestamp } from "firebase-admin/firestore";
import { DETALLE_INSIGNIA, type EstadoInsignia } from "./estados.js";

export const TIPO_INSIGNIA_IDENTIDAD = "IDENTIDAD";

function esObjeto(valor: unknown): valor is Record<string, unknown> {
  return typeof valor === "object" && valor !== null && !Array.isArray(valor);
}

/** Estado actual de la insignia IDENTIDAD en `profesionales/{uid}.insignias`, o `undefined` si no hay. */
export function estadoInsigniaIdentidad(insignias: unknown): EstadoInsignia | undefined {
  if (!Array.isArray(insignias)) return undefined;
  const entrada = insignias.find((i) => esObjeto(i) && i.tipo === TIPO_INSIGNIA_IDENTIDAD);
  return esObjeto(entrada) ? (entrada.estado as EstadoInsignia) : undefined;
}

/**
 * Devuelve el array `insignias` con la entrada IDENTIDAD reemplazada (en su lugar) o agregada, sin
 * tocar las demas. `detalle` es el texto publico y neutro de `DETALLE_INSIGNIA`; `NO_SOLICITADO` no lo lleva.
 */
export function conInsigniaIdentidad(insignias: unknown, estado: EstadoInsignia, ahora: Date): unknown[] {
  const nueva: Record<string, unknown> = { tipo: TIPO_INSIGNIA_IDENTIDAD, estado };
  const detalle = DETALLE_INSIGNIA[estado];
  if (detalle !== undefined) nueva.detalle = detalle;
  nueva.fechaActualizacion = Timestamp.fromDate(ahora);

  const actuales = Array.isArray(insignias) ? [...(insignias as unknown[])] : [];
  const i = actuales.findIndex((x) => esObjeto(x) && x.tipo === TIPO_INSIGNIA_IDENTIDAD);
  if (i >= 0) actuales[i] = nueva;
  else actuales.push(nueva);
  return actuales;
}

/**
 * Campos de `profesionales/{uid}` que hay que escribir para dejar la insignia IDENTIDAD en `estado`.
 * `estadoVerificacionGeneral` replica ese estado porque IDENTIDAD es, por ahora, el unico tipo de
 * verificacion soportado. Al sumar otros tipos (CREDENCIALES, ...) debe pasar a derivarse del
 * conjunto de insignias (p. ej. el peor/mejor estado), no copiarse de una sola.
 */
export function camposInsigniaIdentidad(insignias: unknown, estado: EstadoInsignia, ahora: Date) {
  return { insignias: conInsigniaIdentidad(insignias, estado, ahora), estadoVerificacionGeneral: estado };
}
