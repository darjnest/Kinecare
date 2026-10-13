package com.darjnest.kinecare.core.common.data.repository

import com.darjnest.kinecare.core.common.data.error.FotoPerfilError
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.result.Result

/**
 * Sube la foto de perfil a Firebase Storage (ver docs/DATA_MODEL.md#firebase-storage). Vive en
 * `:core:common` (interfaz) + `:core:network` (implementacion) porque Cliente y Profesional
 * (`:feature:client-panel` y `:feature:professional-panel`) la necesitan y las features nunca se
 * dependen entre si.
 *
 * Solo sube el archivo y devuelve su URL de descarga: guardar esa URL en `usuarios/{uid}.fotoUrl`
 * es responsabilidad de quien llama (`UsuarioRepository.actualizarFotoUrl`).
 */
interface FotoPerfilRepository {

    /**
     * Lee la imagen de [imagenUri] (un `content://` del selector de fotos del sistema), la reduce
     * a JPEG de ~1280 px como maximo y la sube a la ruta de foto de perfil de [usuarioId] segun
     * [rol] (`profesionales/{uid}/foto-perfil.jpg` o `clientes/{uid}/foto-perfil.jpg`). Reemplaza
     * la anterior. La imagen se procesa solo en memoria: nada queda guardado en el dispositivo.
     *
     * @return la URL de descarga de la foto subida.
     */
    suspend fun subir(
        usuarioId: String,
        rol: RolUsuario,
        imagenUri: String,
    ): Result<String, FotoPerfilError>
}
