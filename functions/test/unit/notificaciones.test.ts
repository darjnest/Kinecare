import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { Timestamp } from "firebase-admin/firestore";
import { armarNotificacion, fechaLegible } from "../../src/notificaciones.js";

// 2026-10-16T18:00Z = viernes 15:00 en Chile (UTC-3 desde septiembre).
const FECHA = new Date("2026-10-16T18:00:00Z");
const base = { clienteId: "cli", profesionalId: "pro", fechaHora: Timestamp.fromDate(FECHA) };
const reserva = (estado: string, extra: Record<string, unknown> = {}) => ({ ...base, estado, ...extra });

describe("fechaLegible", () => {
  it("usa la hora de Chile", () => {
    assert.equal(fechaLegible(FECHA), "viernes 16 de octubre a las 15:00");
  });
  it("respeta el horario de invierno (UTC-4)", () => {
    assert.equal(fechaLegible(new Date("2026-07-06T13:30:00Z")), "lunes 6 de julio a las 09:30");
  });
});

describe("armarNotificacion: destinatario y contenido", () => {
  it("una reserva nueva SOLICITADA avisa al profesional", () => {
    const n = armarNotificacion("r1", undefined, reserva("SOLICITADA"));
    assert.ok(n);
    assert.equal(n.destinatarioUid, "pro");
    assert.equal(n.titulo, "Nueva solicitud");
    assert.match(n.cuerpo, /viernes 16 de octubre a las 15:00/);
    assert.deepEqual(n.data, { tipo: "NUEVA_SOLICITUD", reservaId: "r1" });
  });

  it("CONFIRMADA avisa al cliente", () => {
    const n = armarNotificacion("r1", reserva("SOLICITADA"), reserva("CONFIRMADA"));
    assert.ok(n);
    assert.equal(n.destinatarioUid, "cli");
    assert.equal(n.data.tipo, "RESERVA_CONFIRMADA");
    assert.equal(n.titulo, "Reserva confirmada");
  });

  it("RECHAZADA avisa al cliente", () => {
    const n = armarNotificacion("r1", reserva("SOLICITADA"), reserva("RECHAZADA"));
    assert.ok(n);
    assert.equal(n.destinatarioUid, "cli");
    assert.equal(n.data.tipo, "RESERVA_RECHAZADA");
  });

  it("RECHAZADA con expirada=true es un aviso distinto, no dice que el profesional rechazo", () => {
    const n = armarNotificacion("r1", reserva("SOLICITADA"), reserva("RECHAZADA", { expirada: true }));
    assert.ok(n);
    assert.equal(n.destinatarioUid, "cli");
    assert.equal(n.data.tipo, "SOLICITUD_EXPIRADA");
    assert.doesNotMatch(n.cuerpo, /rechaz/i);
  });

  it("CANCELADA_CLIENTE avisa al profesional y CANCELADA_PROFESIONAL al cliente", () => {
    const c = armarNotificacion("r1", reserva("CONFIRMADA"), reserva("CANCELADA_CLIENTE"));
    assert.ok(c);
    assert.equal(c.destinatarioUid, "pro");
    assert.equal(c.data.tipo, "RESERVA_CANCELADA");
    const p = armarNotificacion("r1", reserva("CONFIRMADA"), reserva("CANCELADA_PROFESIONAL"));
    assert.ok(p);
    assert.equal(p.destinatarioUid, "cli");
    assert.equal(p.data.tipo, "RESERVA_CANCELADA");
  });

  it("COMPLETADA pide la reseña al cliente", () => {
    const n = armarNotificacion("r1", reserva("CONFIRMADA"), reserva("COMPLETADA"));
    assert.ok(n);
    assert.equal(n.destinatarioUid, "cli");
    assert.equal(n.titulo, "¿Cómo fue tu atención?");
    assert.match(n.cuerpo, /reseña/);
    assert.deepEqual(n.data, { tipo: "DEJAR_RESENA", reservaId: "r1" });
  });

  it("el payload data solo lleva strings", () => {
    const n = armarNotificacion("r1", reserva("SOLICITADA"), reserva("CONFIRMADA"));
    assert.ok(n);
    for (const v of Object.values(n.data)) assert.equal(typeof v, "string");
  });

  it("sin fechaHora igual arma el mensaje (sin fecha)", () => {
    const n = armarNotificacion("r1", undefined, { clienteId: "cli", profesionalId: "pro", estado: "SOLICITADA" });
    assert.ok(n);
    assert.doesNotMatch(n.cuerpo, /undefined/);
  });
});

describe("armarNotificacion: cuando NO notifica", () => {
  it("borrar la reserva", () => {
    assert.equal(armarNotificacion("r1", reserva("SOLICITADA"), undefined), null);
  });
  it("cambios que no tocan el estado (pago, actualizadoEn)", () => {
    assert.equal(armarNotificacion("r1", reserva("CONFIRMADA"), reserva("CONFIRMADA", { pago: { estado: "AUTORIZADO" } })), null);
  });
  it("EN_CURSO", () => {
    assert.equal(armarNotificacion("r1", reserva("CONFIRMADA"), reserva("EN_CURSO")), null);
  });
  it("una reserva que aparece ya en un estado distinto de SOLICITADA (carga/migracion) avisa solo si el estado lo amerita", () => {
    // Creada directo como CONFIRMADA (no ocurre en el flujo real): el estado "cambia" de nada a CONFIRMADA.
    const n = armarNotificacion("r1", undefined, reserva("CONFIRMADA"));
    assert.ok(n);
    assert.equal(n.destinatarioUid, "cli");
  });
  it("vuelta a SOLICITADA sobre un doc existente no avisa", () => {
    assert.equal(armarNotificacion("r1", reserva("CONFIRMADA"), reserva("SOLICITADA")), null);
  });
  it("sin clienteId o profesionalId", () => {
    assert.equal(armarNotificacion("r1", undefined, { estado: "SOLICITADA", profesionalId: "pro" }), null);
    assert.equal(armarNotificacion("r1", undefined, { estado: "SOLICITADA", clienteId: "cli" }), null);
  });
  it("estado desconocido", () => {
    assert.equal(armarNotificacion("r1", reserva("SOLICITADA"), reserva("LO_QUE_SEA")), null);
  });
});
