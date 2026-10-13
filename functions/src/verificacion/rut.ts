/** Deja solo alfanumericos, en mayusculas y sin ceros a la izquierda: "12.345.678-5" -> "123456785". */
export function normalizarRut(valor: string): string {
  return valor
    .replace(/[^0-9A-Za-z]/g, "")
    .toUpperCase()
    .replace(/^0+/, "");
}

/** Un RUN chileno ya normalizado: 7 u 8 digitos de cuerpo mas el digito verificador (0-9 o K). */
export function tieneFormaDeRun(normalizado: string): boolean {
  return /^\d{7,8}[0-9K]$/.test(normalizado);
}

/**
 * Compara el RUN que reporta el proveedor con el `rut` declarado por el usuario. Devuelve `null` si
 * no se puede comparar (el proveedor no entrego un valor con forma de RUN, o el usuario no tiene
 * `rut`): en ese caso la verificacion no se rechaza por RUT. Con ambos presentes, `true`/`false`.
 * El digito verificador no se valida: solo se compara igualdad.
 */
export function compararRut(rutProveedor: string | null, rutUsuario: unknown): boolean | null {
  if (rutProveedor === null) return null;
  const delProveedor = normalizarRut(rutProveedor);
  if (!tieneFormaDeRun(delProveedor)) return null;
  if (typeof rutUsuario !== "string") return null;
  const delUsuario = normalizarRut(rutUsuario);
  if (delUsuario === "") return null;
  return delProveedor === delUsuario;
}
