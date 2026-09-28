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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
)

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
)

sealed interface ReviewsAction {
    data object Reintentar : ReviewsAction

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
) : ViewModel() {

    private val profesionalId: String? = savedStateHandle[ARG_PROFESIONAL_ID]

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
            ReviewsAction.VolverAtras -> Unit
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
                    val resultado = usuarioRepository.obtenerPorId(clienteId)
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
    )
}
