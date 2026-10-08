import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";
import { leerResena, profesionalesAfectados, redondearPromedio } from "../../src/calificacion.js";

const r = (profesionalId: string, calificacion: number) => ({ profesionalId, calificacion });

describe("leerResena", () => {
  it("extrae profesionalId y calificacion", () => {
    assert.deepEqual(leerResena({ profesionalId: "p1", calificacion: 4, comentario: "ok" }), r("p1", 4));
  });

  it("devuelve null sin documento (creacion o borrado)", () => {
    assert.equal(leerResena(undefined), null);
  });

  it("devuelve null si faltan campos o tienen tipo incorrecto", () => {
    assert.equal(leerResena({ calificacion: 4 }), null);
    assert.equal(leerResena({ profesionalId: "", calificacion: 4 }), null);
    assert.equal(leerResena({ profesionalId: "p1" }), null);
    assert.equal(leerResena({ profesionalId: "p1", calificacion: "5" }), null);
    assert.equal(leerResena({ profesionalId: "p1", calificacion: Number.NaN }), null);
  });
});

describe("profesionalesAfectados", () => {
  it("crear una resena afecta a su profesional", () => {
    assert.deepEqual(profesionalesAfectados(null, r("p1", 5)), ["p1"]);
  });

  it("borrar una resena afecta a su profesional", () => {
    assert.deepEqual(profesionalesAfectados(r("p1", 5), null), ["p1"]);
  });

  it("responder (misma calificacion y profesional) no recalcula", () => {
    assert.deepEqual(profesionalesAfectados(r("p1", 4), r("p1", 4)), []);
  });

  it("cambiar la calificacion recalcula", () => {
    assert.deepEqual(profesionalesAfectados(r("p1", 4), r("p1", 2)), ["p1"]);
  });

  it("cambiar de profesional recalcula a los dos, sin repetir", () => {
    assert.deepEqual(profesionalesAfectados(r("p1", 4), r("p2", 4)).sort(), ["p1", "p2"]);
  });

  it("sin antes ni despues (documento ilegible) no hay nada que recalcular", () => {
    assert.deepEqual(profesionalesAfectados(null, null), []);
  });
});

describe("redondearPromedio", () => {
  it("redondea a 2 decimales", () => {
    assert.equal(redondearPromedio(13 / 3), 4.33);
    assert.equal(redondearPromedio(4.666666), 4.67);
  });

  it("deja intactos los valores exactos", () => {
    assert.equal(redondearPromedio(5), 5);
    assert.equal(redondearPromedio(4.5), 4.5);
  });
});

// El Firestore Emulator NO exige indices compuestos, asi que los tests de integracion pasan aunque
// falte uno; en QA la agregacion `average("calificacion")` filtrada por `profesionalId` fallaba con
// FAILED_PRECONDITION hasta que existio este indice. Este test evita que se borre sin darse cuenta.
describe("indices de firestore.indexes.json", () => {
  it("existe el indice profesionalId + calificacion en resenas que necesita el recalculo", () => {
    const { indexes } = JSON.parse(readFileSync("../firestore.indexes.json", "utf-8")) as {
      indexes: { collectionGroup: string; fields: { fieldPath: string; order?: string }[] }[];
    };
    const existe = indexes.some(
      (i) =>
        i.collectionGroup === "resenas" &&
        i.fields.length === 2 &&
        i.fields[0]?.fieldPath === "profesionalId" &&
        i.fields[0]?.order === "ASCENDING" &&
        i.fields[1]?.fieldPath === "calificacion" &&
        i.fields[1]?.order === "ASCENDING",
    );
    assert.ok(existe, "falta el indice resenas(profesionalId ASC, calificacion ASC)");
  });
});
