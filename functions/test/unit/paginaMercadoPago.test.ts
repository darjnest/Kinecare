import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { CABECERAS_HTML, escaparHtml, paginaDeResultado, renderPagina } from "../../src/paginaMercadoPago.js";

describe("escaparHtml", () => {
  it("escapa los cinco caracteres peligrosos", () => {
    assert.equal(escaparHtml(`<script>alert("x")&'y'</script>`), "&lt;script&gt;alert(&quot;x&quot;)&amp;&#39;y&#39;&lt;/script&gt;");
  });

  it("no escapa dos veces el ampersand ni toca texto normal", () => {
    assert.equal(escaparHtml("a & b"), "a &amp; b");
    assert.equal(escaparHtml("Cuenta conectada, vuelve a KineCare"), "Cuenta conectada, vuelve a KineCare");
  });
});

describe("renderPagina", () => {
  it("escapa titulo y mensaje: no se puede inyectar markup", () => {
    const html = renderPagina(`<img src=x onerror=alert(1)>`, `"><script>alert(1)</script>`);
    assert.ok(!html.includes("<img"));
    assert.ok(!html.includes("<script"));
    assert.ok(html.includes("&lt;img src=x onerror=alert(1)&gt;"));
  });

  it("es autocontenida: sin scripts ni recursos externos", () => {
    const html = renderPagina("t", "m");
    assert.ok(!/<script/i.test(html));
    assert.ok(!/<link/i.test(html));
    assert.ok(!/src=/i.test(html));
    assert.ok(!/https?:\/\//i.test(html));
    assert.ok(html.startsWith("<!DOCTYPE html>"));
    assert.ok(html.includes('lang="es"'));
  });
});

describe("paginaDeResultado", () => {
  it("CONECTADO responde 200", () => {
    const r = paginaDeResultado("CONECTADO");
    assert.equal(r.status, 200);
    assert.ok(r.html.includes("Cuenta conectada"));
    assert.ok(r.html.includes("volver a KineCare"));
  });

  it("CANCELADO y FALLIDO responden 400 con textos distintos", () => {
    const cancelado = paginaDeResultado("CANCELADO");
    const fallido = paginaDeResultado("FALLIDO");
    assert.equal(cancelado.status, 400);
    assert.equal(fallido.status, 400);
    assert.ok(cancelado.html.includes("Cancelaste"));
    assert.ok(fallido.html.includes("No pudimos conectar"));
    assert.ok(!fallido.html.includes("Cancelaste"));
    assert.ok(!cancelado.html.includes("No pudimos conectar"));
  });
});

describe("CABECERAS_HTML", () => {
  it("incluye las cabeceras de seguridad del contrato", () => {
    assert.equal(CABECERAS_HTML["Cache-Control"], "no-store");
    assert.equal(CABECERAS_HTML["Content-Type"], "text/html; charset=utf-8");
    assert.equal(CABECERAS_HTML["Referrer-Policy"], "no-referrer");
    assert.equal(CABECERAS_HTML["X-Content-Type-Options"], "nosniff");
  });
});
