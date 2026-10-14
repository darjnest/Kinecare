package com.darjnest.kinecare.core.network.firebase.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.darjnest.kinecare.core.common.data.error.CertificacionError
import com.darjnest.kinecare.core.common.result.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

private const val ETIQUETA = "LectorDocumento"
private const val NOMBRE_POR_DEFECTO = "Certificación"

/**
 * Lee un `content://` del selector de documentos del sistema (`OpenDocument`). Todo en memoria: no
 * escribe archivos ni cache. Corta la lectura al pasar el limite para no cargar un archivo enorme.
 */
class LectorDocumentoAndroid @Inject constructor(
    @ApplicationContext private val context: Context,
) : LectorDocumento {

    override suspend fun leer(uri: String): Result<DocumentoLeido, CertificacionError> =
        withContext(Dispatchers.IO) {
            try {
                procesar(Uri.parse(uri))
            } catch (e: Exception) {
                Log.w(ETIQUETA, "No se pudo leer el documento elegido", e)
                Result.Error(CertificacionError.ARCHIVO_INVALIDO)
            }
        }

    private fun procesar(uri: Uri): Result<DocumentoLeido, CertificacionError> {
        val resolver = context.contentResolver
        val tipo = resolver.getType(uri)
        if (tipo == null || tipo !in EXTENSIONES_DOCUMENTO) return Result.Error(CertificacionError.TIPO_NO_PERMITIDO)

        val nombre = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

        val salida = ByteArrayOutputStream()
        val entrada = resolver.openInputStream(uri) ?: return Result.Error(CertificacionError.ARCHIVO_INVALIDO)
        entrada.use {
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val leidos = it.read(buffer)
                if (leidos < 0) break
                salida.write(buffer, 0, leidos)
                if (salida.size() > TAMANO_MAXIMO_DOCUMENTO_BYTES) return Result.Error(CertificacionError.ARCHIVO_MUY_GRANDE)
            }
        }
        if (salida.size() == 0) return Result.Error(CertificacionError.ARCHIVO_INVALIDO)

        return Result.Success(
            DocumentoLeido(
                bytes = salida.toByteArray(),
                tipoContenido = tipo,
                nombreSugerido = nombre?.let(::nombreSinExtension)?.takeIf { it.isNotBlank() } ?: NOMBRE_POR_DEFECTO,
            ),
        )
    }
}
