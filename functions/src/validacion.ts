import { errorDeNegocio } from "./errores.js";
import { MAX_LARGO_DIRECCION, MAX_LARGO_ID, MAX_LARGO_INDICACIONES } from "./constantes.js";

export interface DireccionSolicitada {
  calle: string;
  numero: string;
  comuna: string;
  ciudad: string;
  lat: number | null;
  lng: number | null;
  indicaciones: string | null;
}

export interface SolicitudReserva {
  profesionalId: string;
  servicioId: string;
  fechaHora: Date;
  /** null si el cliente no envio direccion. Ya normalizada (trim) si viene. */
  direccion: DireccionSolicitada | null;
}

const ISO_INSTANTE = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,9})?)?(Z|[+-]\d{2}:\d{2})$/;

/** Forma de un id que puede viajar en una URL/query y usarse como id de documento sin riesgo. */
export const ID_SEGURO = /^[A-Za-z0-9_-]{1,128}$/;

export function invalido(detalle: string) {
  return errorDeNegocio("invalid-argument", "DATOS_INVALIDOS", `Datos inválidos: ${detalle}.`);
}

export function esObjeto(valor: unknown): valor is Record<string, unknown> {
  return typeof valor === "object" && valor !== null && !Array.isArray(valor);
}

export function idObligatorio(valor: unknown, campo: string): string {
  if (typeof valor !== "string") throw invalido(`${campo} debe ser texto`);
  const id = valor.trim();
  if (id.length === 0) throw invalido(`${campo} es obligatorio`);
  // Un id con "/" cambiaria la ruta del documento que se lee (path traversal).
  if (id.length > MAX_LARGO_ID || id.includes("/")) throw invalido(`${campo} no es un id válido`);
  return id;
}

/** Texto que puede venir ausente/null; si viene debe ser string de largo acotado. */
function textoOpcional(valor: unknown, campo: string, max: number): string {
  if (valor === undefined || valor === null) return "";
  if (typeof valor !== "string") throw invalido(`${campo} debe ser texto`);
  const texto = valor.trim();
  if (texto.length > max) throw invalido(`${campo} excede ${max} caracteres`);
  return texto;
}

function coordenadaOpcional(valor: unknown, campo: string, limite: number): number | null {
  if (valor === undefined || valor === null) return null;
  if (typeof valor !== "number" || !Number.isFinite(valor) || Math.abs(valor) > limite) {
    throw invalido(`${campo} fuera de rango`);
  }
  return valor;
}

export function parseFechaHora(valor: unknown): Date {
  if (typeof valor !== "string" || !ISO_INSTANTE.test(valor.trim())) {
    throw invalido("fechaHora debe ser un instante ISO-8601 con zona (ej. 2026-10-05T13:00:00Z)");
  }
  const texto = valor.trim();
  const fecha = new Date(texto);
  if (Number.isNaN(fecha.getTime())) throw invalido("fechaHora no es una fecha válida");
  // Date.parse puede "corregir" fechas inexistentes (30 de febrero); se exige que el
  // dia calendario escrito coincida con el interpretado en la zona indicada.
  const [anio, mes, dia] = texto.slice(0, 10).split("-").map(Number);
  const diasDelMes = new Date(Date.UTC(anio, mes, 0)).getUTCDate();
  if (mes < 1 || mes > 12 || dia < 1 || dia > diasDelMes) throw invalido("fechaHora no es una fecha válida");
  return fecha;
}

function parseDireccion(valor: unknown): DireccionSolicitada | null {
  if (valor === undefined || valor === null) return null;
  if (!esObjeto(valor)) throw invalido("direccion debe ser un objeto");
  return {
    calle: textoOpcional(valor.calle, "calle", MAX_LARGO_DIRECCION),
    numero: textoOpcional(valor.numero, "numero", MAX_LARGO_DIRECCION),
    comuna: textoOpcional(valor.comuna, "comuna", MAX_LARGO_DIRECCION),
    ciudad: textoOpcional(valor.ciudad, "ciudad", MAX_LARGO_DIRECCION),
    lat: coordenadaOpcional(valor.lat, "lat", 90),
    lng: coordenadaOpcional(valor.lng, "lng", 180),
    indicaciones: textoOpcional(valor.indicaciones, "indicaciones", MAX_LARGO_INDICACIONES) || null,
  };
}

/**
 * Valida la forma del payload. No consulta Firestore. Ignora claves desconocidas
 * (en particular `modalidad`, `precio`, `comisionPorcentaje`: nunca se confia en ellas).
 * Una direccion con calle/numero/comuna vacios NO es error aqui: depende de la
 * modalidad del servicio y la resuelve el handler (DIRECCION_REQUERIDA).
 */
export function parseSolicitud(data: unknown): SolicitudReserva {
  if (!esObjeto(data)) throw invalido("el cuerpo debe ser un objeto");
  return {
    profesionalId: idObligatorio(data.profesionalId, "profesionalId"),
    servicioId: idObligatorio(data.servicioId, "servicioId"),
    fechaHora: parseFechaHora(data.fechaHora),
    direccion: parseDireccion(data.direccion),
  };
}

/** Una direccion sirve para DOMICILIO si calle, numero y comuna no estan en blanco. */
export function direccionCompleta(direccion: DireccionSolicitada | null): direccion is DireccionSolicitada {
  return direccion !== null && direccion.calle !== "" && direccion.numero !== "" && direccion.comuna !== "";
}
