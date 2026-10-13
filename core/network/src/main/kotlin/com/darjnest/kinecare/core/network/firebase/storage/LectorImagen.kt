package com.darjnest.kinecare.core.network.firebase.storage

import com.darjnest.kinecare.core.common.data.error.FotoPerfilError
import com.darjnest.kinecare.core.common.result.Result

/** Lee la imagen elegida por el usuario y la deja lista para subir (JPEG reducido). Separado para poder probar el repositorio sin Android. */
interface LectorImagen {
    /** @return los bytes JPEG, siempre bajo [TAMANO_MAXIMO_BYTES], o `IMAGEN_INVALIDA`/`IMAGEN_MUY_GRANDE`. */
    suspend fun leerComoJpeg(uri: String): Result<ByteArray, FotoPerfilError>
}
