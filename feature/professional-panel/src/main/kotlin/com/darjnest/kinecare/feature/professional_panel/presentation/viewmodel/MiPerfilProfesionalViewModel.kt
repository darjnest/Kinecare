package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darjnest.kinecare.core.common.data.error.FotoPerfilError
import com.darjnest.kinecare.core.common.data.repository.FotoPerfilRepository
import com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository
import com.darjnest.kinecare.core.common.data.repository.UsuarioRepository
import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.domain.model.Profesional
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.TipoAtencion
import com.darjnest.kinecare.core.common.domain.model.TipoInsignia
import com.darjnest.kinecare.core.common.result.Result
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Identidad y credenciales academicas/legales del profesional, mostradas en
 * la tarjeta principal del perfil. Modelo de presentacion reducido (no
 * reemplaza `Profesional` de dominio; se mapea desde el `Profesional` real,
 * ver [aIdentidad]). Los campos que el dominio todavia no trae son nulos y
 * la vista oculta la fila correspondiente en vez de mostrar un dato falso.
 */
data class IdentidadProfesional(
    val nombre: String,
    val especialidadPrincipal: String,
    val universidad: String? = null,
    val anioTitulacion: Int? = null,
    val numeroRegistroSis: String,
    val entidadRegistro: String? = null,
    val habilitadoIsapreFonasa: Boolean? = null,
    val credencialesAlDia: Boolean,
)

/** Metricas de reputacion clinica mostradas en las 3 tarjetas de resumen. */
data class MetricasReputacion(
    val calificacion: Double,
    val totalResenas: Int,
    val atencionesCompletadas: Int? = null,
    val porcentajePuntualidad: Int? = null,
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
    /**
     * uid del profesional autenticado, una vez cargado su perfil. Es el id
     * con el que el `Root` abre el listado de resenas (`onVerResenas`).
     */
    val profesionalId: String? = null,
    /** La lectura del perfil fallo: sin perfil previo, la vista ofrece reintentar. */
    val errorCarga: Boolean = false,
    val identidad: IdentidadProfesional? = null,
    val metricas: MetricasReputacion? = null,
    val biografia: String = "",
    val compromisoKineCare: String? = null,
    val especialidades: List<Especialidad> = emptyList(),
    val certificacionAdicional: String? = null,
    val zonasAtencion: List<ZonaAtencion> = emptyList(),
    val direccionMapaTexto: String? = null,
    val resenaDestacada: ResenaDestacada? = null,
    val editandoBiografia: Boolean = false,
    val borradorBiografia: String = "",
    val guardandoBiografia: Boolean = false,
    val errorGuardarBiografia: Boolean = false,
    /** Disciplinas que ofrece (`tiposAtencion`): lo que filtra la busqueda del Cliente. */
    val tiposAtencion: Set<TipoAtencion> = emptySet(),
    val editandoTiposAtencion: Boolean = false,
    val borradorTiposAtencion: Set<TipoAtencion> = emptySet(),
    val guardandoTiposAtencion: Boolean = false,
    val errorGuardarTiposAtencion: Boolean = false,
    /** URL de la foto de perfil (`usuarios/{uid}.fotoUrl`); `null` = se muestran las iniciales. */
    val fotoUrl: String? = null,
    /** Subiendo la foto elegida (se muestra progreso sobre el avatar). */
    val subiendoFoto: Boolean = false,
    /** Mensaje del ultimo intento fallido de cambiar la foto; `null` si no hay. */
    val errorFoto: String? = null,
)

sealed interface MiPerfilProfesionalAction {
    data object VolverAtras : MiPerfilProfesionalAction
    data object Reintentar : MiPerfilProfesionalAction
    data object EditarBiografia : MiPerfilProfesionalAction
    data class CambiarBorradorBiografia(val texto: String) : MiPerfilProfesionalAction
    data object GuardarBiografia : MiPerfilProfesionalAction
    data object CancelarEdicionBiografia : MiPerfilProfesionalAction
    data object EditarTiposAtencion : MiPerfilProfesionalAction
    data class AlternarTipoAtencion(val tipo: TipoAtencion) : MiPerfilProfesionalAction
    data object GuardarTiposAtencion : MiPerfilProfesionalAction
    data object CancelarEdicionTiposAtencion : MiPerfilProfesionalAction

    /** Abrir el selector de fotos: lo resuelve el `Root` (necesita el contexto de la Activity). */
    data object CambiarFoto : MiPerfilProfesionalAction

    /** El profesional eligio una imagen en el selector ([uri] es un `content://`). */
    data class FotoElegida(val uri: String) : MiPerfilProfesionalAction
    data object EditarPerfilYCredenciales : MiPerfilProfesionalAction
    data object PrevisualizarPerfilPublico : MiPerfilProfesionalAction
    data object VerTodasLasResenas : MiPerfilProfesionalAction
}

/** Largo maximo de la biografia; lo aplica el ViewModel y la vista lo muestra como contador. */
const val LARGO_MAXIMO_BIOGRAFIA = 500

/**
 * `universidad`, `anioTitulacion`, `entidadRegistro` y `habilitadoIsapreFonasa`
 * no existen en `Profesional` (docs/DOMAIN.md) y quedan nulos.
 * `credencialesAlDia` es la insignia `CREDENCIALES` en `APROBADO`.
 */
private fun Profesional.aIdentidad(): IdentidadProfesional = IdentidadProfesional(
    nombre = perfil.nombre,
    especialidadPrincipal = especialidades.firstOrNull().orEmpty(),
    numeroRegistroSis = rnpi,
    credencialesAlDia = insignias.any {
        it.tipo == TipoInsignia.CREDENCIALES && it.estado == EstadoVerificacion.APROBADO
    },
)

@HiltViewModel
class MiPerfilProfesionalViewModel @Inject constructor(
    private val profesionalRepository: ProfesionalRepository,
    private val usuarioRepository: UsuarioRepository,
    private val fotoPerfilRepository: FotoPerfilRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val _state = MutableStateFlow(MiPerfilProfesionalState())
    val state: StateFlow<MiPerfilProfesionalState> = _state.asStateFlow()

    init {
        cargarPerfil()
    }

    fun onAction(action: MiPerfilProfesionalAction) {
        when (action) {
            MiPerfilProfesionalAction.Reintentar -> cargarPerfil()
            MiPerfilProfesionalAction.EditarBiografia ->
                _state.update {
                    it.copy(editandoBiografia = true, borradorBiografia = it.biografia, errorGuardarBiografia = false)
                }
            is MiPerfilProfesionalAction.CambiarBorradorBiografia ->
                _state.update {
                    it.copy(borradorBiografia = action.texto.take(LARGO_MAXIMO_BIOGRAFIA), errorGuardarBiografia = false)
                }
            MiPerfilProfesionalAction.CancelarEdicionBiografia ->
                _state.update { it.copy(editandoBiografia = false, errorGuardarBiografia = false) }
            MiPerfilProfesionalAction.GuardarBiografia -> guardarBiografia()
            MiPerfilProfesionalAction.EditarTiposAtencion ->
                _state.update {
                    it.copy(
                        editandoTiposAtencion = true,
                        borradorTiposAtencion = it.tiposAtencion,
                        errorGuardarTiposAtencion = false,
                    )
                }
            is MiPerfilProfesionalAction.AlternarTipoAtencion ->
                _state.update {
                    val borrador = if (action.tipo in it.borradorTiposAtencion) {
                        it.borradorTiposAtencion - action.tipo
                    } else {
                        it.borradorTiposAtencion + action.tipo
                    }
                    it.copy(borradorTiposAtencion = borrador, errorGuardarTiposAtencion = false)
                }
            MiPerfilProfesionalAction.CancelarEdicionTiposAtencion ->
                _state.update { it.copy(editandoTiposAtencion = false, errorGuardarTiposAtencion = false) }
            MiPerfilProfesionalAction.GuardarTiposAtencion -> guardarTiposAtencion()
            is MiPerfilProfesionalAction.FotoElegida -> subirFoto(action.uri)
            // Volver atras, ver todas las resenas (usa `state.profesionalId`),
            // editar credenciales y previsualizar el perfil publico son
            // navegacion: los resuelve el Root contra el NavGraph; abrir el
            // selector de fotos tambien es del Root (necesita la Activity).
            MiPerfilProfesionalAction.CambiarFoto,
            MiPerfilProfesionalAction.VolverAtras,
            MiPerfilProfesionalAction.EditarPerfilYCredenciales,
            MiPerfilProfesionalAction.PrevisualizarPerfilPublico,
            MiPerfilProfesionalAction.VerTodasLasResenas,
            -> Unit
        }
    }

    private var cargaJob: Job? = null

    private fun cargarPerfil() {
        if (cargaJob?.isActive == true) return
        val uid = firebaseAuth.currentUser?.uid ?: return
        cargaJob = viewModelScope.launch {
            _state.update { it.copy(cargando = true, errorCarga = false) }
            when (val resultado = profesionalRepository.obtenerPorId(uid)) {
                is Result.Success -> {
                    val profesional = resultado.data
                    _state.update {
                        it.copy(
                            cargando = false,
                            errorCarga = false,
                            profesionalId = uid,
                            fotoUrl = profesional.perfil.fotoUrl,
                            identidad = profesional.aIdentidad(),
                            // Atenciones completadas y puntualidad no existen
                            // en Firestore todavia (Fase 4): quedan nulas y la
                            // vista oculta esas 2 tarjetas.
                            metricas = MetricasReputacion(
                                calificacion = profesional.calificacionPromedio,
                                totalResenas = profesional.totalResenas,
                            ),
                            biografia = profesional.descripcion,
                            tiposAtencion = profesional.tiposAtencion.toSet(),
                            especialidades = profesional.especialidades.map {
                                Especialidad(nombre = it, icono = Icons.Filled.MedicalServices)
                            },
                        )
                    }
                }
                is Result.Error -> _state.update { it.copy(cargando = false, errorCarga = true) }
            }
        }
    }

    private fun subirFoto(uri: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        if (_state.value.subiendoFoto) return
        viewModelScope.launch {
            _state.update { it.copy(subiendoFoto = true, errorFoto = null) }
            when (val subida = fotoPerfilRepository.subir(uid, RolUsuario.PROFESIONAL, uri)) {
                is Result.Error -> _state.update {
                    it.copy(subiendoFoto = false, errorFoto = subida.error.aMensaje())
                }
                is Result.Success -> when (usuarioRepository.actualizarFotoUrl(uid, subida.data)) {
                    is Result.Success -> _state.update { it.copy(subiendoFoto = false, fotoUrl = subida.data) }
                    is Result.Error -> _state.update {
                        it.copy(subiendoFoto = false, errorFoto = "No pudimos guardar la foto en tu perfil. Intenta de nuevo.")
                    }
                }
            }
        }
    }

    private fun guardarBiografia() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val estado = _state.value
        if (estado.guardandoBiografia) return
        val nueva = estado.borradorBiografia.trim()
        viewModelScope.launch {
            _state.update { it.copy(guardandoBiografia = true, errorGuardarBiografia = false) }
            when (profesionalRepository.actualizarDescripcion(uid, nueva)) {
                is Result.Success -> _state.update {
                    it.copy(guardandoBiografia = false, editandoBiografia = false, biografia = nueva)
                }
                // El dialogo sigue abierto con el borrador para reintentar.
                is Result.Error -> _state.update { it.copy(guardandoBiografia = false, errorGuardarBiografia = true) }
            }
        }
    }

    private fun guardarTiposAtencion() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val estado = _state.value
        if (estado.guardandoTiposAtencion) return
        val nuevos = estado.borradorTiposAtencion
        // Sin al menos una el perfil desaparece de la busqueda y la regla lo rechaza.
        if (nuevos.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(guardandoTiposAtencion = true, errorGuardarTiposAtencion = false) }
            when (profesionalRepository.actualizarTiposAtencion(uid, nuevos)) {
                is Result.Success -> _state.update {
                    it.copy(guardandoTiposAtencion = false, editandoTiposAtencion = false, tiposAtencion = nuevos)
                }
                // El dialogo sigue abierto con el borrador para reintentar.
                is Result.Error -> _state.update {
                    it.copy(guardandoTiposAtencion = false, errorGuardarTiposAtencion = true)
                }
            }
        }
    }
}

private fun FotoPerfilError.aMensaje(): String = when (this) {
    FotoPerfilError.SIN_INTERNET -> "Sin conexión. Revisa tu internet e intenta de nuevo."
    FotoPerfilError.SIN_PERMISO -> "No pudimos autorizar el cambio de foto. Cierra sesión, vuelve a entrar e intenta de nuevo."
    FotoPerfilError.IMAGEN_INVALIDA -> "No pudimos leer esa imagen. Elige otra foto."
    FotoPerfilError.IMAGEN_MUY_GRANDE -> "La foto es demasiado pesada. Elige otra más liviana."
    FotoPerfilError.ALMACENAMIENTO_NO_DISPONIBLE -> "Cambiar la foto de perfil aún no está disponible. Estamos trabajando en ello."
    FotoPerfilError.DESCONOCIDO -> "No pudimos subir la foto. Intenta de nuevo."
}
