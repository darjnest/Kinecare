// Integracion de obtenerCuposOcupadosHandler contra el Firestore Emulator.
import assert from "node:assert/strict";
import { before, beforeEach, describe, it } from "node:test";
import { initializeApp } from "firebase-admin/app";
import { Timestamp, getFirestore, type Firestore } from "firebase-admin/firestore";
import { obtenerCuposOcupadosHandler } from "../../src/cupos.js";
import { esperarError } from "../helpers.js";

const PROJECT_ID = "demo-kinecare";
const emulador = process.env.FIRESTORE_EMULATOR_HOST;
if (emulador === undefined) {
  throw new Error(
    "FIRESTORE_EMULATOR_HOST no esta definido: corre `npm run test:emulator` (o `npm run test:unit` para omitir estos tests).",
  );
}

const CLIENTE = "cliente1";
const PRO = "pro1";
const VENTANA = { profesionalId: PRO, desde: "2026-10-05T00:00:00Z", hasta: "2026-10-08T00:00:00Z" };

let db: Firestore;

async function limpiarEmulador() {
  const r = await fetch(`http://${emulador}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`, {
    method: "DELETE",
  });
  assert.equal(r.status, 200);
}

async function sembrar(estado: string, inicio: string, duracionMinutos: number | undefined = 60, profesionalId = PRO) {
  const doc: Record<string, unknown> = {
    clienteId: "otro-cliente-secreto",
    profesionalId,
    servicioId: "srv1",
    estado,
    fechaHora: Timestamp.fromDate(new Date(inicio)),
    direccion: { calle: "Secreta", numero: "123", comuna: "X", ciudad: "Y" },
    pago: { id: null, monto: 1000, estado: "PENDIENTE" },
  };
  if (duracionMinutos !== undefined) doc.duracionMinutos = duracionMinutos;
  await db.collection("reservas").add(doc);
}

/** `uid` null simula una llamada sin sesion (undefined activaria el valor por defecto). */
const consultar = (data: unknown, uid: string | null = CLIENTE) => obtenerCuposOcupadosHandler(db, uid ?? undefined, data);

before(() => {
  initializeApp({ projectId: PROJECT_ID });
  db = getFirestore();
});

beforeEach(async () => {
  await limpiarEmulador();
  await db.collection("usuarios").doc(CLIENTE).set({ rol: "CLIENTE" });
  await db.collection("usuarios").doc(PRO).set({ rol: "PROFESIONAL" });
});

describe("obtenerCuposOcupados", () => {
  it("devuelve solo inicio/fin de las reservas activas del profesional, ordenadas, sin datos personales", async () => {
    await sembrar("CONFIRMADA", "2026-10-06T15:00:00Z", 45);
    await sembrar("SOLICITADA", "2026-10-05T13:00:00Z");
    await sembrar("EN_CURSO", "2026-10-07T10:00:00Z", 30);
    const r = await consultar(VENTANA);
    assert.deepEqual(r, {
      cupos: [
        { inicio: "2026-10-05T13:00:00.000Z", fin: "2026-10-05T14:00:00.000Z" },
        { inicio: "2026-10-06T15:00:00.000Z", fin: "2026-10-06T15:45:00.000Z" },
        { inicio: "2026-10-07T10:00:00.000Z", fin: "2026-10-07T10:30:00.000Z" },
      ],
    });
    for (const cupo of r.cupos) assert.deepEqual(Object.keys(cupo).sort(), ["fin", "inicio"]);
  });

  it("ignora reservas que no ocupan agenda y las de otros profesionales", async () => {
    await sembrar("CANCELADA_CLIENTE", "2026-10-06T15:00:00Z");
    await sembrar("CANCELADA_PROFESIONAL", "2026-10-06T16:00:00Z");
    await sembrar("RECHAZADA", "2026-10-06T17:00:00Z");
    await sembrar("COMPLETADA", "2026-10-06T18:00:00Z");
    await sembrar("CONFIRMADA", "2026-10-06T19:00:00Z", 60, "pro-otro");
    assert.deepEqual(await consultar(VENTANA), { cupos: [] });
  });

  it("incluye una reserva que empieza antes de la ventana pero la cruza, y excluye la que termina justo en 'desde'", async () => {
    await sembrar("CONFIRMADA", "2026-10-04T23:30:00Z", 60); // 23:30-00:30: cruza
    await sembrar("CONFIRMADA", "2026-10-04T23:00:00Z", 60); // 23:00-00:00: termina en desde
    const r = await consultar(VENTANA);
    assert.deepEqual(r.cupos, [{ inicio: "2026-10-04T23:30:00.000Z", fin: "2026-10-05T00:30:00.000Z" }]);
  });

  it("una reserva que empieza exactamente en 'hasta' queda fuera", async () => {
    await sembrar("CONFIRMADA", "2026-10-08T00:00:00Z");
    assert.deepEqual(await consultar(VENTANA), { cupos: [] });
  });

  it("sin duracionMinutos asume 60 minutos", async () => {
    await sembrar("CONFIRMADA", "2026-10-06T15:00:00Z", undefined);
    assert.deepEqual((await consultar(VENTANA)).cupos, [
      { inicio: "2026-10-06T15:00:00.000Z", fin: "2026-10-06T16:00:00.000Z" },
    ]);
  });

  it("un profesional inexistente devuelve lista vacia", async () => {
    assert.deepEqual(await consultar({ ...VENTANA, profesionalId: "nadie" }), { cupos: [] });
  });

  it("SIN_SESION", async () => {
    await esperarError(() => consultar(VENTANA, null), "unauthenticated", "SIN_SESION");
  });

  it("ROL_INVALIDO: un profesional, o un usuario sin documento, no consulta", async () => {
    await esperarError(() => consultar(VENTANA, PRO), "permission-denied", "ROL_INVALIDO");
    await esperarError(() => consultar(VENTANA, "fantasma"), "permission-denied", "ROL_INVALIDO");
  });

  it("DATOS_INVALIDOS: ventana > 15 dias, invertida o mal formada", async () => {
    await esperarError(
      () => consultar({ ...VENTANA, hasta: "2026-10-21T00:00:00Z" }),
      "invalid-argument",
      "DATOS_INVALIDOS",
    );
    await esperarError(() => consultar({ ...VENTANA, hasta: VENTANA.desde }), "invalid-argument", "DATOS_INVALIDOS");
    await esperarError(() => consultar({ profesionalId: PRO }), "invalid-argument", "DATOS_INVALIDOS");
  });
});
