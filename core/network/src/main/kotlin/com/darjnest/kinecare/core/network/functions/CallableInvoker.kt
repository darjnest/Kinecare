package com.darjnest.kinecare.core.network.functions

import com.darjnest.kinecare.core.common.result.Error
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.core.network.functions.dto.CallableErrorBody
import com.darjnest.kinecare.core.network.functions.dto.CallableErrorDto
import com.darjnest.kinecare.core.network.functions.dto.CallableResponse
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException

/**
 * Llama a una funcion `onCall` de Cloud Functions con el ID token del
 * usuario autenticado y traduce el sobre del protocolo (exito / cuerpo de
 * error) a un `Result`. Helper compartido por los repositorios que escriben
 * via funciones.
 *
 * Hoy lo usa `MercadoPagoRepositoryImpl`; `ReservaRepositoryImpl` conserva su
 * copia privada (`llamarCallable`/`errorCallable`) y podria migrar a este
 * helper en un cambio aparte.
 */
internal class CallableInvoker(
    private val firebaseAuth: FirebaseAuth,
    private val json: Json,
) {

    /**
     * El cuerpo de error (si lo hay) se parsea a [CallableErrorDto] y [aError]
     * lo traduce al error propio de cada funcion; un cuerpo que no es JSON de
     * callable (p. ej. el 404 HTML de una funcion no desplegada) llega como
     * `null`. Relanza [CancellationException].
     */
    suspend fun <T, E : Error> invocar(
        sinSesion: E,
        sinInternet: E,
        desconocido: E,
        aError: (CallableErrorDto?) -> E,
        llamada: suspend (autorizacion: String) -> Response<CallableResponse<T>>,
    ): Result<T, E> {
        val usuario = firebaseAuth.currentUser ?: return Result.Error(sinSesion)
        return try {
            val token = usuario.getIdToken(false).await().token
                ?: return Result.Error(sinSesion)
            val respuesta = llamada("Bearer $token")
            val cuerpo = respuesta.body()
            if (respuesta.isSuccessful && cuerpo != null) {
                Result.Success(cuerpo.result)
            } else {
                Result.Error(aError(errorCallable(respuesta.errorBody()?.string())))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseNetworkException) {
            Result.Error(sinInternet)
        } catch (e: IOException) {
            Result.Error(sinInternet)
        } catch (e: Exception) {
            Result.Error(desconocido)
        }
    }

    private fun errorCallable(cuerpo: String?): CallableErrorDto? = cuerpo
        ?.let { runCatching { json.decodeFromString<CallableErrorBody>(it) }.getOrNull() }
        ?.error
}
