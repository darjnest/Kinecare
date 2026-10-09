import { ProveedorVerificacionError, type DatosSesion, type DecisionProveedor, type ProveedorVerificacion } from "../src/verificacion/proveedor.js";

/**
 * Proveedor en memoria: registra las llamadas y devuelve lo que el test configure. Como Didit, es
 * idempotente por `vendorData`: mientras la sesion no se "termine" devuelve la misma.
 */
export class FakeProveedorVerificacion implements ProveedorVerificacion {
  sesionesCreadas: DatosSesion[] = [];
  consultas: string[] = [];
  decisiones = new Map<string, DecisionProveedor>();
  /** Si es un Error, `crearSesion`/`obtenerDecision` fallan con el. */
  fallaCrear: Error | null = null;
  fallaDecision: Error | null = null;

  private abiertas = new Map<string, string>();
  private contador = 0;

  async crearSesion(datos: DatosSesion) {
    this.sesionesCreadas.push(datos);
    if (this.fallaCrear !== null) throw this.fallaCrear;
    let sessionId = this.abiertas.get(datos.vendorData);
    if (sessionId === undefined) {
      sessionId = `sesion-${++this.contador}`;
      this.abiertas.set(datos.vendorData, sessionId);
    }
    return { sessionId, url: `https://verify.test/session/${sessionId}` };
  }

  async obtenerDecision(sessionId: string): Promise<DecisionProveedor> {
    this.consultas.push(sessionId);
    if (this.fallaDecision !== null) throw this.fallaDecision;
    const d = this.decisiones.get(sessionId);
    if (d === undefined) throw new ProveedorVerificacionError("NO_ENCONTRADO", 404);
    return d;
  }

  /** Cierra la sesion abierta del vendedor: el proximo `crearSesion` devuelve una nueva. */
  cerrarSesion(vendorData: string): void {
    this.abiertas.delete(vendorData);
  }
}
