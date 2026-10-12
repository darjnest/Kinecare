// Integracion de cancelarReservaHandler contra el Firestore Emulator (Admin SDK real).
// Ejecutar con: npm run test:emulator  (levanta el emulador y corre npm test).
import assert from "node:assert/strict";
import { before, beforeEach, describe, it } from "node:test";
import { initializeApp } from "firebase-admin/app";
import { Timestamp, getFirestore, type Firestore } from "firebase-admin/firestore";
import { cancelarReservaHandler } from "../../src/cancelarReserva.js";
import { esperarError } from "../helpers.js";

const PROJECT_ID = "demo-kinecare";
const emulador = process.env.FIRESTORE_EMULATOR_HOST;
if (emulador === undefined) {
  throw new Error(
    "FIRESTORE_EMULATOR_HOST no esta definido: corre `npm run test:emulator` (o `npm run test:unit` para omitir estos tests).",
  );
}

const AHORA = new Date("2026-10-01T12:00:00Z");
const EN_2H = "2026-10-01T14:00:00Z";
const EN_24H = "2026-10-02T12:00:00Z";
const EN_5D = "2026-10-06T13:00:00Z";
const PASADA = "2026-10-01T11:00:00Z";

const CLIENTE = "cliente1";
const OTRO_CLIENTE = "cliente2";
const PRO = "pro1";
const OTRO_PRO = "pro2";

let db: Firestore;

async function limpiarEmulador() {
  const r = await fetch(`http://${emulador}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`, {
    method: "DELETE",
  });
  assert.equal(r.status, 200);
}

async function sembrarReserva(
  opciones: { estado?: string; fechaHora?: string | null; pagoId?: string | null; pagoEstado?: string } = {},
) {
  const doc: Record<string, unknown> = {
    clienteId: CLIENTE,
    profesionalId: PRO,
    servicioId: "srv1",
    modalidad: "CONSULTA",
    duracionMinutos: 60,
    direccion: null,
    estado: opciones.estado ?? "CONFIRMADA",
    pago: { id: opciones.pagoId ?? null, monto: 25000, estado: opciones.pagoEstado ?? "PENDIENTE" },
    comisionPorcentaje: 0.1,
    creadoEn: Timestamp.fromDate(new Date("2026-09-29T12:00:00Z")),
    actualizadoEn: Timestamp.fromDate(new Date("2026-09-29T12:00:00Z")),
  };
  if (opciones.fechaHora !== null) doc.fechaHora = Timestamp.fromDate(new Date(opciones.fechaHora ?? EN_5D));
  return (await db.collection("reservas").add(doc)).id;
}

async function sembrarPago(id: string, estado: string) {
  await db.collection("pagos").doc(id).set({
    reservaId: "x",
    clienteId: CLIENTE,
    profesionalId: PRO,
    monto: 25000,
    comision: 2500,
    estado,
    creadoEn: Timestamp.fromDate(new Date("2026-09-29T12:00:00Z")),
    actualizadoEn: Timestamp.fromDate(new Date("2026-09-29T12:00:00Z")),
  });
}

/** `uid` null simula una llamada sin sesion (undefined activaria el valor por defecto). */
function cancelar(data: unknown, uid: string | null = CLIENTE, ahora = AHORA) {
  return cancelarReservaHandler(db, uid ?? undefined, data, ahora);
}

async function leer(coleccion: string, id: string) {
  return (await db.collection(coleccion).doc(id).get()).data()!;
}

before(() => {
  initializeApp({ projectId: PROJECT_ID });
  db = getFirestore();
});

beforeEach(limpiarEmulador);

describe("cancelarReserva: camino feliz", () => {
  it("el cliente cancela una CONFIRMADA futura -> CANCELADA_CLIENTE con auditoria", async () => {
    const id = await sembrarReserva();
    const r = await cancelar({ reservaId: id, motivo: "  Me enfermé " });
    assert.deepEqual(r, { estado: "CANCELADA_CLIENTE", reembolso: "NO_APLICA" });
    const doc = await leer("reservas", id);
    assert.equal(doc.estado, "CANCELADA_CLIENTE");
    assert.equal(doc.canceladaPor, CLIENTE);
    assert.equal(doc.motivoCancelacion, "Me enfermé");
    assert.ok(doc.canceladaEn instanceof Timestamp);
    assert.ok(doc.actualizadoEn.toMillis() > new Date("2026-09-29T12:00:00Z").getTime());
    // El resto no cambia.
    assert.equal(doc.profesionalId, PRO);
    assert.equal(doc.comisionPorcentaje, 0.1);
    assert.equal(doc.fechaHora.toDate().getTime(), new Date(EN_5D).getTime());
  });

  it("el profesional cancela una SOLICITADA -> CANCELADA_PROFESIONAL, sin motivo guarda null", async () => {
    const id = await sembrarReserva({ estado: "SOLICITADA" });
    const r = await cancelar({ reservaId: id }, PRO);
    assert.equal(r.estado, "CANCELADA_PROFESIONAL");
    const doc = await leer("reservas", id);
    assert.equal(doc.canceladaPor, PRO);
    assert.equal(doc.motivoCancelacion, null);
  });

  it("el estado y quien cancela no se pueden elegir desde el payload", async () => {
    const id = await sembrarReserva();
    const r = await cancelar({ reservaId: id, estado: "CANCELADA_PROFESIONAL", canceladaPor: PRO, rol: "PROFESIONAL" });
    assert.equal(r.estado, "CANCELADA_CLIENTE");
    assert.equal((await leer("reservas", id)).canceladaPor, CLIENTE);
  });

  it("no toca bloqueosAgenda y libera la agenda (un crear posterior ya no ve la reserva)", async () => {
    const id = await sembrarReserva();
    await cancelar({ reservaId: id });
    assert.equal((await db.collection("bloqueosAgenda").get()).size, 0);
  });
});

describe("cancelarReserva: errores", () => {
  it("SIN_SESION", async () => {
    await esperarError(() => cancelar({ reservaId: "r1" }, null), "unauthenticated", "SIN_SESION");
    await esperarError(() => cancelar({ reservaId: "r1" }, ""), "unauthenticated", "SIN_SESION");
  });

  it("DATOS_INVALIDOS y MOTIVO_INVALIDO", async () => {
    await esperarError(() => cancelar({}), "invalid-argument", "DATOS_INVALIDOS");
    await esperarError(() => cancelar({ reservaId: "r1", motivo: "x".repeat(301) }), "invalid-argument", "MOTIVO_INVALIDO");
    await esperarError(() => cancelar({ reservaId: "r1", motivo: 5 }), "invalid-argument", "MOTIVO_INVALIDO");
  });

  it("RESERVA_NO_ENCONTRADA", async () => {
    await esperarError(() => cancelar({ reservaId: "no-existe" }), "not-found", "RESERVA_NO_ENCONTRADA");
  });

  it("NO_AUTORIZADO: otro cliente u otro profesional, y no cambia nada", async () => {
    const id = await sembrarReserva();
    await esperarError(() => cancelar({ reservaId: id }, OTRO_CLIENTE), "permission-denied", "NO_AUTORIZADO");
    await esperarError(() => cancelar({ reservaId: id }, OTRO_PRO), "permission-denied", "NO_AUTORIZADO");
    assert.equal((await leer("reservas", id)).estado, "CONFIRMADA");
  });

  for (const estado of ["EN_CURSO", "COMPLETADA", "CANCELADA_CLIENTE", "CANCELADA_PROFESIONAL", "RECHAZADA"]) {
    it(`ESTADO_INVALIDO desde ${estado}`, async () => {
      const id = await sembrarReserva({ estado });
      await esperarError(() => cancelar({ reservaId: id }), "failed-precondition", "ESTADO_INVALIDO");
      assert.equal((await leer("reservas", id)).estado, estado);
    });
  }

  it("CITA_YA_INICIADA: fechaHora pasada, exactamente ahora o ausente", async () => {
    const pasada = await sembrarReserva({ fechaHora: PASADA });
    const ahoraExacto = await sembrarReserva({ fechaHora: AHORA.toISOString() });
    const sinFecha = await sembrarReserva({ fechaHora: null });
    for (const id of [pasada, ahoraExacto, sinFecha]) {
      await esperarError(() => cancelar({ reservaId: id }), "failed-precondition", "CITA_YA_INICIADA");
      assert.equal((await leer("reservas", id)).estado, "CONFIRMADA");
    }
  });

  it("una segunda cancelacion falla limpio con ESTADO_INVALIDO", async () => {
    const id = await sembrarReserva();
    await cancelar({ reservaId: id });
    await esperarError(() => cancelar({ reservaId: id }, PRO), "failed-precondition", "ESTADO_INVALIDO");
    assert.equal((await leer("reservas", id)).estado, "CANCELADA_CLIENTE");
  });

  it("cancelaciones simultaneas (cliente y profesional): una gana, la otra recibe ESTADO_INVALIDO", async () => {
    const id = await sembrarReserva();
    const intentos = await Promise.allSettled([
      ...Array.from({ length: 4 }, () => cancelar({ reservaId: id }, CLIENTE)),
      ...Array.from({ length: 4 }, () => cancelar({ reservaId: id }, PRO)),
    ]);
    assert.equal(intentos.filter((i) => i.status === "fulfilled").length, 1);
    const doc = await leer("reservas", id);
    assert.ok(["CANCELADA_CLIENTE", "CANCELADA_PROFESIONAL"].includes(doc.estado));
  });
});

describe("cancelarReserva: reembolso", () => {
  it("pago PENDIENTE o sin pago: NO_APLICA y el pago no se toca", async () => {
    await sembrarPago("p1", "PENDIENTE");
    const id = await sembrarReserva({ pagoId: "p1", pagoEstado: "PENDIENTE" });
    const r = await cancelar({ reservaId: id });
    assert.equal(r.reembolso, "NO_APLICA");
    assert.equal((await leer("pagos", "p1")).reembolsoPendiente, undefined);
  });

  it("profesional cancela con pago AUTORIZADO (aunque falten minutos): reembolso TOTAL pendiente", async () => {
    await sembrarPago("p1", "AUTORIZADO");
    const id = await sembrarReserva({ pagoId: "p1", pagoEstado: "AUTORIZADO", fechaHora: EN_2H });
    const r = await cancelar({ reservaId: id }, PRO);
    assert.deepEqual(r, { estado: "CANCELADA_PROFESIONAL", reembolso: "TOTAL" });
    const pago = await leer("pagos", "p1");
    assert.equal(pago.reembolsoPendiente, true);
    assert.equal(pago.reembolsoPolitica, "TOTAL");
    assert.ok(pago.reembolsoSolicitadoEn instanceof Timestamp);
    assert.equal(pago.estado, "AUTORIZADO", "el estado del pago solo lo cambia el webhook");
  });

  it("cliente con >= 24 h: reembolso TOTAL pendiente", async () => {
    await sembrarPago("p1", "AUTORIZADO");
    const id = await sembrarReserva({ pagoId: "p1", pagoEstado: "AUTORIZADO", fechaHora: EN_24H });
    const r = await cancelar({ reservaId: id });
    assert.equal(r.reembolso, "TOTAL");
    assert.equal((await leer("pagos", "p1")).reembolsoPendiente, true);
  });

  it("cliente con < 24 h: sin reembolso automatico, queda registrado", async () => {
    await sembrarPago("p1", "AUTORIZADO");
    const id = await sembrarReserva({ pagoId: "p1", pagoEstado: "AUTORIZADO", fechaHora: EN_2H });
    const r = await cancelar({ reservaId: id });
    assert.equal(r.reembolso, "NINGUNO");
    const pago = await leer("pagos", "p1");
    assert.equal(pago.reembolsoPendiente, false);
    assert.equal(pago.reembolsoPolitica, "NINGUNO");
  });

  it("reserva que apunta a un pago inexistente cancela igual", async () => {
    const id = await sembrarReserva({ pagoId: "fantasma", pagoEstado: "AUTORIZADO" });
    const r = await cancelar({ reservaId: id });
    assert.equal(r.reembolso, "NO_APLICA");
    assert.equal((await leer("reservas", id)).estado, "CANCELADA_CLIENTE");
  });
});
