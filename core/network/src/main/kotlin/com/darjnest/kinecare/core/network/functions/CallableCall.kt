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
 * Llama a una funcion `onCall` con el ID token del usuario autenticado. El
 * cuerpo de error (si lo hay) se parsea a [CallableErrorDto] y [aError] lo
 * traduce al error propio de cada funcion; un cuerpo que no es JSON de
 * callable (p. ej. el 404 HTML de una funcion no desplegada) llega como `null`.
 *
 * Sin sesion (o sin token) retorna [sinSesion]; `IOException` y
 * `FirebaseNetworkException` retornan [sinInternet]; cualquier otra excepcion
 * [desconocido]. Compartido por los repositorios que hablan con Cloud
 * Functions (`ReservaRepositoryImpl`, `PagoRepositoryImpl` de
 * `:feature:payment`).
 */
suspend fun <T, E : Error> llamarCallable(
    firebaseAuth: FirebaseAuth,
    json: Json,
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
            Result.Error(aError(errorCallable(json, respuesta.errorBody()?.string())))
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

private fun errorCallable(json: Json, cuerpo: String?): CallableErrorDto? = cuerpo
    ?.let { runCatching { json.decodeFromString<CallableErrorBody>(it) }.getOrNull() }
    ?.error
