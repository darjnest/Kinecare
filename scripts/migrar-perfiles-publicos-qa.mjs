#!/usr/bin/env node
// Migracion de datos para la separacion publico/privado de `usuarios` (firestore.rules): para cada
// `usuarios/{uid}` existente en QA crea `perfilesPublicos/{uid}` ({nombre, fotoUrl}) y reclama el RUT en
// `rutsRegistrados/{rut}` ({uid}). Idempotente. Sin `--aplicar` solo informa (dry-run).
//
// ORDEN DE DESPLIEGUE: correr esto (con --aplicar) ANTES de desplegar las reglas nuevas, o las pantallas
// que resuelven el nombre de otra persona fallaran hasta que se migre. Es seguro correrlo antes: las
// reglas viejas ignoran estas colecciones y la app vieja tambien.
//
// Uso:
//   gcloud auth application-default login   # o: export GOOGLE_APPLICATION_CREDENTIALS=<clave.json>
//   node scripts/migrar-perfiles-publicos-qa.mjs            # dry-run
//   node scripts/migrar-perfiles-publicos-qa.mjs --aplicar
//
// Se niega a correr contra otro proyecto que no sea QA. Un RUT repetido en varios usuarios (posible antes
// del indice: el registro con Google lo comprobaba solo en el cliente) NO se resuelve solo: se informa y
// se deja el RUT reclamado por el primer usuario, para revisarlo a mano.
import { createRequire } from "node:module";
import { parseArgs } from "node:util";

const PROYECTO_QA = "kinecare-cl-qa";
const require = createRequire(new URL("../functions/package.json", import.meta.url));
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");

const { values: args } = parseArgs({ options: { aplicar: { type: "boolean", default: false } } });

initializeApp({ projectId: PROYECTO_QA });
const db = getFirestore();

const usuarios = await db.collection("usuarios").get();
console.log(`${usuarios.size} usuarios en ${PROYECTO_QA}${args.aplicar ? "" : " (dry-run, nada se escribe)"}`);

const rutsVistos = new Map(); // rut -> uid que lo reclamo
const escrituras = [];
const conflictos = [];
const sinDatos = [];

for (const doc of usuarios.docs) {
  const { nombre, fotoUrl = null, rut } = doc.data();
  if (typeof nombre !== "string" || nombre.length === 0) {
    sinDatos.push(`${doc.id}: sin nombre`);
    continue;
  }
  escrituras.push({ ref: db.collection("perfilesPublicos").doc(doc.id), datos: { nombre, fotoUrl: typeof fotoUrl === "string" ? fotoUrl : null } });

  if (typeof rut !== "string" || !/^[0-9]{6,8}[0-9K]$/.test(rut)) {
    sinDatos.push(`${doc.id}: RUT ausente o con formato invalido (${String(rut)})`);
    continue;
  }
  const previo = rutsVistos.get(rut);
  if (previo !== undefined && previo !== doc.id) {
    conflictos.push(`RUT ${rut} repetido: ya reclamado por usuarios/${previo}, tambien en usuarios/${doc.id}`);
    continue;
  }
  rutsVistos.set(rut, doc.id);
  escrituras.push({ ref: db.collection("rutsRegistrados").doc(rut), datos: { uid: doc.id } });
}

if (args.aplicar) {
  const escritor = db.bulkWriter();
  for (const { ref, datos } of escrituras) escritor.set(ref, datos);
  await escritor.close();
}

console.log(`${escrituras.length} documentos ${args.aplicar ? "escritos" : "por escribir"} (perfilesPublicos + rutsRegistrados)`);
for (const m of [...sinDatos, ...conflictos]) console.log(`  AVISO: ${m}`);
if (conflictos.length > 0) process.exitCode = 2;
