package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

data class DocumentosYValidacionState(
    val cargando: Boolean = false,
    val acreditacion: AcreditacionOficial? = null,
    val resumen: ResumenDocumental? = null,
    val documentos: List<DocumentoClinico> = emptyList(),
)

sealed interface DocumentosYValidacionAction {
    data object CopiarNumeroSis : DocumentosYValidacionAction
    data object ConsultarEnSuperSalud : DocumentosYValidacionAction
    data class VerDocumento(val documentoId: String) : DocumentosYValidacionAction
    data object SubirNuevaCertificacion : DocumentosYValidacionAction
    data object ContactarMesaLegal : DocumentosYValidacionAction
}

@HiltViewModel
class DocumentosYValidacionViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(DocumentosYValidacionState())
    val state: StateFlow<DocumentosYValidacionState> = _state.asStateFlow()

    fun onAction(action: DocumentosYValidacionAction) {
        when (action) {
            // Copiar el N° SIS, abrir supersalud.gob.cl, ver/descargar un documento,
            // subir una nueva certificacion y contactar a la mesa legal requieren
            // Storage/Cloud Functions y navegacion externa que aun no estan
            // conectadas a esta pantalla (docs/TASKS.md, Fase 7).
            DocumentosYValidacionAction.CopiarNumeroSis,
            DocumentosYValidacionAction.ConsultarEnSuperSalud,
            is DocumentosYValidacionAction.VerDocumento,
            DocumentosYValidacionAction.SubirNuevaCertificacion,
            DocumentosYValidacionAction.ContactarMesaLegal,
            -> Unit
        }
    }
}
