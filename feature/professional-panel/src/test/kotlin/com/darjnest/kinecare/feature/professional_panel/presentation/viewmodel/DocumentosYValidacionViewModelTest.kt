@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import com.darjnest.kinecare.core.common.data.error.CertificacionError
import com.darjnest.kinecare.core.common.data.repository.CertificacionRepository
import com.darjnest.kinecare.core.common.domain.model.Certificacion
import com.darjnest.kinecare.core.common.domain.model.EstadoCertificacion
import com.darjnest.kinecare.core.common.result.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class DocumentosYValidacionViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val certificacionRepository = mockk<CertificacionRepository>()
    private val firebaseAuth = mockk<FirebaseAuth>()
    private val uid = "prof-1"

    init {
        val firebaseUser = mockk<FirebaseUser>()
        every { firebaseUser.uid } returns uid
        every { firebaseAuth.currentUser } returns firebaseUser
    }

    private fun certificacion(id: String, estado: EstadoCertificacion = EstadoCertificacion.EN_REVISION, motivo: String? = null) =
        Certificacion(
            id = id,
            nombre = "Diplomado $id",
            estado = estado,
            tipoContenido = "application/pdf",
            tamanoBytes = 1_300_000,
            creadoEn = Instant.parse("2026-10-14T15:00:00Z"),
            motivoRechazo = motivo,
        )

    private fun crearViewModel() = DocumentosYValidacionViewModel(certificacionRepository, firebaseAuth)

    @Test
    fun `al inicializar lista las certificaciones subidas con fecha, tipo y tamano`() = runTest {
        coEvery { certificacionRepository.obtener(uid) } returns Result.Success(
            listOf(certificacion("a"), certificacion("b", EstadoCertificacion.RECHAZADA, motivo = "Archivo ilegible")),
        )

        val estado = crearViewModel().state.value

        assertEquals(listOf("a", "b"), estado.certificaciones.map { it.id })
        assertEquals("14 oct 2026 · PDF · 1,2 MB", estado.certificaciones.first().detalle)
        assertEquals("Archivo ilegible", estado.certificaciones.last().motivoRechazo)
        assertEquals(EstadoCertificacion.RECHAZADA, estado.certificaciones.last().estado)
    }

    @Test
    fun `si no se pueden cargar avisa y deja la lista vacia`() = runTest {
        coEvery { certificacionRepository.obtener(uid) } returns Result.Error(CertificacionError.SIN_INTERNET)

        val estado = crearViewModel().state.value

        assertEquals(emptyList<CertificacionSubida>(), estado.certificaciones)
        assertEquals("Sin conexión. Revisa tu internet e intenta de nuevo.", estado.mensaje)
    }

    @Test
    fun `elegir un archivo lo sube, avisa y recarga el listado`() = runTest {
        coEvery { certificacionRepository.obtener(uid) } returns Result.Success(emptyList()) andThen
            Result.Success(listOf(certificacion("nueva")))
        coEvery { certificacionRepository.subir(uid, "content://doc/1", null) } returns Result.Success(Unit)
        val viewModel = crearViewModel()

        viewModel.onAction(DocumentosYValidacionAction.CertificacionElegida("content://doc/1"))

        val estado = viewModel.state.value
        assertFalse(estado.subiendoCertificacion)
        assertEquals("Certificación enviada a revisión.", estado.mensaje)
        assertEquals(listOf("nueva"), estado.certificaciones.map { it.id })
        coVerify(exactly = 1) { certificacionRepository.subir(uid, "content://doc/1", null) }
    }

    @Test
    fun `un fallo al subir muestra el motivo y no recarga`() = runTest {
        coEvery { certificacionRepository.obtener(uid) } returns Result.Success(emptyList())
        coEvery { certificacionRepository.subir(uid, any(), any()) } returns Result.Error(CertificacionError.TIPO_NO_PERMITIDO)
        val viewModel = crearViewModel()

        viewModel.onAction(DocumentosYValidacionAction.CertificacionElegida("content://doc/2"))

        val estado = viewModel.state.value
        assertFalse(estado.subiendoCertificacion)
        assertEquals("Solo se aceptan archivos PDF, JPG o PNG.", estado.mensaje)
        coVerify(exactly = 1) { certificacionRepository.obtener(uid) }
    }

    @Test
    fun `formatearTamano usa MB con coma o KB`() {
        assertEquals("1,0 MB", formatearTamano(1_048_576))
        assertEquals("340 KB", formatearTamano(348_160))
        assertEquals("1 KB", formatearTamano(10))
    }
}
