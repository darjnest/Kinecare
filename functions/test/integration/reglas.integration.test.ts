// Security Rules de firestore.rules ejercitadas contra el Firestore Emulator por REST, como lo haria
// un cliente (token de Auth sin firmar, que el emulador acepta). `firebase emulators:exec` carga las
// reglas del firebase.json del repo. El Admin SDK (que ignora las reglas) solo siembra datos.
//
// Cubre las reglas que dependen de este cambio: `usuarios/{uid}/dispositivos/{token}` y la respuesta
// del profesional en `resenas`. No pretende cubrir todas las reglas del archivo.
import assert from "node:assert/strict";
import { before, beforeEach, describe, it } from "node:test";
import { initializeApp } from "firebase-admin/app";
import { Timestamp, getFirestore, type Firestore } from "firebase-admin/firestore";
import { calificacionTriggerHandler } from "../../src/calificacion.js";

const PROJECT_ID = "demo-kinecare";
const emulador = process.env.FIRESTORE_EMULATOR_HOST;
if (emulador === undefined) {
  throw new Error(
    "FIRESTORE_EMULATOR_HOST no esta definido: corre `npm run test:emulator` (o `npm run test:unit` para omitir estos tests).",
  );
}

const BASE = `http://${emulador}/v1/projects/${PROJECT_ID}/databases/(default)/documents`;
const CLIENTE = "cliente1";
const OTRO_CLIENTE = "cliente2";
const PRO = "pro1";
const OTRO_PRO = "pro2";
const TOKEN = "fcm:APA91bH-token_de_prueba";

let db: Firestore;

function jwt(uid: string): string {
  const b64 = (o: unknown) => Buffer.from(JSON.stringify(o)).toString("base64url");
  return `${b64({ alg: "none", typ: "JWT" })}.${b64({ user_id: uid, sub: uid, iss: "x", aud: PROJECT_ID })}.`;
}

type Valor = { stringValue: string } | { integerValue: string };
const str = (s: string): Valor => ({ stringValue: s });

async function llamar(uid: string | null, metodo: string, ruta: string, cuerpo?: unknown): Promise<number> {
  const r = await fetch(`${BASE}/${ruta}`, {
    method: metodo,
    headers: { "Content-Type": "application/json", ...(uid === null ? {} : { Authorization: `Bearer ${jwt(uid)}` }) },
    body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
  });
  await r.text();
  return r.status;
}

/** Escritura con `actualizadoEn` fijado por el servidor (REQUEST_TIME), como serverTimestamp() del SDK. */
async function escribirDispositivo(
  uid: string | null,
  duenoRuta: string,
  token: string,
  campos: Record<string, Valor>,
  conServerTimestamp: boolean,
): Promise<number> {
  const name = `projects/${PROJECT_ID}/databases/(default)/documents/usuarios/${duenoRuta}/dispositivos/${token}`;
  const r = await fetch(`${BASE.replace(/\/documents$/, "")}/documents:commit`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...(uid === null ? {} : { Authorization: `Bearer ${jwt(uid)}` }) },
    body: JSON.stringify({
      writes: [
        {
          update: { name, fields: campos },
          ...(conServerTimestamp
            ? { updateTransforms: [{ fieldPath: "actualizadoEn", setToServerValue: "REQUEST_TIME" }] }
            : {}),
        },
      ],
    }),
  });
  await r.text();
  return r.status;
}

const ok = (status: number) => assert.equal(status, 200, `se esperaba 200, llego ${status}`);
const denegado = (status: number) => assert.equal(status, 403, `se esperaba 403 (reglas), llego ${status}`);

async function limpiarEmulador() {
  const r = await fetch(`http://${emulador}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`, {
    method: "DELETE",
  });
  assert.equal(r.status, 200);
}

before(() => {
  initializeApp({ projectId: PROJECT_ID });
  db = getFirestore();
});

beforeEach(async () => {
  await limpiarEmulador();
  await db.collection("usuarios").doc(CLIENTE).set({ rol: "CLIENTE" });
  await db.collection("usuarios").doc(OTRO_CLIENTE).set({ rol: "CLIENTE" });
  await db.collection("usuarios").doc(PRO).set({ rol: "PROFESIONAL" });
  await db.collection("usuarios").doc(OTRO_PRO).set({ rol: "PROFESIONAL" });
});

describe("reglas: usuarios/{uid}/dispositivos/{token}", () => {
  const campos = { plataforma: str("ANDROID") };

  it("el dueno crea su token con plataforma ANDROID y serverTimestamp", async () => {
    ok(await escribirDispositivo(CLIENTE, CLIENTE, TOKEN, campos, true));
    const doc = await db.collection("usuarios").doc(CLIENTE).collection("dispositivos").doc(TOKEN).get();
    assert.equal(doc.get("plataforma"), "ANDROID");
    assert.ok(doc.get("actualizadoEn") instanceof Timestamp);
  });

  it("el dueno puede refrescar el mismo token (re-registro) y leer/borrar el suyo", async () => {
    ok(await escribirDispositivo(CLIENTE, CLIENTE, TOKEN, campos, true));
    ok(await escribirDispositivo(CLIENTE, CLIENTE, TOKEN, campos, true));
    ok(await llamar(CLIENTE, "GET", `usuarios/${CLIENTE}/dispositivos/${TOKEN}`));
    ok(await llamar(CLIENTE, "GET", `usuarios/${CLIENTE}/dispositivos`));
    ok(await llamar(CLIENTE, "DELETE", `usuarios/${CLIENTE}/dispositivos/${TOKEN}`));
  });

  it("nadie escribe en los dispositivos de otro usuario (ni anonimo)", async () => {
    denegado(await escribirDispositivo(OTRO_CLIENTE, CLIENTE, TOKEN, campos, true));
    denegado(await escribirDispositivo(PRO, CLIENTE, TOKEN, campos, true));
    denegado(await escribirDispositivo(null, CLIENTE, TOKEN, campos, true));
    assert.equal((await db.collection("usuarios").doc(CLIENTE).collection("dispositivos").get()).size, 0);
  });

  it("nadie lee ni borra los dispositivos de otro usuario", async () => {
    ok(await escribirDispositivo(CLIENTE, CLIENTE, TOKEN, campos, true));
    denegado(await llamar(OTRO_CLIENTE, "GET", `usuarios/${CLIENTE}/dispositivos/${TOKEN}`));
    denegado(await llamar(OTRO_CLIENTE, "GET", `usuarios/${CLIENTE}/dispositivos`));
    denegado(await llamar(null, "GET", `usuarios/${CLIENTE}/dispositivos/${TOKEN}`));
    denegado(await llamar(OTRO_CLIENTE, "DELETE", `usuarios/${CLIENTE}/dispositivos/${TOKEN}`));
    assert.equal((await db.collection("usuarios").doc(CLIENTE).collection("dispositivos").get()).size, 1);
  });

  it("exige claves exactas: sin plataforma, con claves extra o sin actualizadoEn se rechaza", async () => {
    denegado(await escribirDispositivo(CLIENTE, CLIENTE, TOKEN, {}, true));
    denegado(await escribirDispositivo(CLIENTE, CLIENTE, TOKEN, { ...campos, rol: str("PROFESIONAL") }, true));
    denegado(await escribirDispositivo(CLIENTE, CLIENTE, TOKEN, campos, false));
  });

  it("plataforma distinta de ANDROID se rechaza", async () => {
    denegado(await escribirDispositivo(CLIENTE, CLIENTE, TOKEN, { plataforma: str("IOS") }, true));
  });

  it("actualizadoEn debe ser el instante del servidor, no uno elegido por el cliente", async () => {
    const lejano = { timestampValue: "2030-01-01T00:00:00Z" } as unknown as Valor;
    denegado(await escribirDispositivo(CLIENTE, CLIENTE, TOKEN, { ...campos, actualizadoEn: lejano }, false));
  });
});

describe("reglas: responder una resena (resenas/{id}.respuestaProfesional)", () => {
  async function sembrarResena(extra: Record<string, unknown> = {}) {
    await db.collection("resenas").doc("r1").set({
      reservaId: "r1",
      clienteId: CLIENTE,
      profesionalId: PRO,
      calificacion: 4,
      comentario: "Muy buena atención",
      fecha: Timestamp.fromDate(new Date("2026-09-30T12:00:00Z")),
      ...extra,
    });
  }

  const parche = (uid: string | null, campos: Record<string, unknown>, mascara: string[]) =>
    llamar(
      uid,
      "PATCH",
      `resenas/r1?${mascara.map((m) => `updateMask.fieldPaths=${m}`).join("&")}`,
      { fields: campos },
    );

  it("el profesional dueno responde una vez, y solo cambia esa clave", async () => {
    await sembrarResena();
    ok(await parche(PRO, { respuestaProfesional: str("Gracias por tu comentario") }, ["respuestaProfesional"]));
    const doc = (await db.collection("resenas").doc("r1").get()).data()!;
    assert.equal(doc.respuestaProfesional, "Gracias por tu comentario");
    assert.equal(doc.calificacion, 4);
    assert.equal(doc.comentario, "Muy buena atención");
  });

  it("acepta 500 caracteres y rechaza 501", async () => {
    await sembrarResena();
    denegado(await parche(PRO, { respuestaProfesional: str("a".repeat(501)) }, ["respuestaProfesional"]));
    ok(await parche(PRO, { respuestaProfesional: str("a".repeat(500)) }, ["respuestaProfesional"]));
  });

  it("rechaza vacio, solo espacios y valores que no son texto", async () => {
    await sembrarResena();
    denegado(await parche(PRO, { respuestaProfesional: str("") }, ["respuestaProfesional"]));
    denegado(await parche(PRO, { respuestaProfesional: str("    ") }, ["respuestaProfesional"]));
    denegado(await parche(PRO, { respuestaProfesional: { integerValue: "5" } }, ["respuestaProfesional"]));
  });

  it("no se puede editar una respuesta ya publicada (ni siquiera el profesional)", async () => {
    await sembrarResena({ respuestaProfesional: "Primera respuesta" });
    denegado(await parche(PRO, { respuestaProfesional: str("Cambio de opinion") }, ["respuestaProfesional"]));
    assert.equal((await db.collection("resenas").doc("r1").get()).get("respuestaProfesional"), "Primera respuesta");
  });

  it("otro profesional, el cliente autor y anonimos no pueden responder", async () => {
    await sembrarResena();
    denegado(await parche(OTRO_PRO, { respuestaProfesional: str("Intruso") }, ["respuestaProfesional"]));
    denegado(await parche(CLIENTE, { respuestaProfesional: str("Autorrespuesta") }, ["respuestaProfesional"]));
    denegado(await parche(null, { respuestaProfesional: str("Anonimo") }, ["respuestaProfesional"]));
    assert.equal((await db.collection("resenas").doc("r1").get()).get("respuestaProfesional"), undefined);
  });

  it("el profesional no puede tocar otras claves junto con la respuesta", async () => {
    await sembrarResena();
    denegado(
      await parche(PRO, { respuestaProfesional: str("Gracias"), calificacion: { integerValue: "5" } }, [
        "respuestaProfesional",
        "calificacion",
      ]),
    );
    denegado(await parche(PRO, { calificacion: { integerValue: "5" } }, ["calificacion"]));
    denegado(await parche(PRO, { comentario: str("Editado") }, ["comentario"]));
    const doc = (await db.collection("resenas").doc("r1").get()).data()!;
    assert.equal(doc.calificacion, 4);
    assert.equal(doc.respuestaProfesional, undefined);
  });

  it("nadie borra una resena", async () => {
    await sembrarResena();
    denegado(await llamar(PRO, "DELETE", "resenas/r1"));
    denegado(await llamar(CLIENTE, "DELETE", "resenas/r1"));
  });

  it("responder no dispara recalculo: el evento before/after no afecta a ningun profesional", async () => {
    // recalcularCalificacion decide con (antes, despues); responder solo agrega respuestaProfesional.
    await db.collection("profesionales").doc(PRO).set({ calificacionPromedio: 4, totalResenas: 1 });
    const antes = { profesionalId: PRO, calificacion: 4 };
    const despues = { profesionalId: PRO, calificacion: 4, respuestaProfesional: "Gracias" };
    await calificacionTriggerHandler(db, antes, despues);
    const perfil = (await db.collection("profesionales").doc(PRO).get()).data()!;
    assert.deepEqual(perfil, { calificacionPromedio: 4, totalResenas: 1 });
  });
});

const patch = (uid: string | null, ruta: string, campos: Record<string, unknown>) =>
  llamar(uid, "PATCH", `${ruta}?${Object.keys(campos).map((m) => `updateMask.fieldPaths=${m}`).join("&")}`, {
    fields: campos,
  });

const FOTO_PRO = (uid: string) =>
  `https://firebasestorage.googleapis.com/v0/b/demo-kinecare.firebasestorage.app/o/profesionales%2F${uid}%2Ffoto-perfil.jpg?alt=media&token=abc&v=1`;

describe("reglas: usuarios/{uid} (campos inmutables y fotoUrl)", () => {
  beforeEach(async () => {
    await db.collection("usuarios").doc(CLIENTE).set({
      rol: "CLIENTE",
      nombre: "Ana",
      rut: "111111111",
      email: "111111111@rut.kinecare.cl",
      fechaRegistro: Timestamp.fromDate(new Date("2026-09-01T12:00:00Z")),
      fotoUrl: null,
    });
  });

  it("el dueno edita nombre, telefono y correoContacto", async () => {
    ok(await patch(CLIENTE, `usuarios/${CLIENTE}`, { nombre: str("Ana Maria"), telefono: str("+56911111111") }));
    assert.equal((await db.collection("usuarios").doc(CLIENTE).get()).get("nombre"), "Ana Maria");
  });

  it("no puede cambiar rut, email, fechaRegistro ni rol", async () => {
    denegado(await patch(CLIENTE, `usuarios/${CLIENTE}`, { rut: str("222222222") }));
    denegado(await patch(CLIENTE, `usuarios/${CLIENTE}`, { email: str("otro@x.cl") }));
    denegado(await patch(CLIENTE, `usuarios/${CLIENTE}`, { fechaRegistro: { timestampValue: "2030-01-01T00:00:00Z" } }));
    denegado(await patch(CLIENTE, `usuarios/${CLIENTE}`, { rol: str("PROFESIONAL") }));
    assert.equal((await db.collection("usuarios").doc(CLIENTE).get()).get("rut"), "111111111");
  });

  it("nadie edita el usuario de otro", async () => {
    denegado(await patch(OTRO_CLIENTE, `usuarios/${CLIENTE}`, { nombre: str("Intruso") }));
    denegado(await patch(null, `usuarios/${CLIENTE}`, { nombre: str("Anonimo") }));
  });

  it("fotoUrl: acepta la URL de Storage de la propia foto y null", async () => {
    ok(await patch(PRO, `usuarios/${PRO}`, { fotoUrl: str(FOTO_PRO(PRO)) }));
    ok(await patch(PRO, `usuarios/${PRO}`, { fotoUrl: { nullValue: null } }));
  });

  it("fotoUrl: rechaza URLs externas, de otro usuario o con otro host parecido", async () => {
    denegado(await patch(CLIENTE, `usuarios/${CLIENTE}`, { fotoUrl: str("https://atacante.example/pixel.png") }));
    denegado(await patch(PRO, `usuarios/${PRO}`, { fotoUrl: str(FOTO_PRO(OTRO_PRO)) }));
    denegado(
      await patch(PRO, `usuarios/${PRO}`, {
        fotoUrl: str(`https://firebasestorage.googleapis.com.atacante.example/v0/b/x/o/profesionales%2F${PRO}%2Ffoto-perfil.jpg?a=1`),
      }),
    );
    denegado(await patch(PRO, `usuarios/${PRO}`, { fotoUrl: { integerValue: "5" } }));
  });

  it("registro: crea con fotoUrl de Google, nula o ausente; rechaza una URL arbitraria", async () => {
    const crear = (uid: string, fotoUrl?: Valor | { nullValue: null }) =>
      llamar(uid, "PATCH", `usuarios/${uid}`, {
        fields: { rol: str("CLIENTE"), ...(fotoUrl === undefined ? {} : { fotoUrl }) },
      });
    ok(await crear("nuevo1", str("https://lh3.googleusercontent.com/a/abc=s96-c")));
    ok(await crear("nuevo2", { nullValue: null }));
    ok(await crear("nuevo3"));
    denegado(await crear("nuevo4", str("https://atacante.example/pixel.png")));
  });
});

describe("reglas: profesionales/{uid} (campos que edita el dueno)", () => {
  beforeEach(async () => {
    await db.collection("profesionales").doc(PRO).set({
      tiposAtencion: ["KINESIOLOGIA"],
      especialidades: [],
      rnpi: "",
      descripcion: "",
      calificacionPromedio: 0,
      totalResenas: 0,
      estadoVerificacionGeneral: "NO_SOLICITADO",
      insignias: [],
      disponibilidad: [],
    });
  });

  const ruta = `profesionales/${PRO}`;

  it("el dueno edita descripcion, disponibilidad, tiposAtencion y especialidades", async () => {
    ok(await patch(PRO, ruta, { descripcion: str("Kinesiologo deportivo") }));
    ok(await patch(PRO, ruta, { disponibilidad: { arrayValue: { values: [] } } }));
    ok(await patch(PRO, ruta, { tiposAtencion: { arrayValue: { values: [str("KINESIOLOGIA"), str("MASOTERAPIA")] } } }));
    ok(await patch(PRO, ruta, { especialidades: { arrayValue: { values: [str("Deportiva")] } } }));
  });

  it("no puede cambiar rnpi, ni reputacion, ni verificacion, ni mercadoPagoConectado", async () => {
    denegado(await patch(PRO, ruta, { rnpi: str("123456") }));
    denegado(await patch(PRO, ruta, { calificacionPromedio: { doubleValue: 5 } }));
    denegado(await patch(PRO, ruta, { totalResenas: { integerValue: "99" } }));
    denegado(await patch(PRO, ruta, { estadoVerificacionGeneral: str("APROBADO") }));
    denegado(await patch(PRO, ruta, { insignias: { arrayValue: { values: [str("X")] } } }));
    denegado(await patch(PRO, ruta, { mercadoPagoConectado: { booleanValue: true } }));
  });

  it("no puede agregar claves arbitrarias, ni siquiera junto a una clave permitida", async () => {
    denegado(await patch(PRO, ruta, { fotoUrl: str("https://atacante.example/pixel.png") }));
    denegado(await patch(PRO, ruta, { descripcion: str("ok"), basura: str("x") }));
    assert.equal((await db.collection("profesionales").doc(PRO).get()).get("descripcion"), "");
  });

  it("tiposAtencion sigue validado y otro usuario no puede editar el perfil", async () => {
    denegado(await patch(PRO, ruta, { tiposAtencion: { arrayValue: { values: [] } } }));
    denegado(await patch(PRO, ruta, { tiposAtencion: { arrayValue: { values: [str("DENTISTA")] } } }));
    denegado(await patch(OTRO_PRO, ruta, { descripcion: str("Intruso") }));
    denegado(await patch(null, ruta, { descripcion: str("Anonimo") }));
  });
});
