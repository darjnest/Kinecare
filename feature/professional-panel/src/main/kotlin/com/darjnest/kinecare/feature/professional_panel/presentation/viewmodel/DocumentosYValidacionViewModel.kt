@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darjnest.kinecare.core.common.data.error.CertificacionError
import com.darjnest.kinecare.core.common.data.repository.CertificacionRepository
import com.darjnest.kinecare.core.common.domain.model.Certificacion
import com.darjnest.kinecare.core.common.domain.model.EstadoCertificacion
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.core.common.util.ZonaHorariaChile
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/**
 * Acreditacion oficial vigente ante la Superintendencia de Salud, mostrada
 * en el banner superior de la pantalla. Modelo de presentacion reducido
 * (no reemplaza `Insignia`/`EstadoVerificacion` de dominio; cuando esta
 * pantalla se conecte a Firestore/Cloud Functions, se mapea desde el
 * `Profesional` real y su registro RNPI).
 */
data class AcreditacionOficial(
    val numeroSis: String,
    val especialidad: String,
    val registroActivo: Boolean,
)

/** Metricas resumen del expediente (documentos validados, auditoria, nivel). */
data class ResumenDocumental(
    val documentosValidados: Int,
    val documentosTotales: Int,
    val fechaAuditoria: String,
    val auditoriaSinReparos: Boolean,
    val nivelKineCare: String,
    val nivelDescripcion: String,
)

/** Estado visual de cada documento del expediente clinico y legal. */
enum class EstadoDocumento { VERIFICADO, BIOMETRIA_OK, AL_DIA, VIGENTE }

/**
 * Documento del expediente clinico y legal del profesional (titulo, SIS,
 * cedula, antecedentes, poliza de responsabilidad civil). La verificacion
 * de identidad/documentos siempre se resuelve en Cloud Functions, nunca en
 * el cliente: esta pantalla solo lee y presenta el resultado.
 */
data class DocumentoClinico(
    val id: String,
    val nombre: String,
    val entidad: String,
    val estado: EstadoDocumento,
    val notaTexto: String? = null,
    val metadatoTexto: String? = null,
    val accionTexto: String? = null,
)

/** Certificacion subida por el profesional, lista para mostrarse en el listado. */
data class CertificacionSubida(
    val id: String,
    val nombre: String,
    val estado: EstadoCertificacion,
    /** "14 oct 2026 · PDF · 1,2 MB"; la fecha falta mientras el servidor no confirma la hora. */
    val detalle: String,
    val motivoRechazo: String?,
)

data class DocumentosYValidacionState(
    val cargando: Boolean = false,
    val acreditacion: AcreditacionOficial? = null,
    val resumen: ResumenDocumental? = null,
    val documentos: List<DocumentoClinico> = emptyList(),
    /** Certificaciones que el profesional subio, de la mas reciente a la mas antigua. */
    val certificaciones: List<CertificacionSubida> = emptyList(),
    val subiendoCertificacion: Boolean = false,
    /** Aviso puntual para un Snackbar; la vista lo consume con [DocumentosYValidacionAction.MensajeMostrado]. */
    val mensaje: String? = null,
)

sealed interface DocumentosYValidacionAction {
    data object CopiarNumeroSis : DocumentosYValidacionAction
    data object ConsultarEnSuperSalud : DocumentosYValidacionAction
    data class VerDocumento(val documentoId: String) : DocumentosYValidacionAction
    /** Abre el selector de documentos: lo resuelve el `Root` (necesita la Activity), no el ViewModel. */
    data object SubirNuevaCertificacion : DocumentosYValidacionAction
    data class CertificacionElegida(val uri: String) : DocumentosYValidacionAction
    data object MensajeMostrado : DocumentosYValidacionAction

    /** Navegacion a `:feature:verification`: la resuelve el `Root` por callback, no el ViewModel. */
    data object VerificarIdentidad : DocumentosYValidacionAction
}

private val MESES_ABREVIADOS = listOf(
    "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic",
)

internal fun CertificacionError.aMensaje(): String = when (this) {
    CertificacionError.SIN_INTERNET -> "Sin conexión. Revisa tu internet e intenta de nuevo."
    CertificacionError.SIN_PERMISO -> "No pudimos autorizar la subida. Cierra sesión, vuelve a entrar e intenta de nuevo."
    CertificacionError.ARCHIVO_INVALIDO -> "No pudimos leer ese archivo. Elige otro."
    CertificacionError.TIPO_NO_PERMITIDO -> "Solo se aceptan archivos PDF, JPG o PNG."
    CertificacionError.ARCHIVO_MUY_GRANDE -> "El archivo supera los 10 MB. Elige uno más liviano."
    CertificacionError.ALMACENAMIENTO_NO_DISPONIBLE -> "Subir documentos aún no está disponible. Estamos trabajando en ello."
    CertificacionError.DESCONOCIDO -> "No pudimos subir la certificación. Intenta de nuevo."
}

/** "1,2 MB", "340 KB". */
internal fun formatearTamano(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0)).replace('.', ',')
    else -> "${maxOf(1L, bytes / 1024)} KB"
}

private fun String.aEtiquetaTipo(): String = when (this) {
    "application/pdf" -> "PDF"
    "image/jpeg" -> "JPG"
    "image/png" -> "PNG"
    else -> ""
}

private fun Instant.aTextoFecha(): String {
    val fecha = toLocalDateTime(ZonaHorariaChile).date
    return "%02d %s %d".format(fecha.day, MESES_ABREVIADOS[fecha.month.ordinal], fecha.year)
}

internal fun Certificacion.aCertificacionSubida() = CertificacionSubida(
    id = id,
    nombre = nombre,
    estado = estado,
    detalle = listOfNotNull(
        creadoEn?.aTextoFecha(),
        tipoContenido.aEtiquetaTipo().takeIf { it.isNotEmpty() },
        formatearTamano(tamanoBytes).takeIf { tamanoBytes > 0 },
    ).joinToString(" · "),
    motivoRechazo = motivoRechazo,
)

@HiltViewModel
class DocumentosYValidacionViewModel @Inject constructor(
    private val certificacionRepository: CertificacionRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val _state = MutableStateFlow(DocumentosYValidacionState())
    val state: StateFlow<DocumentosYValidacionState> = _state.asStateFlow()

    init {
        cargarCertificaciones()
    }

    fun onAction(action: DocumentosYValidacionAction) {
        when (action) {
            // Copiar el N° SIS, abrir supersalud.gob.cl y ver/descargar un documento requieren
            // Storage/Cloud Functions y navegacion externa que aun no estan conectadas a esta
            // pantalla (docs/TASKS.md, Fase 7).
            DocumentosYValidacionAction.CopiarNumeroSis,
            DocumentosYValidacionAction.ConsultarEnSuperSalud,
            is DocumentosYValidacionAction.VerDocumento,
            // Abrir el selector y verificar identidad son del Root (Activity / NavGraph).
            DocumentosYValidacionAction.SubirNuevaCertificacion,
            DocumentosYValidacionAction.VerificarIdentidad,
            -> Unit

            is DocumentosYValidacionAction.CertificacionElegida -> subirCertificacion(action.uri)
            DocumentosYValidacionAction.MensajeMostrado -> _state.update { it.copy(mensaje = null) }
        }
    }

    private fun cargarCertificaciones() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        viewModelScope.launch {
            when (val resultado = certificacionRepository.obtener(uid)) {
                is Result.Success -> _state.update {
                    it.copy(certificaciones = resultado.data.map(Certificacion::aCertificacionSubida))
                }
                is Result.Error -> _state.update {
                    it.copy(
                        mensaje = if (resultado.error == CertificacionError.SIN_INTERNET) {
                            resultado.error.aMensaje()
                        } else {
                            "No pudimos cargar tus certificaciones."
                        },
                    )
                }
            }
        }
    }

    private fun subirCertificacion(uri: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        if (_state.value.subiendoCertificacion) return
        viewModelScope.launch {
            _state.update { it.copy(subiendoCertificacion = true) }
            when (val resultado = certificacionRepository.subir(uid, uri)) {
                is Result.Success -> {
                    _state.update { it.copy(subiendoCertificacion = false, mensaje = "Certificación enviada a revisión.") }
                    cargarCertificaciones()
                }
                is Result.Error -> _state.update {
                    it.copy(subiendoCertificacion = false, mensaje = resultado.error.aMensaje())
                }
            }
        }
    }
}
