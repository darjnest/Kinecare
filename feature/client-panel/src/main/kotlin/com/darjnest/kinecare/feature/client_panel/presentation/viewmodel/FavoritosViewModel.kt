package com.darjnest.kinecare.feature.client_panel.presentation.viewmodel

import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Profesional guardado como favorito por el Cliente, mostrado en "Mis
 * Kinesiólogos Guardados". Modelo de presentacion reducido (no reemplaza
 * `Profesional` de dominio; cuando esta pantalla se conecte a Firestore, se
 * mapea desde el `Profesional` real y la lista `Cliente.favoritos`).
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

@HiltViewModel
class FavoritosViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(FavoritosState())
    val state: StateFlow<FavoritosState> = _state.asStateFlow()

    fun onAction(action: FavoritosAction) {
        when (action) {
            is FavoritosAction.SeleccionarFiltro ->
                _state.update { it.copy(filtroSeleccionado = action.filtro) }
            // Quitar de favoritos y agendar con un profesional: requieren
            // escritura sobre `Cliente.favoritos` y navegacion hacia la
            // feature de Reserva, ninguna conectada todavia (no hay
            // Firestore de favoritos ni de reservas conectado a esta
            // pantalla) — se conectan cuando la feature salga de esta fase
            // (docs/TASKS.md).
            is FavoritosAction.QuitarDeFavoritos,
            is FavoritosAction.AgendarConProfesional,
            -> Unit
        }
    }
}
