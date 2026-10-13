@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.reviews.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darjnest.kinecare.core.common.data.error.ResenaError
import com.darjnest.kinecare.core.common.data.repository.ResenaRepository
import com.darjnest.kinecare.core.common.data.repository.UsuarioRepository
import com.darjnest.kinecare.core.common.domain.model.Resena
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.reviews.presentation.navigation.ARG_PROFESIONAL_ID
import com.darjnest.kinecare.feature.reviews.presentation.util.AUTOR_ANONIMO
import com.darjnest.kinecare.feature.reviews.presentation.util.nombreCorto
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Resena lista para mostrar (modelo de presentacion; no expone `clienteId` ni datos personales). */
data class ResenaItem(
    val id: String,
    /** "Nombre A." o "Cliente" si no se pudo resolver el autor. */
    val autor: String,
    val calificacion: Int,
    /** Nulo cuando el cliente no escribio comentario (o solo espacios): la vista lo oculta. */
    val comentario: String?,
    val fechaMillis: Long,
    val respuestaProfesional: String?,
    /** `true` si quien mira es el profesional dueño y la resena aun no tiene respuesta: se ofrece "Responder". */
    val puedeResponder: Boolean = false,
)

/** Largo maximo de la respuesta del profesional (firestore.rules / docs/DATA_MODEL.md). */
const val MAX_RESPUESTA_PROFESIONAL = 500

/** Respuesta en redaccion (dialogo abierto) a la resena [resenaId] de [autor]. */
data class RespuestaEnCurso(
    val resenaId: String,
    val autor: String,
    val texto: String = "",
    val enviando: Boolean = false,
    /** Error mostrado dentro del dialogo (sin conexion, sin permiso...). */
    val error: String? = null,
) {
    val puedeEnviar: Boolean
        get() = texto.isNotBlank() && texto.trim().length <= MAX_RESPUESTA_PROFESIONAL && !enviando
}

/**
 * Resumen calculado sobre las resenas **cargadas** (maximo 100, las mas
 * recientes). Ver [calcularResumen] por que no usa el promedio guardado.
 */
data class ResumenResenas(
    val promedio: Double,
    val total: Int,
    /** Cantidad de resenas por cantidad de estrellas; siempre las claves 5, 4, 3, 2, 1 en ese orden. */
    val distribucion: Map<Int, Int>,
)

data class ReviewsState(
    val cargando: Boolean = true,
    val error: ResenaError? = null,
    val resumen: ResumenResenas? = null,
    val resenas: List<ResenaItem> = emptyList(),
    /** No nulo mientras el dialogo "Responder" esta abierto. */
    val respuesta: RespuestaEnCurso? = null,
)

/** Eventos de una sola vez (no son estado): el `Root` los muestra en un Snackbar. */
sealed interface ReviewsEvent {
    data class MostrarMensaje(val texto: String) : ReviewsEvent
}

sealed interface ReviewsAction {
    data object Reintentar : ReviewsAction

    /** Abre el dialogo para responder una resena (solo el profesional dueño, resenas sin respuesta). */
    data class Responder(val resenaId: String) : ReviewsAction
    data class CambiarRespuesta(val texto: String) : ReviewsAction
    data object EnviarRespuesta : ReviewsAction
    data object DescartarRespuesta : ReviewsAction

    /** Navegacion: la resuelve el `Root` contra el NavGraph, no el ViewModel. */
    data object VolverAtras : ReviewsAction
}

/**
 * Promedio, total y distribucion 5 -> 1 estrellas calculados localmente a
 * partir de [resenas] (las cargadas, maximo 100).
 *
 * Es intencional y no se lee `profesionales.calificacionPromedio`/
 * `totalResenas`: hoy nada mantiene esos campos sincronizados al crear una
 * resena (recalcular requiere una Cloud Function, plan Blaze), asi que
 * mostrarlos aqui contradeciria la lista que esta debajo. Consecuencia
 * conocida: con mas de 100 resenas el resumen cubre solo las 100 mas
 * recientes. Devuelve `null` si no hay resenas.
 */
internal fun calcularResumen(resenas: List<Resena>): ResumenResenas? {
    if (resenas.isEmpty()) return null
    val porEstrellas = resenas.groupingBy { it.calificacion }.eachCount()
    return ResumenResenas(
        promedio = resenas.sumOf { it.calificacion }.toDouble() / resenas.size,
        total = resenas.size,
        distribucion = (5 downTo 1).associateWith { porEstrellas[it] ?: 0 },
    )
}

@HiltViewModel
class ReviewsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val resenaRepository: ResenaRepository,
    private val usuarioRepository: UsuarioRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val profesionalId: String? = savedStateHandle[ARG_PROFESIONAL_ID]

    private val _eventos = Channel<ReviewsEvent>(Channel.BUFFERED)
    val eventos: Flow<ReviewsEvent> = _eventos.receiveAsFlow()

    /** Solo el profesional dueño del perfil puede responder; el backend (firestore.rules) lo vuelve a exigir. */
    private val esProfesionalDueno: Boolean
        get() = !profesionalId.isNullOrBlank() && firebaseAuth.currentUser?.uid == profesionalId

    private val _state = MutableStateFlow(ReviewsState())
    val state: StateFlow<ReviewsState> = _state.asStateFlow()

    /** clienteId -> "Nombre A."; solo guarda los que se resolvieron, para no repetir llamadas al reintentar. */
    private val autoresResueltos = mutableMapOf<String, String>()
    private var cargaJob: Job? = null

    init {
        cargar()
    }

    fun onAction(action: ReviewsAction) {
        when (action) {
            ReviewsAction.Reintentar -> cargar()
            is ReviewsAction.Responder -> abrirRespuesta(action.resenaId)
            is ReviewsAction.CambiarRespuesta -> _state.update { estado ->
                estado.respuesta?.takeIf { !it.enviando }?.let {
                    estado.copy(
                        respuesta = it.copy(texto = action.texto.take(MAX_RESPUESTA_PROFESIONAL), error = null),
                    )
                } ?: estado
            }
            ReviewsAction.EnviarRespuesta -> enviarRespuesta()
            ReviewsAction.DescartarRespuesta -> _state.update { estado ->
                if (estado.respuesta?.enviando == true) estado else estado.copy(respuesta = null)
            }
            ReviewsAction.VolverAtras -> Unit
        }
    }

    private fun abrirRespuesta(resenaId: String) {
        val resena = _state.value.resenas.firstOrNull { it.id == resenaId && it.puedeResponder } ?: return
        _state.update { it.copy(respuesta = RespuestaEnCurso(resenaId = resena.id, autor = resena.autor)) }
    }

    private fun enviarRespuesta() {
        val respuesta = _state.value.respuesta?.takeIf { it.puedeEnviar } ?: return
        if (!esProfesionalDueno) return
        val texto = respuesta.texto.trim()
        _state.update { it.copy(respuesta = respuesta.copy(enviando = true, error = null)) }
        viewModelScope.launch {
            when (val resultado = resenaRepository.responder(respuesta.resenaId, texto)) {
                is Result.Success -> {
                    _state.update { estado ->
                        estado.copy(
                            respuesta = null,
                            resenas = estado.resenas.map {
                                if (it.id == respuesta.resenaId) {
                                    it.copy(respuestaProfesional = texto, puedeResponder = false)
                                } else {
                                    it
                                }
                            },
                        )
                    }
                    _eventos.send(ReviewsEvent.MostrarMensaje("Respuesta publicada."))
                }
                is Result.Error -> _state.update {
                    it.copy(respuesta = respuesta.copy(enviando = false, error = mensajeDeErrorAlResponder(resultado.error)))
                }
            }
        }
    }

    private fun cargar() {
        val id = profesionalId
        if (id.isNullOrBlank()) {
            _state.update { it.copy(cargando = false, error = ResenaError.DESCONOCIDO) }
            return
        }
        cargaJob?.cancel()
        _state.update { it.copy(cargando = true, error = null) }
        cargaJob = viewModelScope.launch {
            when (val resultado = resenaRepository.obtenerPorProfesional(id)) {
                is Result.Success -> {
                    val resenas = resultado.data
                    resolverAutores(resenas.map { it.clienteId }.distinct())
                    _state.update {
                        it.copy(
                            cargando = false,
                            error = null,
                            resumen = calcularResumen(resenas),
                            resenas = resenas.map { resena -> resena.aItem() },
                        )
                    }
                }
                is Result.Error -> _state.update {
                    it.copy(cargando = false, error = resultado.error, resumen = null, resenas = emptyList())
                }
            }
        }
    }

    /** Resuelve cada `clienteId` distinto una sola vez; si falla queda sin entrada y se muestra [AUTOR_ANONIMO]. */
    private suspend fun resolverAutores(clienteIds: List<String>) {
        val pendientes = clienteIds.filter { it !in autoresResueltos }
        if (pendientes.isEmpty()) return
        coroutineScope {
            pendientes.map { clienteId ->
                async {
                    val resultado = usuarioRepository.obtenerPerfilPublico(clienteId)
                    if (resultado is Result.Success) clienteId to nombreCorto(resultado.data.nombre) else null
                }
            }.awaitAll().filterNotNull().forEach { (clienteId, nombre) -> autoresResueltos[clienteId] = nombre }
        }
    }

    private fun Resena.aItem() = ResenaItem(
        id = id,
        autor = autoresResueltos[clienteId] ?: AUTOR_ANONIMO,
        calificacion = calificacion,
        comentario = comentario?.trim()?.takeIf { it.isNotEmpty() },
        fechaMillis = fecha.toEpochMilliseconds(),
        respuestaProfesional = respuestaProfesional?.trim()?.takeIf { it.isNotEmpty() },
        puedeResponder = esProfesionalDueno && respuestaProfesional?.isNotBlank() != true,
    )
}

private fun mensajeDeErrorAlResponder(error: ResenaError): String = when (error) {
    ResenaError.SIN_INTERNET -> "Sin conexión. Revisa tu internet e inténtalo de nuevo."
    ResenaError.SIN_PERMISO -> "No pudimos publicar tu respuesta: esta reseña ya no admite respuestas o no es de tu perfil."
    ResenaError.DESCONOCIDO -> "No pudimos publicar tu respuesta. Inténtalo de nuevo."
}
