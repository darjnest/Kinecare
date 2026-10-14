package com.darjnest.kinecare.core.common.data.repository

import com.darjnest.kinecare.core.common.data.error.CertificacionError
import com.darjnest.kinecare.core.common.domain.model.Certificacion
import com.darjnest.kinecare.core.common.result.Result

/**
 * Certificaciones que sube el profesional para acreditar especialidades (ver
 * docs/DATA_MODEL.md#firebase-storage). El archivo va a Storage y queda un registro en Firestore
 * con estado `EN_REVISION`; quien lo aprueba o rechaza es soporte, desde la consola.
 */
interface CertificacionRepository {

    /** Las certificaciones de [profesionalId], de la mas reciente a la mas antigua. */
    suspend fun obtener(profesionalId: String): Result<List<Certificacion>, CertificacionError>

    /**
     * Lee el archivo de [archivoUri] (un `content://` del selector de documentos del sistema; PDF,
     * JPEG o PNG de hasta 10 MB), lo sube y crea el registro `EN_REVISION`. El archivo se procesa solo
     * en memoria: nada queda guardado en el dispositivo. [nombre] vacio usa el nombre del archivo.
     */
    suspend fun subir(
        profesionalId: String,
        archivoUri: String,
        nombre: String? = null,
    ): Result<Unit, CertificacionError>
}
