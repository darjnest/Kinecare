package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Identidad y credenciales academicas/legales del profesional, mostradas en
 * la tarjeta principal del perfil. Modelo de presentacion reducido (no
 * reemplaza `Profesional` de dominio; cuando esta pantalla se conecte a
 * Firestore, se mapea desde el `Profesional` real).
 */
data class IdentidadProfesional(
    val nombre: String,
    val especialidadPrincipal: String,
    val universidad: String,
    val anioTitulacion: Int,
    val numeroRegistroSis: String,
    val entidadRegistro: String,
    val habilitadoIsapreFonasa: Boolean,
    val credencialesAlDia: Boolean,
)

/** Metricas de reputacion clinica mostradas en las 3 tarjetas de resumen. */
data class MetricasReputacion(
    val calificacion: Double,
    val totalResenas: Int,
    val atencionesCompletadas: Int,
    val porcentajePuntualidad: Int,
)

/** Chip de especialidad o tecnica clinica activa del profesional. */
data class Especialidad(
    val nombre: String,
    val icono: ImageVector,
)

/** Direccion fisica o cobertura de domicilio donde atiende el profesional. */
data class ZonaAtencion(
    val id: String,
    val titulo: String,
    val subtitulo: String,
    val precio: Long,
    val icono: ImageVector,
    val notaAdicional: String? = null,
)

/** Resena destacada del profesional, mostrada como muestra en el perfil. */
data class ResenaDestacada(
    val autor: String,
    val calificacion: Int,
    val comentario: String,
)

data class MiPerfilProfesionalState(
    val cargando: Boolean = false,
    val perfilPublicoActivo: Boolean = false,
    val identidad: IdentidadProfesional? = null,
    val metricas: MetricasReputacion? = null,
    val biografia: String = "",
    val compromisoKineCare: String? = null,
    val especialidades: List<Especialidad> = emptyList(),
    val certificacionAdicional: String? = null,
    val zonasAtencion: List<ZonaAtencion> = emptyList(),
    val direccionMapaTexto: String? = null,
    val resenaDestacada: ResenaDestacada? = null,
)

sealed interface MiPerfilProfesionalAction {
    data object VolverAtras : MiPerfilProfesionalAction
    data class CambiarPerfilPublico(val activo: Boolean) : MiPerfilProfesionalAction
    data object EditarBiografia : MiPerfilProfesionalAction
    data object EditarPerfilYCredenciales : MiPerfilProfesionalAction
    data object PrevisualizarPerfilPublico : MiPerfilProfesionalAction
    data object VerTodasLasResenas : MiPerfilProfesionalAction
}

@HiltViewModel
class MiPerfilProfesionalViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(MiPerfilProfesionalState())
    val state: StateFlow<MiPerfilProfesionalState> = _state.asStateFlow()

    fun onAction(action: MiPerfilProfesionalAction) {
        when (action) {
            is MiPerfilProfesionalAction.CambiarPerfilPublico ->
                _state.update { it.copy(perfilPublicoActivo = action.activo) }
            // Volver atras, editar biografia/credenciales, previsualizar el
            // perfil publico y ver todas las resenas: requieren navegacion o
            // escritura sobre el Profesional real y la feature de Resenas,
            // ninguna conectada todavia (no hay Firestore de perfil
            // profesional ni resenas conectado a esta pantalla) — se
            // conectan cuando la feature salga de esta fase (docs/TASKS.md,
            // Fase 7).
            MiPerfilProfesionalAction.VolverAtras,
            MiPerfilProfesionalAction.EditarBiografia,
            MiPerfilProfesionalAction.EditarPerfilYCredenciales,
            MiPerfilProfesionalAction.PrevisualizarPerfilPublico,
            MiPerfilProfesionalAction.VerTodasLasResenas,
            -> Unit
        }
    }
}
