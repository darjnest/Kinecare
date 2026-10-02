// Integracion de responderReservaHandler contra el Firestore Emulator (Admin SDK real).
// Ejecutar con: npm run test:emulator  (levanta el emulador y corre npm test).
import assert from "node:assert/strict";
import { before, beforeEach, describe, it } from "node:test";
import { initializeApp } from "firebase-admin/app";
import { Timestamp, getFirestore, type Firestore } from "firebase-admin/firestore";
import { responderReservaHandler } from "../../src/responderReserva.js";
import { esperarError } from "../helpers.js";

const PROJECT_ID = "demo-kinecare";
const emulador = process.env.FIRESTORE_EMULATOR_HOST;
if (emulador === undefined) {
  throw new Error(
    "FIRESTORE_EMULATOR_HOST no esta definido: corre `npm run test:emulator` (o `npm run test:unit` para omitir estos tests).",
  );
}

const AHORA = new Date("2026-10-01T12:00:00Z");
const FUTURA = "2026-10-05T13:00:00Z";
const PASADA = "2026-09-30T13:00:00Z";

const CLIENTE = "cliente1";
const PRO = "pro1";
const OTRO_PRO = "pro2";

let db: Firestore;

async function limpiarEmulador() {
  const r = await fetch(`http://${emulador}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`, {
    method: "DELETE",
  });
  assert.equal(r.status, 200);
}

async function sembrarUsuarios() {
  await db.collection("usuarios").doc(CLIENTE).set({ rol: "CLIENTE", nombre: "Cata Cliente" });
  await db.collection("usuarios").doc(PRO).set({ rol: "PROFESIONAL", nombre: "Pro Uno" });
  await db.collection("usuarios").doc(OTRO_PRO).set({ rol: "PROFESIONAL", nombre: "Pro Dos" });
}

/** Reserva tal como la deja crearReserva (estado SOLICITADA salvo que se indique otro). */
async function sembrarReserva(opciones: { estado?: string; fechaHora?: string | null; profesionalId?: string } = {}) {
  const doc: Record<string, unknown> = {
    clienteId: CLIENTE,
    profesionalId: opciones.profesionalId ?? PRO,
    servicioId: "srv1",
    modalidad: "CONSULTA",
    duracionMinutos: 60,
    direccion: null,
    estado: opciones.estado ?? "SOLICITADA",
    pago: { id: null, monto: 25000, estado: "PENDIENTE" },
    comisionPorcentaje: 0.1,
    creadoEn: Timestamp.fromDate(new Date("2026-09-29T12:00:00Z")),
    actualizadoEn: Timestamp.fromDate(new Date("2026-09-29T12:00:00Z")),
  };
  if (opciones.fechaHora !== null) doc.fechaHora = Timestamp.fromDate(new Date(opciones.fechaHora ?? FUTURA));
  const ref = await db.collection("reservas").add(doc);
  return ref.id;
}

function responder(data: unknown, uid: string | undefined = PRO, ahora = AHORA) {
  return responderReservaHandler(db, uid, data, ahora);
}

async function leer(reservaId: string) {
  return (await db.collection("reservas").doc(reservaId).get()).data()!;
}

before(() => {
  initializeApp({ projectId: PROJECT_ID });
  db = getFirestore();
});

beforeEach(async () => {
  await limpiarEmulador();
  await sembrarUsuarios();
});

describe("responderReserva: camino feliz", () => {
  it("ACEPTAR deja la reserva CONFIRMADA y marca respondidaEn", async () => {
    const id = await sembrarReserva();
    const resultado = await responder({ reservaId: id, respuesta: "ACEPTAR" });
    assert.deepEqual(resultado, { estado: "CONFIRMADA" });

    const d = await leer(id);
    assert.equal(d.estado, "CONFIRMADA");
    assert.ok(d.respondidaEn instanceof Timestamp);
    assert.ok(d.actualizadoEn instanceof Timestamp);
    assert.notEqual(d.actualizadoEn.toMillis(), d.creadoEn.toMillis());
    // No toca el resto del documento.
    assert.deepEqual(d.pago, { id: null, monto: 25000, estado: "PENDIENTE" });
    assert.equal(d.clienteId, CLIENTE);
    assert.equal(d.comisionPorcentaje, 0.1);
  });

  it("RECHAZAR deja la reserva RECHAZADA", async () => {
    const id = await sembrarReserva();
    assert.deepEqual(await responder({ reservaId: id, respuesta: "RECHAZAR" }), { estado: "RECHAZADA" });
    assert.equal((await leer(id)).estado, "RECHAZADA");
  });

  it("RECHAZAR una solicitud vencida se permite (limpieza)", async () => {
    const id = await sembrarReserva({ fechaHora: PASADA });
    assert.deepEqual(await responder({ reservaId: id, respuesta: "RECHAZAR" }), { estado: "RECHAZADA" });
  });
});

describe("responderReserva: errores", () => {
  it("SIN_SESION sin uid", async () => {
    const id = await sembrarReserva();
    // Directo al handler: `responder(data, undefined)` tomaria el uid por defecto.
    await esperarError(
      () => responderReservaHandler(db, undefined, { reservaId: id, respuesta: "ACEPTAR" }, AHORA),
      "unauthenticated",
      "SIN_SESION",
    );
    await esperarError(() => responder({ reservaId: id, respuesta: "ACEPTAR" }, ""), "unauthenticated", "SIN_SESION");
  });

  it("DATOS_INVALIDOS con payload mal formado", async () => {
    await esperarError(() => responder({ reservaId: "r1", respuesta: "CONFIRMAR" }), "invalid-argument", "DATOS_INVALIDOS");
  });

  it("ROL_INVALIDO si quien responde es cliente o no tiene usuario", async () => {
    const id = await sembrarReserva();
    await esperarError(() => responder({ reservaId: id, respuesta: "ACEPTAR" }, CLIENTE), "permission-denied", "ROL_INVALIDO");
    await esperarError(() => responder({ reservaId: id, respuesta: "ACEPTAR" }, "fantasma"), "permission-denied", "ROL_INVALIDO");
    assert.equal((await leer(id)).estado, "SOLICITADA");
  });

  it("RESERVA_NO_ENCONTRADA si no existe", async () => {
    await esperarError(() => responder({ reservaId: "noExiste", respuesta: "ACEPTAR" }), "not-found", "RESERVA_NO_ENCONTRADA");
  });

  it("RESERVA_NO_ENCONTRADA si es de otro profesional (no revela que existe)", async () => {
    const id = await sembrarReserva({ profesionalId: OTRO_PRO });
    await esperarError(() => responder({ reservaId: id, respuesta: "RECHAZAR" }), "not-found", "RESERVA_NO_ENCONTRADA");
    assert.equal((await leer(id)).estado, "SOLICITADA");
  });

  for (const estado of ["CONFIRMADA", "RECHAZADA", "EN_CURSO", "COMPLETADA", "CANCELADA_CLIENTE", "CANCELADA_PROFESIONAL"]) {
    it(`RESERVA_YA_RESPONDIDA si esta en ${estado}`, async () => {
      const id = await sembrarReserva({ estado });
      await esperarError(() => responder({ reservaId: id, respuesta: "ACEPTAR" }), "failed-precondition", "RESERVA_YA_RESPONDIDA");
      await esperarError(() => responder({ reservaId: id, respuesta: "RECHAZAR" }), "failed-precondition", "RESERVA_YA_RESPONDIDA");
      assert.equal((await leer(id)).estado, estado);
    });
  }

  it("RESERVA_VENCIDA al aceptar una cita que ya empezo", async () => {
    const id = await sembrarReserva({ fechaHora: PASADA });
    await esperarError(() => responder({ reservaId: id, respuesta: "ACEPTAR" }), "failed-precondition", "RESERVA_VENCIDA");
    // Justo a la hora de inicio tampoco.
    const enPunto = await sembrarReserva({ fechaHora: AHORA.toISOString() });
    await esperarError(() => responder({ reservaId: enPunto, respuesta: "ACEPTAR" }), "failed-precondition", "RESERVA_VENCIDA");
    assert.equal((await leer(id)).estado, "SOLICITADA");
  });

  it("RESERVA_VENCIDA al aceptar una reserva sin fechaHora (dato corrupto)", async () => {
    const id = await sembrarReserva({ fechaHora: null });
    await esperarError(() => responder({ reservaId: id, respuesta: "ACEPTAR" }), "failed-precondition", "RESERVA_VENCIDA");
  });
});

describe("responderReserva: concurrencia", () => {
  it("respuestas simultaneas: exactamente una gana y las demas ven RESERVA_YA_RESPONDIDA", async () => {
    const id = await sembrarReserva();
    const respuestas = ["ACEPTAR", "RECHAZAR", "ACEPTAR", "RECHAZAR", "ACEPTAR", "RECHAZAR"];
    const resultados = await Promise.allSettled(respuestas.map((r) => responder({ reservaId: id, respuesta: r })));

    const ganadoras = resultados.filter((r) => r.status === "fulfilled");
    assert.equal(ganadoras.length, 1);
    for (const r of resultados) {
      if (r.status === "rejected") assert.deepEqual(r.reason.details, { motivo: "RESERVA_YA_RESPONDIDA" });
    }
    const final = (await leer(id)).estado;
    assert.equal(final, (ganadoras[0] as PromiseFulfilledResult<{ estado: string }>).value.estado);
  });
});
