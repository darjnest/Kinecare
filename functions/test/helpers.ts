import assert from "node:assert/strict";
import { HttpsError } from "firebase-functions/v2/https";

/** Verifica que `fn` rechace con HttpsError(code) y details.motivo exactos. */
export async function esperarError(fn: () => unknown, code: string, motivo: string): Promise<void> {
  await assert.rejects(
    async () => {
      await fn();
    },
    (e: unknown) => {
      assert.ok(e instanceof HttpsError, `se esperaba HttpsError, llego: ${String(e)}`);
      assert.equal(e.code, code);
      assert.deepEqual(e.details, { motivo });
      return true;
    },
  );
}

/** Version sincrona de esperarError para helpers puros. */
export function esperarErrorSync(fn: () => unknown, code: string, motivo: string): void {
  assert.throws(fn, (e: unknown) => {
    assert.ok(e instanceof HttpsError, `se esperaba HttpsError, llego: ${String(e)}`);
    assert.equal(e.code, code);
    assert.deepEqual(e.details, { motivo });
    return true;
  });
}
