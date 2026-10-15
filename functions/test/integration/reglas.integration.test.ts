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
    const crear = (uid: string, rut: string, fotoUrl?: Valor | { nullValue: null }) =>
      registrar(uid, rut, { rol: str("CLIENTE"), ...(fotoUrl === undefined ? {} : { fotoUrl }) });
    ok(await crear("nuevo1", "111111111", str("https://lh3.googleusercontent.com/a/abc=s96-c")));
    ok(await crear("nuevo2", "222222222", { nullValue: null }));
    ok(await crear("nuevo3", "333333333"));
    denegado(await crear("nuevo4", "444444444", str("https://atacante.example/pixel.png")));
  });
});

/** Batch de registro: `usuarios/{uid}` + (opcional) `rutsRegistrados/{rut}` + `perfilesPublicos/{uid}`. */
async function commitComoCliente(uid: string | null, escrituras: { ruta: string; campos: Record<string, unknown> }[]): Promise<number> {
  const base = `projects/${PROJECT_ID}/databases/(default)/documents`;
  const r = await fetch(`${BASE.replace(/\/documents$/, "")}/documents:commit`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...(uid === null ? {} : { Authorization: `Bearer ${jwt(uid)}` }) },
    body: JSON.stringify({
      writes: escrituras.map((e) => ({ update: { name: `${base}/${e.ruta}`, fields: e.campos } })),
    }),
  });
  await r.text();
  return r.status;
}

/** Registro completo como lo hace la app: reclama el RUT, crea el usuario y su perfil publico. */
const registrar = (uid: string, rut: string, usuario: Record<string, unknown>, conIndiceDeRut = true) =>
  commitComoCliente(uid, [
    ...(conIndiceDeRut ? [{ ruta: `rutsRegistrados/${rut}`, campos: { uid: str(uid) } }] : []),
    { ruta: `usuarios/${uid}`, campos: { rut: str(rut), nombre: str("Nueva"), ...usuario } },
    { ruta: `perfilesPublicos/${uid}`, campos: { nombre: str("Nueva"), fotoUrl: usuario.fotoUrl ?? { nullValue: null } } },
  ]);

describe("reglas: usuarios privado, perfilesPublicos y rutsRegistrados", () => {
  beforeEach(async () => {
    await db.collection("usuarios").doc(CLIENTE).set({ rol: "CLIENTE", nombre: "Ana", rut: "111111111", telefono: "+56911111111" });
    await db.collection("perfilesPublicos").doc(CLIENTE).set({ nombre: "Ana", fotoUrl: null });
  });

  it("usuarios/{uid} solo lo lee su dueno (get y list)", async () => {
    ok(await llamar(CLIENTE, "GET", `usuarios/${CLIENTE}`));
    denegado(await llamar(OTRO_CLIENTE, "GET", `usuarios/${CLIENTE}`));
    denegado(await llamar(PRO, "GET", `usuarios/${CLIENTE}`));
    denegado(await llamar(null, "GET", `usuarios/${CLIENTE}`));
    denegado(await llamar(OTRO_CLIENTE, "GET", "usuarios"));
    // Buscar por RUT (la consulta que hacia el registro con Google antes del indice de rutsRegistrados).
    const consulta = await fetch(`${BASE}:runQuery`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${jwt(OTRO_CLIENTE)}` },
      body: JSON.stringify({
        structuredQuery: {
          from: [{ collectionId: "usuarios" }],
          where: { fieldFilter: { field: { fieldPath: "rut" }, op: "EQUAL", value: str("111111111") } },
        },
      }),
    });
    denegado(consulta.status);
  });

  it("perfilesPublicos lo lee cualquier usuario autenticado, no un anonimo", async () => {
    ok(await llamar(OTRO_CLIENTE, "GET", `perfilesPublicos/${CLIENTE}`));
    ok(await llamar(PRO, "GET", `perfilesPublicos/${CLIENTE}`));
    denegado(await llamar(null, "GET", `perfilesPublicos/${CLIENTE}`));
  });

  it("perfilesPublicos: solo el dueno escribe, solo nombre y fotoUrl validos", async () => {
    ok(await patch(CLIENTE, `perfilesPublicos/${CLIENTE}`, { nombre: str("Ana Maria") }));
    ok(await patch(CLIENTE, `perfilesPublicos/${CLIENTE}`, { fotoUrl: str("https://lh3.googleusercontent.com/a/abc") }));
    denegado(await patch(OTRO_CLIENTE, `perfilesPublicos/${CLIENTE}`, { nombre: str("Intruso") }));
    denegado(await patch(null, `perfilesPublicos/${CLIENTE}`, { nombre: str("Anonimo") }));
    denegado(await patch(CLIENTE, `perfilesPublicos/${CLIENTE}`, { telefono: str("+56900000000") }));
    denegado(await patch(CLIENTE, `perfilesPublicos/${CLIENTE}`, { rut: str("111111111") }));
    denegado(await patch(CLIENTE, `perfilesPublicos/${CLIENTE}`, { fotoUrl: str("https://atacante.example/pixel.png") }));
    denegado(await patch(CLIENTE, `perfilesPublicos/${CLIENTE}`, { nombre: str("") }));
    denegado(await patch(CLIENTE, `perfilesPublicos/${CLIENTE}`, { nombre: str("x".repeat(201)) }));
    denegado(await llamar(CLIENTE, "DELETE", `perfilesPublicos/${CLIENTE}`));
  });

  it("registro: reclama el RUT, crea el usuario y su perfil publico en un batch", async () => {
    ok(await registrar("nuevo1", "123456785", { rol: str("CLIENTE") }));
    assert.equal((await db.collection("rutsRegistrados").doc("123456785").get()).get("uid"), "nuevo1");
    assert.equal((await db.collection("perfilesPublicos").doc("nuevo1").get()).get("nombre"), "Nueva");
  });

  it("registro con un RUT ya tomado: se rechaza el batch completo y no queda nada", async () => {
    ok(await registrar("nuevo1", "123456785", { rol: str("CLIENTE") }));
    denegado(await registrar("nuevo2", "123456785", { rol: str("CLIENTE") }));
    assert.equal((await db.collection("usuarios").doc("nuevo2").get()).exists, false);
    assert.equal((await db.collection("perfilesPublicos").doc("nuevo2").get()).exists, false);
    assert.equal((await db.collection("rutsRegistrados").doc("123456785").get()).get("uid"), "nuevo1");
  });

  it("no se puede crear el usuario sin reclamar su RUT, ni reclamando el de otro uid", async () => {
    denegado(await registrar("nuevo1", "123456785", { rol: str("CLIENTE") }, false));
    // Reclama el RUT a nombre de otro: la clave `uid` no coincide con quien escribe.
    denegado(
      await commitComoCliente("nuevo1", [
        { ruta: "rutsRegistrados/123456785", campos: { uid: str("otro") } },
        { ruta: "usuarios/nuevo1", campos: { rut: str("123456785"), rol: str("CLIENTE") } },
      ]),
    );
    // Reclama un RUT y crea el usuario con otro distinto.
    denegado(
      await commitComoCliente("nuevo1", [
        { ruta: "rutsRegistrados/123456785", campos: { uid: str("nuevo1") } },
        { ruta: "usuarios/nuevo1", campos: { rut: str("987654321"), rol: str("CLIENTE") } },
      ]),
    );
    assert.equal((await db.collection("usuarios").doc("nuevo1").get()).exists, false);
  });

  it("rutsRegistrados: nadie lo lee ni lo modifica; el id y las claves se validan", async () => {
    ok(await registrar("nuevo1", "123456785", { rol: str("CLIENTE") }));
    denegado(await llamar("nuevo1", "GET", "rutsRegistrados/123456785"));
    denegado(await llamar(OTRO_CLIENTE, "GET", "rutsRegistrados/123456785"));
    denegado(await llamar("nuevo1", "GET", "rutsRegistrados"));
    denegado(await patch("nuevo1", "rutsRegistrados/123456785", { uid: str(OTRO_CLIENTE) }));
    denegado(await llamar("nuevo1", "DELETE", "rutsRegistrados/123456785"));
    denegado(await commitComoCliente("x1", [{ ruta: "rutsRegistrados/no-es-un-rut", campos: { uid: str("x1") } }]));
    denegado(await commitComoCliente("x1", [{ ruta: "rutsRegistrados/111111119", campos: { uid: str("x1"), extra: str("y") } }]));
    ok(await commitComoCliente("x1", [{ ruta: "rutsRegistrados/11111111K", campos: { uid: str("x1") } }]));
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

describe("reglas: profesionales/{uid}/certificaciones/{id}", () => {
  const ruta = (uid: string, id = "c1") => `profesionales/${uid}/certificaciones/${id}`;
  const entero = (n: number): Valor => ({ integerValue: String(n) });
  const valido = (uid: string): Record<string, Valor> => ({
    nombre: str("Diplomado en kinesiologia deportiva"),
    archivoPath: str(`profesionales/${uid}/certificaciones/c1.pdf`),
    tipoContenido: str("application/pdf"),
    tamanoBytes: entero(1_300_000),
    estado: str("EN_REVISION"),
  });

  /** Crea el registro; `creadoEn` lo fija el servidor (REQUEST_TIME), como `FieldValue.serverTimestamp()`. */
  async function crear(
    uid: string | null,
    duenoRuta: string,
    campos: Record<string, Valor>,
    conServerTimestamp = true,
    id = "c1",
  ): Promise<number> {
    const name = `projects/${PROJECT_ID}/databases/(default)/documents/${ruta(duenoRuta, id)}`;
    const r = await fetch(`${BASE.replace(/\/documents$/, "")}/documents:commit`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...(uid === null ? {} : { Authorization: `Bearer ${jwt(uid)}` }) },
      body: JSON.stringify({
        writes: [
          {
            update: { name, fields: campos },
            ...(conServerTimestamp
              ? { updateTransforms: [{ fieldPath: "creadoEn", setToServerValue: "REQUEST_TIME" }] }
              : {}),
          },
        ],
      }),
    });
    await r.text();
    return r.status;
  }

  it("el dueno crea su certificacion EN_REVISION con serverTimestamp y la lee", async () => {
    ok(await crear(PRO, PRO, valido(PRO)));
    const doc = await db.collection("profesionales").doc(PRO).collection("certificaciones").doc("c1").get();
    assert.equal(doc.get("estado"), "EN_REVISION");
    assert.ok(doc.get("creadoEn") instanceof Timestamp);
    ok(await llamar(PRO, "GET", ruta(PRO)));
    ok(await llamar(PRO, "GET", `profesionales/${PRO}/certificaciones`));
  });

  it("nadie crea ni lee las certificaciones de otro profesional (ni anonimo)", async () => {
    denegado(await crear(OTRO_PRO, PRO, valido(PRO)));
    denegado(await crear(CLIENTE, PRO, valido(PRO)));
    denegado(await crear(null, PRO, valido(PRO)));
    ok(await crear(PRO, PRO, valido(PRO)));
    denegado(await llamar(OTRO_PRO, "GET", ruta(PRO)));
    denegado(await llamar(OTRO_PRO, "GET", `profesionales/${PRO}/certificaciones`));
    denegado(await llamar(null, "GET", ruta(PRO)));
  });

  it("exige el estado EN_REVISION: un profesional no se autoaprueba", async () => {
    denegado(await crear(PRO, PRO, { ...valido(PRO), estado: str("APROBADA") }));
    denegado(await crear(PRO, PRO, { ...valido(PRO), estado: str("RECHAZADA") }));
  });

  it("exige claves exactas: sin una requerida o con una extra (motivoRechazo) se rechaza", async () => {
    const { nombre: _nombre, ...sinNombre } = valido(PRO);
    denegado(await crear(PRO, PRO, sinNombre));
    denegado(await crear(PRO, PRO, { ...valido(PRO), motivoRechazo: str("x") }));
    denegado(await crear(PRO, PRO, { ...valido(PRO), verificada: { booleanValue: true } as never }));
  });

  it("creadoEn debe ser el instante del servidor, no uno elegido por el cliente", async () => {
    denegado(await crear(PRO, PRO, valido(PRO), false));
    denegado(await crear(PRO, PRO, { ...valido(PRO), creadoEn: { timestampValue: "2020-01-01T00:00:00Z" } as never }, false));
  });

  it("acota el nombre (1 a 120), el tipo (PDF, JPEG o PNG) y el tamano (< 10 MB)", async () => {
    denegado(await crear(PRO, PRO, { ...valido(PRO), nombre: str("") }));
    denegado(await crear(PRO, PRO, { ...valido(PRO), nombre: str("a".repeat(121)) }));
    ok(await crear(PRO, PRO, { ...valido(PRO), nombre: str("a".repeat(120)) }, true, "c2"));
    denegado(await crear(PRO, PRO, { ...valido(PRO), tipoContenido: str("application/zip") }));
    denegado(await crear(PRO, PRO, { ...valido(PRO), tamanoBytes: entero(0) }));
    denegado(await crear(PRO, PRO, { ...valido(PRO), tamanoBytes: entero(10 * 1024 * 1024) }));
    ok(await crear(PRO, PRO, { ...valido(PRO), tamanoBytes: entero(10 * 1024 * 1024 - 1) }, true, "c3"));
  });

  it("archivoPath debe apuntar a la carpeta de certificaciones del propio profesional", async () => {
    denegado(await crear(PRO, PRO, { ...valido(PRO), archivoPath: str(`profesionales/${OTRO_PRO}/certificaciones/c1.pdf`) }));
    denegado(await crear(PRO, PRO, { ...valido(PRO), archivoPath: str(`profesionales/${PRO}/credenciales/c1.pdf`) }));
    denegado(await crear(PRO, PRO, { ...valido(PRO), archivoPath: str(`profesionales/${PRO}/certificaciones/a/b.pdf`) }));
  });

  it("la app no edita ni borra una certificacion (la resuelve soporte con Admin SDK)", async () => {
    ok(await crear(PRO, PRO, valido(PRO)));
    denegado(await llamar(PRO, "PATCH", `${ruta(PRO)}?updateMask.fieldPaths=estado`, { fields: { estado: str("APROBADA") } }));
    denegado(await llamar(PRO, "DELETE", ruta(PRO)));
    // crear otra vez el mismo id equivale a un update: tambien denegado
    denegado(await crear(PRO, PRO, valido(PRO)));
    const doc = await db.collection("profesionales").doc(PRO).collection("certificaciones").doc("c1").get();
    assert.equal(doc.get("estado"), "EN_REVISION");
  });
});

describe("reglas: comisionesPendientes y cobrosComision (saldo adeudado por atenciones en efectivo)", () => {
  const colecciones = ["comisionesPendientes", "cobrosComision"] as const;

  beforeEach(async () => {
    await db.collection("comisionesPendientes").doc("r1").set({ profesionalId: PRO, monto: 2500, estado: "PENDIENTE" });
    await db.collection("cobrosComision").doc("c1").set({ profesionalId: PRO, monto: 2500, estado: "PENDIENTE" });
  });

  const idDe = (coleccion: (typeof colecciones)[number]) => (coleccion === "comisionesPendientes" ? "r1" : "c1");

  for (const coleccion of colecciones) {
    it(`${coleccion}: la lee solo el profesional dueño`, async () => {
      ok(await llamar(PRO, "GET", `${coleccion}/${idDe(coleccion)}`));
      denegado(await llamar(OTRO_PRO, "GET", `${coleccion}/${idDe(coleccion)}`));
      denegado(await llamar(CLIENTE, "GET", `${coleccion}/${idDe(coleccion)}`));
      denegado(await llamar(null, "GET", `${coleccion}/${idDe(coleccion)}`));
    });

    it(`${coleccion}: nadie la escribe desde la app, ni el dueño`, async () => {
      const campos = { profesionalId: str(PRO), monto: { integerValue: "1" }, estado: str("PAGADA") };
      denegado(await llamar(PRO, "PATCH", `${coleccion}/${idDe(coleccion)}?updateMask.fieldPaths=estado`, { fields: campos }));
      denegado(await llamar(PRO, "PATCH", `${coleccion}/nuevo`, { fields: campos }));
      denegado(await llamar(PRO, "DELETE", `${coleccion}/${idDe(coleccion)}`));
    });
  }
});
