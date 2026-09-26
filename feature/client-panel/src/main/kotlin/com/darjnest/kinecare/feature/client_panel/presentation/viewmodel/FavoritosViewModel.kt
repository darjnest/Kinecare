package com.darjnest.kinecare.feature.client_panel.presentation.viewmodel

import com.darjnest.kinecare.core.common.data.repository.ClienteRepository
import com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository
import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.Profesional
import com.darjnest.kinecare.core.common.domain.model.TipoInsignia
import com.darjnest.kinecare.core.common.result.Result
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Profesional guardado como favorito por el Cliente, mostrado en "Mis
 * Kinesiólogos Guardados". Modelo de presentacion reducido (no reemplaza
 * `Profesional` de dominio; se mapea desde el `Profesional` real resuelto a
 * partir de `Cliente.favoritos`).
 */
data class ProfesionalFavorito(
    val id: String,
    val nombre: String,
    val especialidad: String,
    val universidad: String,
    val esTerapeutaPrincipal: Boolean,
    val identidadVerificada: Boolean,
    val numeroRegistroSis: String,
    val calificacion: Double,
    val totalResenas: Int,
    val precioSesion: Long,
    val modalidad: ModalidadServicio,
    val lugarAtencion: String,
    val ultimaAtencionTexto: String? = null,
    val proximoCupoTexto: String? = null,
    val insigniaSecundariaTexto: String? = null,
)

/** Filtro de la lista de favoritos segun modalidad o especialidad. */
enum class FiltroFavoritos { TODOS, DOMICILIO, CONSULTA, KINESIOLOGIA, MASOTERAPIA }

data class FavoritosState(
    val cargando: Boolean = false,
    val favoritos: List<ProfesionalFavorito> = emptyList(),
    val filtroSeleccionado: FiltroFavoritos = FiltroFavoritos.TODOS,
)

sealed interface FavoritosAction {
    data class SeleccionarFiltro(val filtro: FiltroFavoritos) : FavoritosAction
    data class QuitarDeFavoritos(val profesionalId: String) : FavoritosAction
    data class AgendarConProfesional(val profesionalId: String) : FavoritosAction
}

/**
 * Mapea el `Profesional` real al modelo reducido de la tarjeta de
 * favoritos. `universidad` y `lugarAtencion` quedan vacios: el dominio de
 * `Profesional` (docs/DOMAIN.md) todavia no trae casa de estudios ni una
 * direccion/ubicacion legible (solo un geopoint en Firestore, sin mapear) —
 * no se inventan campos nuevos para llenarlos. `esTerapeutaPrincipal` marca
 * el primer favorito de la lista, misma convencion usada para la direccion
 * predeterminada en `MiPerfilClienteViewModel` (el dominio tampoco trae un
 * flag de "terapeuta principal").
 */
private fun Profesional.aProfesionalFavorito(esPrincipal: Boolean): ProfesionalFavorito {
    val identidadVerificada = insignias.any {
        it.tipo == TipoInsignia.IDENTIDAD && it.estado == EstadoVerificacion.APROBADO
    }
    return ProfesionalFavorito(
        id = usuario.id,
        nombre = usuario.nombre,
        especialidad = especialidades.firstOrNull().orEmpty(),
        universidad = "",
        esTerapeutaPrincipal = esPrincipal,
        identidadVerificada = identidadVerificada,
        numeroRegistroSis = rnpi,
        calificacion = calificacionPromedio,
        totalResenas = totalResenas,
        precioSesion = servicios.minOfOrNull { it.precio } ?: 0L,
        modalidad = servicios.firstOrNull()?.modalidad ?: ModalidadServicio.CONSULTA,
        lugarAtencion = "",
        // Sin historial de reservas ni disponibilidad calculada conectados
        // todavia a esta pantalla (Fase 4, ver docs/TASKS.md).
        ultimaAtencionTexto = null,
        proximoCupoTexto = null,
        insigniaSecundariaTexto = null,
    )
}

@HiltViewModel
class FavoritosViewModel @Inject constructor(
    private val clienteRepository: ClienteRepository,
    private val profesionalRepository: ProfesionalRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val _state = MutableStateFlow(FavoritosState())
    val state: StateFlow<FavoritosState> = _state.asStateFlow()

    init {
        cargarFavoritos()
    }

    fun onAction(action: FavoritosAction) {
        when (action) {
            is FavoritosAction.SeleccionarFiltro ->
                _state.update { it.copy(filtroSeleccionado = action.filtro) }
            is FavoritosAction.QuitarDeFavoritos -> quitarDeFavoritos(action.profesionalId)
            // Agendar con un profesional: requiere navegacion hacia la
            // feature de Reserva, que no existe todavia (docs/TASKS.md,
            // Fase 4) — se conecta cuando la feature salga de esta fase.
            is FavoritosAction.AgendarConProfesional -> Unit
        }
    }

    private fun cargarFavoritos() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        viewModelScope.launch {
            _state.update { it.copy(cargando = true) }
            when (val resultado = clienteRepository.obtenerPorId(uid)) {
                is Result.Success -> _state.update {
                    it.copy(cargando = false, favoritos = resolverFavoritos(resultado.data.favoritos))
                }
                is Result.Error -> _state.update { it.copy(cargando = false, favoritos = emptyList()) }
            }
        }
    }

    private suspend fun resolverFavoritos(idsFavoritos: List<String>): List<ProfesionalFavorito> =
        idsFavoritos.mapIndexedNotNull { index, profesionalId ->
            when (val resultado = profesionalRepository.obtenerPorId(profesionalId)) {
                is Result.Success -> resultado.data.aProfesionalFavorito(esPrincipal = index == 0)
                is Result.Error -> null
            }
        }

    private fun quitarDeFavoritos(profesionalId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        // Actualizacion optimista: se quita de la lista visible de inmediato
        // y se revierte recargando desde Firestore si la escritura falla.
        val favoritosPrevios = _state.value.favoritos
        _state.update { it.copy(favoritos = it.favoritos.filterNot { favorito -> favorito.id == profesionalId }) }

        viewModelScope.launch {
            when (clienteRepository.quitarFavorito(uid, profesionalId)) {
                is Result.Success -> Unit
                is Result.Error -> _state.update { it.copy(favoritos = favoritosPrevios) }
            }
        }
    }
}
