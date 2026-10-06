// Pagina HTML que ve el profesional al volver de Mercado Pago (se abre en el navegador del
// telefono). Autocontenida: sin JS ni recursos externos. No interpola nada del request.

export type ResultadoCallback = "CONECTADO" | "CANCELADO" | "FALLIDO";

export interface RespuestaHtml {
  status: 200 | 400;
  html: string;
}

/** Cabeceras de toda respuesta HTML de este flujo (incluye 405). */
export const CABECERAS_HTML: Readonly<Record<string, string>> = {
  "Cache-Control": "no-store",
  "Content-Type": "text/html; charset=utf-8",
  "Referrer-Policy": "no-referrer",
  "X-Content-Type-Options": "nosniff",
  "Content-Security-Policy": "default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'",
};

export function escaparHtml(texto: string): string {
  return texto
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

/** Documento HTML minimo. `titulo` y `mensaje` se escapan. */
export function renderPagina(titulo: string, mensaje: string): string {
  const t = escaparHtml(titulo);
  const m = escaparHtml(mensaje);
  return (
    `<!DOCTYPE html><html lang="es"><head><meta charset="utf-8">` +
    `<meta name="viewport" content="width=device-width, initial-scale=1">` +
    `<title>KineCare - ${t}</title>` +
    `<style>body{font-family:sans-serif;max-width:28rem;margin:4rem auto;padding:0 1rem;text-align:center}` +
    `h1{font-size:1.4rem}</style></head><body><h1>${t}</h1><p>${m}</p></body></html>`
  );
}

const TEXTOS: Record<ResultadoCallback, { status: 200 | 400; titulo: string; mensaje: string }> = {
  CONECTADO: {
    status: 200,
    titulo: "Cuenta conectada",
    mensaje: "Tu cuenta de Mercado Pago quedó conectada. Ya puedes volver a KineCare.",
  },
  CANCELADO: {
    status: 400,
    titulo: "Cancelaste la conexión",
    mensaje: "No se conectó tu cuenta de Mercado Pago. Vuelve a KineCare si quieres intentarlo de nuevo.",
  },
  FALLIDO: {
    status: 400,
    titulo: "No pudimos conectar tu cuenta",
    mensaje: "Ocurrió un problema al conectar Mercado Pago. Vuelve a KineCare e inténtalo de nuevo.",
  },
};

export function paginaDeResultado(resultado: ResultadoCallback): RespuestaHtml {
  const { status, titulo, mensaje } = TEXTOS[resultado];
  return { status, html: renderPagina(titulo, mensaje) };
}
