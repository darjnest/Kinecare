package com.darjnest.kinecare.core.network.firebase.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.darjnest.kinecare.core.common.data.error.FotoPerfilError
import com.darjnest.kinecare.core.common.result.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Decodifica la imagen del selector de fotos, la gira segun su orientacion EXIF, la reduce a
 * [LADO_MAXIMO_PX] y la comprime a JPEG. Todo en memoria: no escribe archivos ni cache. Al
 * recomprimir se pierden los metadatos EXIF (ubicacion de la foto incluida).
 */
class LectorImagenAndroid @Inject constructor(
    @ApplicationContext private val context: Context,
) : LectorImagen {

    override suspend fun leerComoJpeg(uri: String): Result<ByteArray, FotoPerfilError> =
        withContext(Dispatchers.IO) {
            try {
                procesar(Uri.parse(uri))
            } catch (e: OutOfMemoryError) {
                Result.Error(FotoPerfilError.IMAGEN_INVALIDA)
            } catch (e: Exception) {
                Result.Error(FotoPerfilError.IMAGEN_INVALIDA)
            }
        }

    private fun procesar(uri: Uri): Result<ByteArray, FotoPerfilError> {
        val resolver = context.contentResolver

        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
            ?: return Result.Error(FotoPerfilError.IMAGEN_INVALIDA)
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return Result.Error(FotoPerfilError.IMAGEN_INVALIDA)

        val opciones = BitmapFactory.Options().apply {
            inSampleSize = calcularInSampleSize(limites.outWidth, limites.outHeight)
        }
        val decodificada = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opciones) }
            ?: return Result.Error(FotoPerfilError.IMAGEN_INVALIDA)

        val orientacion = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val (anchoFinal, altoFinal) = dimensionesReducidas(decodificada.width, decodificada.height)
        val reducida = if (anchoFinal != decodificada.width || altoFinal != decodificada.height) {
            Bitmap.createScaledBitmap(decodificada, anchoFinal, altoFinal, true)
        } else {
            decodificada
        }
        val orientada = reducida.orientar(orientacion)
        val listaParaJpeg = orientada.sobreFondoBlanco()

        for (calidad in CALIDADES_JPEG) {
            val salida = ByteArrayOutputStream()
            if (!listaParaJpeg.compress(Bitmap.CompressFormat.JPEG, calidad, salida)) {
                return Result.Error(FotoPerfilError.IMAGEN_INVALIDA)
            }
            if (salida.size() < TAMANO_MAXIMO_BYTES) return Result.Success(salida.toByteArray())
        }
        return Result.Error(FotoPerfilError.IMAGEN_MUY_GRANDE)
    }

    private fun Bitmap.orientar(orientacion: Int): Bitmap {
        val matriz = Matrix()
        when (orientacion) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matriz.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matriz.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matriz.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matriz.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matriz.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matriz.postRotate(90f)
                matriz.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matriz.postRotate(270f)
                matriz.postScale(-1f, 1f)
            }
            else -> return this
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matriz, true)
    }

    /** JPEG no tiene transparencia: un PNG con alfa saldria con fondo negro. */
    private fun Bitmap.sobreFondoBlanco(): Bitmap {
        if (!hasAlpha()) return this
        val resultado = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(resultado).apply {
            drawColor(Color.WHITE)
            drawBitmap(this@sobreFondoBlanco, 0f, 0f, null)
        }
        return resultado
    }
}
