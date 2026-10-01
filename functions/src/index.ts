import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { crearReservaHandler } from "./crearReserva.js";

initializeApp();

// Wrapper delgado: toda la logica vive en crearReservaHandler (probado contra el emulador).
export const crearReserva = onCall({ region: "us-central1" }, (request) =>
  crearReservaHandler(getFirestore(), request.auth?.uid, request.data, new Date()),
);
