@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.client_panel.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.client_panel.data.repository.ReporteProblemaRepository
import com.darjnest.kinecare.feature.client_panel.domain.EstadoReporte
import com.darjnest.kinecare.feature.client_panel.domain.MotivoReporte
import com.darjnest.kinecare.feature.client_panel.domain.ReporteProblema
import com.darjnest.kinecare.feature.client_panel.domain.ReporteProblemaError
import com.darjnest.kinecare.feature.client_panel.presentation.navigation.ARG_PROFESIONAL_ID
import com.darjnest.kinecare.feature.client_panel.presentation.navigation.ARG_RESERVA_ID
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ReportarProblemaViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val reporteProblemaRepository = mockk<ReporteProblemaRepository>()
    private val firebaseAuth = mockk<FirebaseAuth>()

    private val reservaId = "res-1"
    private val profesionalId = "prof-1"
    private val uid = "cli-1"
    private val descripcionValida = "El profesional nunca llegó a mi casa"

    private fun iniciarSesion(uid: String? = this.uid) {
        if (uid == null) {
            every { firebaseAuth.currentUser } returns null
        } else {
            val usuario = mockk<FirebaseUser>()
            every { usuario.uid } returns uid
            every { firebaseAuth.currentUser } returns usuario
        }
    }

    private fun crearViewModel(
        reserva: String? = reservaId,
        profesional: String? = profesionalId,
    ): ReportarProblemaViewModel {
        val args = buildMap {
            if (reserva != null) put(ARG_RESERVA_ID, reserva)
            if (profesional != null) put(ARG_PROFESIONAL_ID, profesional)
        }
        return ReportarProblemaViewModel(SavedStateHandle(args), reporteProblemaRepository, firebaseAuth)
    }

    private fun sinReportePrevio() {
        coEvery { reporteProblemaRepository.obtenerPorReserva(reservaId) } returns Result.Success(null)
    }

    private fun ReportarProblemaViewModel.completarFormulario(
        motivo: MotivoReporte = MotivoReporte.PROFESIONAL_NO_LLEGO,
        descripcion: String = descripcionValida,
    ) {
        onAction(ReportarProblemaAction.SeleccionarMotivo(motivo))
        onAction(ReportarProblemaAction.CambiarDescripcion(descripcion))
    }

    @Test
    fun `al abrir comprueba si ya hay reporte y muestra el formulario vacio si no`() = runTest {
        iniciarSesion()
        sinReportePrevio()

        val state = crearViewModel().state.value

        assertFalse(state.verificando)
        assertNull(state.errorVerificacion)
        assertNull(state.reporteExistente)
        assertFalse(state.puedeEnviar)
    }

    @Test
    fun `si la reserva ya tiene reporte lo muestra de solo lectura con la fecha en hora de Chile`() = runTest {
        iniciarSesion()
        coEvery { reporteProblemaRepository.obtenerPorReserva(reservaId) } returns Result.Success(
            ReporteProblema(
                reservaId = reservaId,
                clienteId = uid,
                profesionalId = profesionalId,
                motivo = MotivoReporte.COBRO_INCORRECTO,
                descripcion = "Me cobraron dos veces",
                estado = EstadoReporte.EN_REVISION,
                // 2026-10-02T02:00Z = 01/10 23:00 en Santiago (UTC-3).
                fecha = Instant.parse("2026-10-02T02:00:00Z"),
            ),
        )

        val state = crearViewModel().state.value

        assertEquals(
            ReportePropio(
                motivo = MotivoReporte.COBRO_INCORRECTO,
                descripcion = "Me cobraron dos veces",
                estado = EstadoReporte.EN_REVISION,
                fechaTexto = "01/10/2026",
            ),
            state.reporteExistente,
        )
        assertFalse(state.recienEnviado)
        assertFalse(state.puedeEnviar)
    }

    @Test
    fun `si la comprobacion falla muestra el error y Reintentar vuelve a consultar`() = runTest {
        iniciarSesion()
        coEvery { reporteProblemaRepository.obtenerPorReserva(reservaId) } returns
            Result.Error(ReporteProblemaError.SIN_INTERNET)
        val viewModel = crearViewModel()
        assertEquals(ReporteProblemaError.SIN_INTERNET, viewModel.state.value.errorVerificacion)

        sinReportePrevio()
        viewModel.onAction(ReportarProblemaAction.Reintentar)

        assertNull(viewModel.state.value.errorVerificacion)
        coVerify(exactly = 2) { reporteProblemaRepository.obtenerPorReserva(reservaId) }
    }

    @Test
    fun `sin reservaId no consulta y muestra error`() = runTest {
        iniciarSesion()

        val state = crearViewModel(reserva = null).state.value

        assertEquals(ReporteProblemaError.DESCONOCIDO, state.errorVerificacion)
        coVerify(exactly = 0) { reporteProblemaRepository.obtenerPorReserva(any()) }
    }

    @Test
    fun `no se puede enviar sin motivo ni con descripcion de menos de 10 caracteres sin contar espacios`() = runTest {
        iniciarSesion()
        sinReportePrevio()
        val viewModel = crearViewModel()

        viewModel.onAction(ReportarProblemaAction.CambiarDescripcion(descripcionValida))
        assertFalse(viewModel.state.value.puedeEnviar)

        viewModel.completarFormulario(descripcion = "   corto      ")
        assertFalse(viewModel.state.value.descripcionValida)
        assertFalse(viewModel.state.value.puedeEnviar)

        viewModel.onAction(ReportarProblemaAction.Enviar)
        coVerify(exactly = 0) { reporteProblemaRepository.crear(any()) }

        viewModel.completarFormulario()
        assertTrue(viewModel.state.value.puedeEnviar)
    }

    @Test
    fun `la descripcion se recorta a 1000 caracteres`() = runTest {
        iniciarSesion()
        sinReportePrevio()
        val viewModel = crearViewModel()

        viewModel.onAction(ReportarProblemaAction.CambiarDescripcion("a".repeat(1_200)))

        assertEquals(LARGO_MAXIMO_DESCRIPCION, viewModel.state.value.descripcion.length)
    }

    @Test
    fun `Enviar crea el reporte con los ids, el uid y la descripcion sin espacios y muestra la confirmacion`() = runTest {
        iniciarSesion()
        sinReportePrevio()
        val enviado = slot<ReporteProblema>()
        coEvery { reporteProblemaRepository.crear(capture(enviado)) } returns Result.Success(Unit)
        val viewModel = crearViewModel()
        viewModel.completarFormulario(motivo = MotivoReporte.ATRASO, descripcion = "  Llegó una hora tarde  ")

        viewModel.onAction(ReportarProblemaAction.Enviar)

        assertEquals(reservaId, enviado.captured.reservaId)
        assertEquals(uid, enviado.captured.clienteId)
        assertEquals(profesionalId, enviado.captured.profesionalId)
        assertEquals(MotivoReporte.ATRASO, enviado.captured.motivo)
        assertEquals("Llegó una hora tarde", enviado.captured.descripcion)
        val state = viewModel.state.value
        assertFalse(state.enviando)
        assertTrue(state.recienEnviado)
        assertEquals(
            ReportePropio(
                motivo = MotivoReporte.ATRASO,
                descripcion = "Llegó una hora tarde",
                estado = EstadoReporte.ABIERTO,
                fechaTexto = null,
            ),
            state.reporteExistente,
        )
        assertFalse(state.puedeEnviar)
    }

    @Test
    fun `mientras envia no se puede volver a enviar`() = runTest {
        iniciarSesion()
        sinReportePrevio()
        val respuesta = CompletableDeferred<Result<Unit, ReporteProblemaError>>()
        coEvery { reporteProblemaRepository.crear(any()) } coAnswers { respuesta.await() }
        val viewModel = crearViewModel()
        viewModel.completarFormulario()

        viewModel.onAction(ReportarProblemaAction.Enviar)
        assertTrue(viewModel.state.value.enviando)
        viewModel.onAction(ReportarProblemaAction.Enviar)
        respuesta.complete(Result.Success(Unit))

        coVerify(exactly = 1) { reporteProblemaRepository.crear(any()) }
    }

    @Test
    fun `si el envio falla conserva el formulario y muestra el error, que se limpia al editar`() = runTest {
        iniciarSesion()
        sinReportePrevio()
        coEvery { reporteProblemaRepository.crear(any()) } returns Result.Error(ReporteProblemaError.SIN_PERMISO)
        val viewModel = crearViewModel()
        viewModel.completarFormulario()

        viewModel.onAction(ReportarProblemaAction.Enviar)

        val state = viewModel.state.value
        assertEquals(ErrorEnvioReporte.SIN_PERMISO, state.error)
        assertEquals(descripcionValida, state.descripcion)
        assertNull(state.reporteExistente)
        assertTrue(state.puedeEnviar)

        viewModel.onAction(ReportarProblemaAction.CambiarDescripcion("$descripcionValida."))
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `sin sesion no envia y muestra SIN_SESION`() = runTest {
        iniciarSesion(uid = null)
        sinReportePrevio()
        val viewModel = crearViewModel()
        viewModel.completarFormulario()

        viewModel.onAction(ReportarProblemaAction.Enviar)

        assertEquals(ErrorEnvioReporte.SIN_SESION, viewModel.state.value.error)
        coVerify(exactly = 0) { reporteProblemaRepository.crear(any()) }
    }

    @Test
    fun `sin profesionalId no envia y muestra DESCONOCIDO`() = runTest {
        iniciarSesion()
        sinReportePrevio()
        val viewModel = crearViewModel(profesional = null)
        viewModel.completarFormulario()

        viewModel.onAction(ReportarProblemaAction.Enviar)

        assertEquals(ErrorEnvioReporte.DESCONOCIDO, viewModel.state.value.error)
        coVerify(exactly = 0) { reporteProblemaRepository.crear(any()) }
    }
}
