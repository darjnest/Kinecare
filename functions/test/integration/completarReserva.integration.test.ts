// Integracion de completarReservaHandler contra el Firestore Emulator (Admin SDK real).
// Ejecutar con: npm run test:emulator  (levanta el emulador y corre npm test).
import assert from "node:assert/strict";
import { before, beforeEach, describe, it } from "node:test";
import { initializeApp } from "firebase-admin/app";
import { Timestamp, getFirestore, type Firestore } from "firebase-admin/firestore";
import { completarReservaHandler } from "../../src/completarReserva.js";
import { esperarError } from "../helpers.js";

const PROJECT_ID = "demo-kinecare";
const emulador = process.env.FIRESTORE_EMULATOR_HOST;
if (emulador === undefined) {
  throw new Error(
    "FIRESTORE_EMULATOR_HOST no esta definido: corre `npm run test:emulator` (o `npm run test:unit` para omitir estos tests).",
  );
}

const AHORA = new Date("2026-10-01T12:00:00Z");
const PASADA = "2026-10-01T11:00:00Z";
const FUTURA = "2026-10-05T13:00:00Z";

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

async function sembrarReserva(opciones: { estado?: string; fechaHora?: string | null; profesionalId?: string } = {}) {
  const doc: Record<string, unknown> = {
    clienteId: CLIENTE,
    profesionalId: opciones.profesionalId ?? PRO,
    servicioId: "srv1",
    modalidad: "CONSULTA",
    duracionMinutos: 60,
    direccion: null,
    estado: opciones.estado ?? "CONFIRMADA",
    pago: { id: "p1", monto: 25000, estado: "AUTORIZADO" },
    comisionPorcentaje: 0.1,
    creadoEn: Timestamp.fromDate(new Date("2026-09-29T12:00:00Z")),
    actualizadoEn: Timestamp.fromDate(new Date("2026-09-29T12:00:00Z")),
  };
  if (opciones.fechaHora !== null) doc.fechaHora = Timestamp.fromDate(new Date(opciones.fechaHora ?? PASADA));
  const ref = await db.collection("reservas").add(doc);
  return ref.id;
}

function completar(data: unknown, uid: string | undefined = PRO, ahora = AHORA) {
  return completarReservaHandler(db, uid, data, ahora);
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

describe("completarReserva: camino feliz", () => {
  it("una CONFIRMADA ya iniciada pasa a COMPLETADA y marca completadaEn", async () => {
    const id = await sembrarReserva();
    assert.deepEqual(await completar({ reservaId: id }), { estado: "COMPLETADA" });

    const d = await leer(id);
    assert.equal(d.estado, "COMPLETADA");
    assert.ok(d.completadaEn instanceof Timestamp);
    assert.notEqual(d.actualizadoEn.toMillis(), d.creadoEn.toMillis());
    // No toca pago, comision ni cliente.
    assert.deepEqual(d.pago, { id: "p1", monto: 25000, estado: "AUTORIZADO" });
    assert.equal(d.comisionPorcentaje, 0.1);
    assert.equal(d.clienteId, CLIENTE);
  });

  it("una EN_CURSO tambien se puede completar", async () => {
    const id = await sembrarReserva({ estado: "EN_CURSO" });
    assert.deepEqual(await completar({ reservaId: id }), { estado: "COMPLETADA" });
  });
});

describe("completarReserva: errores", () => {
  it("SIN_SESION sin uid", async () => {
    const id = await sembrarReserva();
    await esperarError(() => completarReservaHandler(db, undefined, { reservaId: id }, AHORA), "unauthenticated", "SIN_SESION");
    await esperarError(() => completar({ reservaId: id }, ""), "unauthenticated", "SIN_SESION");
  });

  it("DATOS_INVALIDOS con payload mal formado", async () => {
    await esperarError(() => completar({}), "invalid-argument", "DATOS_INVALIDOS");
  });

  it("ROL_INVALIDO si quien completa es cliente o no tiene usuario", async () => {
    const id = await sembrarReserva();
    await esperarError(() => completar({ reservaId: id }, CLIENTE), "permission-denied", "ROL_INVALIDO");
    await esperarError(() => completar({ reservaId: id }, "fantasma"), "permission-denied", "ROL_INVALIDO");
    assert.equal((await leer(id)).estado, "CONFIRMADA");
  });

  it("RESERVA_NO_ENCONTRADA si no existe o es de otro profesional", async () => {
    await esperarError(() => completar({ reservaId: "noExiste" }), "not-found", "RESERVA_NO_ENCONTRADA");
    const ajena = await sembrarReserva({ profesionalId: OTRO_PRO });
    await esperarError(() => completar({ reservaId: ajena }), "not-found", "RESERVA_NO_ENCONTRADA");
    assert.equal((await leer(ajena)).estado, "CONFIRMADA");
  });

  it("RESERVA_NO_COMPLETABLE si no esta confirmada (solicitada, rechazada, cancelada, ya completada)", async () => {
    for (const estado of ["SOLICITADA", "RECHAZADA", "CANCELADA_CLIENTE", "CANCELADA_PROFESIONAL", "COMPLETADA"]) {
      const id = await sembrarReserva({ estado });
      await esperarError(() => completar({ reservaId: id }), "failed-precondition", "RESERVA_NO_COMPLETABLE");
      assert.equal((await leer(id)).estado, estado);
    }
  });

  it("CITA_NO_INICIADA si la hora todavia no llega o no hay fechaHora", async () => {
    const futura = await sembrarReserva({ fechaHora: FUTURA });
    await esperarError(() => completar({ reservaId: futura }), "failed-precondition", "CITA_NO_INICIADA");
    const sinFecha = await sembrarReserva({ fechaHora: null });
    await esperarError(() => completar({ reservaId: sinFecha }), "failed-precondition", "CITA_NO_INICIADA");
    assert.equal((await leer(futura)).estado, "CONFIRMADA");
  });

  it("dos completar simultaneos: uno gana y el otro falla sin pisar el estado", async () => {
    const id = await sembrarReserva();
    const resultados = await Promise.allSettled([completar({ reservaId: id }), completar({ reservaId: id })]);
    assert.equal(resultados.filter((r) => r.status === "fulfilled").length, 1);
    assert.equal(resultados.filter((r) => r.status === "rejected").length, 1);
    assert.equal((await leer(id)).estado, "COMPLETADA");
  });
});
