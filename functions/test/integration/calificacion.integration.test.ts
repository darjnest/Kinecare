// Integracion de recalcularCalificacion contra el Firestore Emulator (Admin SDK real).
// Ejecutar con: npm run test:emulator  (levanta el emulador y corre npm test).
import assert from "node:assert/strict";
import { before, beforeEach, describe, it } from "node:test";
import { initializeApp } from "firebase-admin/app";
import { Timestamp, getFirestore, type Firestore } from "firebase-admin/firestore";
import { calificacionTriggerHandler, recalcularCalificacion } from "../../src/calificacion.js";

const PROJECT_ID = "demo-kinecare";
const emulador = process.env.FIRESTORE_EMULATOR_HOST;
if (emulador === undefined) {
  throw new Error(
    "FIRESTORE_EMULATOR_HOST no esta definido: corre `npm run test:emulator` (o `npm run test:unit` para omitir estos tests).",
  );
}

const PRO = "pro1";
const OTRO_PRO = "pro2";

let db: Firestore;

async function limpiarEmulador() {
  const r = await fetch(`http://${emulador}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`, {
    method: "DELETE",
  });
  assert.equal(r.status, 200);
}

/** Perfil tal como lo deja el registro: reputacion en cero. */
async function sembrarPerfil(id: string, extra: Record<string, unknown> = {}) {
  await db.collection("profesionales").doc(id).set({ calificacionPromedio: 0, totalResenas: 0, ...extra });
}

async function sembrarResena(id: string, profesionalId: string, calificacion: number, extra: Record<string, unknown> = {}) {
  const doc = { reservaId: id, clienteId: `cli-${id}`, profesionalId, calificacion, fecha: Timestamp.now(), ...extra };
  await db.collection("resenas").doc(id).set(doc);
  return doc;
}

async function reputacion(id: string) {
  const d = (await db.collection("profesionales").doc(id).get()).data() ?? {};
  return { calificacionPromedio: d.calificacionPromedio, totalResenas: d.totalResenas };
}

before(() => {
  initializeApp({ projectId: PROJECT_ID });
  db = getFirestore();
});
beforeEach(limpiarEmulador);

describe("recalcularCalificacion (emulador)", () => {
  it("sin resenas deja el perfil en 0/0", async () => {
    await sembrarPerfil(PRO);
    const r = await recalcularCalificacion(db, PRO);
    assert.deepEqual(r, { calificacionPromedio: 0, totalResenas: 0 });
    assert.deepEqual(await reputacion(PRO), { calificacionPromedio: 0, totalResenas: 0 });
  });

  it("promedia y cuenta solo las resenas de ese profesional", async () => {
    await sembrarPerfil(PRO);
    await sembrarPerfil(OTRO_PRO);
    await sembrarResena("r1", PRO, 5);
    await sembrarResena("r2", PRO, 4);
    await sembrarResena("r3", PRO, 4);
    await sembrarResena("r4", OTRO_PRO, 1);

    await recalcularCalificacion(db, PRO);

    assert.deepEqual(await reputacion(PRO), { calificacionPromedio: 4.33, totalResenas: 3 });
    // El otro perfil no se toca hasta que le toque a el.
    assert.deepEqual(await reputacion(OTRO_PRO), { calificacionPromedio: 0, totalResenas: 0 });
  });

  it("corrige valores sembrados a mano (el promedio sale de las resenas, no del valor guardado)", async () => {
    await sembrarPerfil(PRO, { calificacionPromedio: 4.9, totalResenas: 120 });
    await sembrarResena("r1", PRO, 3);

    await recalcularCalificacion(db, PRO);

    assert.deepEqual(await reputacion(PRO), { calificacionPromedio: 3, totalResenas: 1 });
  });

  it("es idempotente: repetir el evento da el mismo resultado", async () => {
    await sembrarPerfil(PRO);
    await sembrarResena("r1", PRO, 5);
    await sembrarResena("r2", PRO, 2);

    const a = await recalcularCalificacion(db, PRO);
    const b = await recalcularCalificacion(db, PRO);

    assert.deepEqual(a, b);
    assert.deepEqual(await reputacion(PRO), { calificacionPromedio: 3.5, totalResenas: 2 });
  });

  it("un perfil inexistente devuelve null y no crea el documento", async () => {
    await sembrarResena("r1", "fantasma", 5);

    const r = await recalcularCalificacion(db, "fantasma");

    assert.equal(r, null);
    assert.equal((await db.collection("profesionales").doc("fantasma").get()).exists, false);
  });

  it("no pisa otros campos del perfil", async () => {
    await sembrarPerfil(PRO, { descripcion: "Kinesiologa deportiva", tiposAtencion: ["KINESIOLOGIA"] });
    await sembrarResena("r1", PRO, 5);

    await recalcularCalificacion(db, PRO);

    const d = (await db.collection("profesionales").doc(PRO).get()).data();
    assert.equal(d?.descripcion, "Kinesiologa deportiva");
    assert.deepEqual(d?.tiposAtencion, ["KINESIOLOGIA"]);
  });

  it("varias resenas simultaneas convergen al valor correcto", async () => {
    await sembrarPerfil(PRO);
    const calificaciones = [5, 4, 3, 2, 1, 5, 4, 4];
    await Promise.all(calificaciones.map((c, i) => sembrarResena(`r${i}`, PRO, c)));

    // Un evento por resena, todos a la vez, como llegarian del trigger.
    await Promise.all(calificaciones.map((_, i) => calificacionTriggerHandler(db, undefined, { profesionalId: PRO, calificacion: calificaciones[i] })));

    const esperado = Math.round((calificaciones.reduce((a, b) => a + b, 0) / calificaciones.length) * 100) / 100;
    assert.deepEqual(await reputacion(PRO), { calificacionPromedio: esperado, totalResenas: calificaciones.length });
  });
});

describe("calificacionTriggerHandler (emulador)", () => {
  it("crear una resena actualiza al profesional", async () => {
    await sembrarPerfil(PRO);
    const doc = await sembrarResena("r1", PRO, 4);

    const ids = await calificacionTriggerHandler(db, undefined, doc);

    assert.deepEqual(ids, [PRO]);
    assert.deepEqual(await reputacion(PRO), { calificacionPromedio: 4, totalResenas: 1 });
  });

  it("borrar una resena (soporte, desde la consola) baja el conteo", async () => {
    await sembrarPerfil(PRO);
    const doc1 = await sembrarResena("r1", PRO, 5);
    await sembrarResena("r2", PRO, 1);
    await recalcularCalificacion(db, PRO);
    assert.deepEqual(await reputacion(PRO), { calificacionPromedio: 3, totalResenas: 2 });

    await db.collection("resenas").doc("r2").delete();
    await calificacionTriggerHandler(db, { profesionalId: PRO, calificacion: 1 }, undefined);

    assert.deepEqual(await reputacion(PRO), { calificacionPromedio: doc1.calificacion, totalResenas: 1 });
  });

  it("responder una resena no recalcula ni escribe el perfil", async () => {
    await sembrarPerfil(PRO);
    const antes = await sembrarResena("r1", PRO, 4);
    await recalcularCalificacion(db, PRO);
    // Marca el perfil a mano: si el trigger recalculara, lo pisaria con el valor real.
    await db.collection("profesionales").doc(PRO).update({ totalResenas: 99 });

    const ids = await calificacionTriggerHandler(db, antes, { ...antes, respuestaProfesional: "Gracias!" });

    assert.deepEqual(ids, []);
    assert.equal((await reputacion(PRO)).totalResenas, 99);
  });

  it("un documento ilegible no recalcula nada", async () => {
    await sembrarPerfil(PRO);
    assert.deepEqual(await calificacionTriggerHandler(db, undefined, { calificacion: "mala" }), []);
  });
});
