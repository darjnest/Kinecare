#!/usr/bin/env node
// Crea una cuenta de Profesional en el proyecto QA (kinecare-cl-qa): usuario de Firebase Auth
// (email sintetico derivado del RUT, igual que la app) + usuarios/{uid} + profesionales/{uid}.
//
// Uso:
//   gcloud auth application-default login   # o: export GOOGLE_APPLICATION_CREDENTIALS=<clave-de-cuenta-de-servicio.json>
//   node scripts/crear-profesional-qa.mjs --rut 12.345.678-5 --nombre "Ana Prueba" \
//        [--tipos KINESIOLOGIA,MASOTERAPIA] [--telefono +56912345678] [--correo ana@ejemplo.cl] [--minimo]
//
// La contrasena NO se pasa por argumento: se pide por consola (sin eco) o se toma de la variable
// KINECARE_PASSWORD. Requiere `npm ci` en functions/ (trae firebase-admin).
//
// Por defecto deja la cuenta lista para probar reservas: 1 servicio, disponibilidad lun-vie y
// ubicacion en Santiago. Con --minimo escribe exactamente el perfil inicial que crea la app al
// registrarse (sin servicios ni horario). Se niega a correr contra otro proyecto que no sea QA y
// no sobrescribe cuentas existentes.
import { createRequire } from "node:module";
import { parseArgs } from "node:util";

const PROYECTO_QA = "kinecare-cl-qa";
const TIPOS_VALIDOS = ["KINESIOLOGIA", "MASOTERAPIA"];

const require = createRequire(new URL("../functions/package.json", import.meta.url));
let admin;
try {
  admin = require("firebase-admin");
} catch {
  fallar("falta firebase-admin; corre 'npm ci' en functions/");
}
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue, GeoPoint } = require("firebase-admin/firestore");

function fallar(mensaje) {
  console.error(`ERROR: ${mensaje}`);
  process.exit(1);
}

// --- Mismo algoritmo que RutUtils.kt (core:common) ---
const normalizarRut = (rut) => rut.trim().replaceAll(".", "").replaceAll("-", "").toUpperCase();

function rutValido(rut) {
  const limpio = normalizarRut(rut);
  if (limpio.length < 2) return false;
  const cuerpo = limpio.slice(0, -1);
  if (!/^\d+$/.test(cuerpo)) return false;
  let suma = 0;
  let mult = 2;
  for (const c of [...cuerpo].reverse()) {
    suma += Number(c) * mult;
    mult = mult === 7 ? 2 : mult + 1;
  }
  const resto = 11 - (suma % 11);
  const dv = resto === 11 ? "0" : resto === 10 ? "K" : String(resto);
  return limpio.at(-1) === dv;
}

const emailFirebase = (rut) => `${normalizarRut(rut).toLowerCase()}@rut.kinecare.cl`;

function pedirPassword() {
  if (process.env.KINECARE_PASSWORD) return Promise.resolve(process.env.KINECARE_PASSWORD);
  if (!process.stdin.isTTY) fallar("sin terminal interactiva: define KINECARE_PASSWORD");
  // Lectura tecla a tecla en modo raw: sin eco y sin depender de readline.
  return new Promise((resolver) => {
    const stdin = process.stdin;
    let texto = "";
    const terminar = (valor) => {
      stdin.removeListener("data", alTecla);
      stdin.setRawMode(false);
      stdin.pause();
      process.stdout.write("\n");
      if (valor === null) fallar("cancelado");
      resolver(valor);
    };
    const alTecla = (chunk) => {
      for (const c of chunk.toString("utf8")) {
        if (c === "\r" || c === "\n") return terminar(texto);
        if (c === "\u0003" || c === "\u0004") return terminar(null); // Ctrl+C / Ctrl+D
        if (c === "\u007f" || c === "\b") texto = texto.slice(0, -1);
        else if (c >= " ") texto += c;
      }
    };
    process.stdout.write("Contrasena (min. 6 caracteres): ");
    stdin.setRawMode(true);
    stdin.resume();
    stdin.on("data", alTecla);
  });
}

const { values: args } = parseArgs({
  options: {
    rut: { type: "string" },
    nombre: { type: "string" },
    tipos: { type: "string", default: "KINESIOLOGIA" },
    telefono: { type: "string", default: "" },
    correo: { type: "string", default: "" },
    minimo: { type: "boolean", default: false },
  },
});

if (!args.rut || !args.nombre) fallar("faltan --rut y --nombre (ver cabecera del script)");
if (!rutValido(args.rut)) fallar(`RUT invalido: ${args.rut}`);
const tipos = [...new Set(args.tipos.split(",").map((t) => t.trim().toUpperCase()))];
if (tipos.length === 0 || tipos.length > 2 || !tipos.every((t) => TIPOS_VALIDOS.includes(t))) {
  fallar(`--tipos debe ser uno o ambos de ${TIPOS_VALIDOS.join(", ")}`);
}

const rut = normalizarRut(args.rut);
const email = emailFirebase(rut);

// initializeApp usa credenciales por defecto (ADC o GOOGLE_APPLICATION_CREDENTIALS); el projectId
// explicito evita que un proyecto por defecto distinto (p. ej. produccion) reciba la escritura.
initializeApp({ projectId: PROYECTO_QA });
const auth = getAuth();
const db = getFirestore();

// Sin pisar nada existente: ni el usuario de Auth ni un RUT ya registrado en Firestore.
const existente = await auth.getUserByEmail(email).catch((e) => (e.code === "auth/user-not-found" ? null : Promise.reject(e)));
if (existente) fallar(`ya existe un usuario de Auth para el RUT ${rut} (uid ${existente.uid})`);
const rutEnUso = await db.collection("usuarios").where("rut", "==", rut).limit(1).get();
if (!rutEnUso.empty) fallar(`el RUT ${rut} ya figura en usuarios/${rutEnUso.docs[0].id}`);

const password = await pedirPassword();
if (password.length < 6) fallar("la contrasena debe tener al menos 6 caracteres");

const { uid } = await auth.createUser({ email, password, displayName: args.nombre });

try {
  const perfil = {
    tiposAtencion: tipos.sort((a, b) => TIPOS_VALIDOS.indexOf(a) - TIPOS_VALIDOS.indexOf(b)),
    especialidades: [],
    rnpi: "",
    descripcion: "",
    calificacionPromedio: 0, // el campo debe existir o el perfil no aparece en la busqueda
    totalResenas: 0,
    estadoVerificacionGeneral: "NO_SOLICITADO",
    insignias: [],
    disponibilidad: [],
  };
  if (!args.minimo) {
    perfil.especialidades = tipos.map((t) => (t === "KINESIOLOGIA" ? "Kinesiologia" : "Masoterapia"));
    perfil.descripcion = "Cuenta de prueba creada con scripts/crear-profesional-qa.mjs";
    perfil.disponibilidad = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"].map((diaSemana) => ({
      diaSemana,
      horaInicio: "09:00",
      horaFin: "18:00",
      activo: true,
    }));
    perfil.ubicacion = new GeoPoint(-33.4489, -70.6693); // Santiago centro
  }

  const batch = db.batch();
  batch.set(db.collection("usuarios").doc(uid), {
    nombre: args.nombre,
    rut,
    email,
    correoContacto: args.correo,
    telefono: args.telefono,
    rol: "PROFESIONAL",
    fotoUrl: null,
    fechaRegistro: FieldValue.serverTimestamp(),
  });
  batch.set(db.collection("profesionales").doc(uid), perfil);
  if (!args.minimo) {
    batch.set(db.collection("profesionales").doc(uid).collection("servicios").doc(), {
      nombre: "Sesion de kinesiologia",
      descripcion: "Servicio de prueba",
      modalidad: "CONSULTA",
      duracionMinutos: 60,
      precio: 20000,
      activo: true,
    });
  }
  await batch.commit();
} catch (e) {
  // No dejar un usuario de Auth huerfano (sin usuarios/{uid} la app no sabe su rol).
  await auth.deleteUser(uid).catch(() => undefined);
  fallar(`no se pudieron escribir los documentos en Firestore; se revirtio el usuario de Auth: ${e.message}`);
}

console.log(`Profesional creado en ${PROYECTO_QA}`);
console.log(`  uid:    ${uid}`);
console.log(`  RUT:    ${rut} (login en la app: RUT + la contrasena ingresada)`);
console.log(`  tipos:  ${tipos.join(", ")}${args.minimo ? " (perfil minimo, como el registro de la app)" : ""}`);
