package com.darjnest.kinecare.feature.verification.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.verification.data.repository.VerificacionRepository
import com.darjnest.kinecare.feature.verification.domain.EstadoSolicitudVerificacion
import com.darjnest.kinecare.feature.verification.domain.EstadoVerificacionError
import com.darjnest.kinecare.feature.verification.domain.MotivoRechazoVerificacion
import com.darjnest.kinecare.feature.verification.presentation.navigation.ARG_SOLICITUD_ID
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * El resultado nunca sale del deep link: `solicitudId` solo identifica la
 * solicitud y cada fase viene de `VerificacionRepository.consultarEstado`. La
 * espera entre consultas usa el tiempo virtual de `runTest` (el
 * `MainDispatcherExtension` comparte su scheduler).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VerificacionResultadoViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val repository = mockk<VerificacionRepository>()

    private val solicitudId = "sol-1"

    private fun crearViewModel(solicitudIdArg: String? = solicitudId): VerificacionResultadoViewModel {
        val args = if (solicitudIdArg != null) mapOf(ARG_SOLICITUD_ID to solicitudIdArg) else emptyMap()
        return VerificacionResultadoViewModel(SavedStateHandle(args), repository)
    }

    private fun resultado(
        estado: EstadoVerificacion,
        motivo: MotivoRechazoVerificacion? = null,
    ): Result<EstadoSolicitudVerificacion, EstadoVerificacionError> =
        Result.Success(EstadoSolicitudVerificacion(estado, solicitudId, motivo))

    private fun responde(vararg estados: EstadoVerificacion) {
        coEvery { repository.consultarEstado(solicitudId) } returnsMany estados.map { resultado(it) }
    }

    @Test
    fun `APROBADO muestra el resultado final con una sola consulta`() = runTest {
        responde(EstadoVerificacion.APROBADO)

        val viewModel = crearViewModel()

        assertEquals(VerificacionResultadoState(FaseVerificacionResultado.APROBADO), viewModel.state.value)
        coVerify(exactly = 1) { repository.consultarEstado(solicitudId) }
    }

    @Test
    fun `RECHAZADO conserva el motivo y no reintenta la consulta`() = runTest {
        coEvery { repository.consultarEstado(solicitudId) } returns
            resultado(EstadoVerificacion.RECHAZADO, MotivoRechazoVerificacion.RUT_NO_COINCIDE)

        val viewModel = crearViewModel()

        assertEquals(
            VerificacionResultadoState(FaseVerificacionResultado.RECHAZADO, motivo = MotivoRechazoVerificacion.RUT_NO_COINCIDE),
            viewModel.state.value,
        )
        coVerify(exactly = 1) { repository.consultarEstado(solicitudId) }
    }

    @Test
    fun `NO_SOLICITADO se muestra sin reintentar`() = runTest {
        coEvery { repository.consultarEstado(solicitudId) } returns resultado(EstadoVerificacion.NO_SOLICITADO)

        assertEquals(FaseVerificacionResultado.NO_SOLICITADO, crearViewModel().state.value.fase)
        coVerify(exactly = 1) { repository.consultarEstado(solicitudId) }
    }

    @Test
    fun `mientras la verificacion esta PENDIENTE consulta de nuevo tras la espera hasta que se aprueba`() = runTest {
        responde(EstadoVerificacion.PENDIENTE, EstadoVerificacion.PENDIENTE, EstadoVerificacion.APROBADO)

        val viewModel = crearViewModel()

        assertEquals(FaseVerificacionResultado.CONSULTANDO, viewModel.state.value.fase)
        coVerify(exactly = 1) { repository.consultarEstado(solicitudId) }

        advanceTimeBy(ESPERA_ENTRE_CONSULTAS.inWholeMilliseconds - 1)
        coVerify(exactly = 1) { repository.consultarEstado(solicitudId) }

        // `advanceTimeBy` no ejecuta lo agendado justo en el instante final: `runCurrent` si.
        advanceTimeBy(1)
        runCurrent()
        coVerify(exactly = 2) { repository.consultarEstado(solicitudId) }
        assertEquals(FaseVerificacionResultado.CONSULTANDO, viewModel.state.value.fase)

        advanceTimeBy(ESPERA_ENTRE_CONSULTAS.inWholeMilliseconds)
        runCurrent()
        assertEquals(FaseVerificacionResultado.APROBADO, viewModel.state.value.fase)
        coVerify(exactly = 3) { repository.consultarEstado(solicitudId) }
    }

    @Test
    fun `mientras sigue PENDIENTE y luego se rechaza muestra el rechazo con su motivo`() = runTest {
        coEvery { repository.consultarEstado(solicitudId) } returnsMany listOf(
            resultado(EstadoVerificacion.PENDIENTE),
            resultado(EstadoVerificacion.RECHAZADO, MotivoRechazoVerificacion.DECLINED),
        )

        val viewModel = crearViewModel()
        advanceTimeBy(ESPERA_ENTRE_CONSULTAS.inWholeMilliseconds)
        runCurrent()

        assertEquals(
            VerificacionResultadoState(FaseVerificacionResultado.RECHAZADO, motivo = MotivoRechazoVerificacion.DECLINED),
            viewModel.state.value,
        )
    }

    @Test
    fun `si sigue PENDIENTE tras todas las consultas queda en revision sin consultar mas`() = runTest {
        coEvery { repository.consultarEstado(solicitudId) } returns resultado(EstadoVerificacion.PENDIENTE)
        val espera = ESPERA_ENTRE_CONSULTAS.inWholeMilliseconds
        val viewModel = crearViewModel()

        // Hay MAX consultas separadas por MAX - 1 esperas: hasta el ultimo instante sigue consultando.
        advanceTimeBy(espera * (MAX_CONSULTAS_PENDIENTE - 1) - 1)
        runCurrent()
        assertEquals(FaseVerificacionResultado.CONSULTANDO, viewModel.state.value.fase)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(VerificacionResultadoState(FaseVerificacionResultado.EN_REVISION), viewModel.state.value)
        coVerify(exactly = MAX_CONSULTAS_PENDIENTE) { repository.consultarEstado(solicitudId) }

        // Pasado el tiempo ya no hay mas consultas: el sondeo termino.
        advanceTimeBy(espera * 10)
        runCurrent()
        coVerify(exactly = MAX_CONSULTAS_PENDIENTE) { repository.consultarEstado(solicitudId) }
    }

    @Test
    fun `Reintentar desde en revision vuelve a consultar y puede aprobar`() = runTest {
        coEvery { repository.consultarEstado(solicitudId) } returnsMany
            List(MAX_CONSULTAS_PENDIENTE) { resultado(EstadoVerificacion.PENDIENTE) } +
            listOf(resultado(EstadoVerificacion.APROBADO))
        val viewModel = crearViewModel()
        advanceTimeBy(ESPERA_ENTRE_CONSULTAS.inWholeMilliseconds * MAX_CONSULTAS_PENDIENTE)
        runCurrent()
        assertEquals(FaseVerificacionResultado.EN_REVISION, viewModel.state.value.fase)

        viewModel.onAction(VerificacionResultadoAction.Reintentar)

        assertEquals(FaseVerificacionResultado.APROBADO, viewModel.state.value.fase)
    }

    @Test
    fun `un error de la consulta lo muestra y Reintentar consulta de nuevo`() = runTest {
        coEvery { repository.consultarEstado(solicitudId) } returnsMany listOf(
            Result.Error(EstadoVerificacionError.SIN_INTERNET),
            resultado(EstadoVerificacion.APROBADO),
        )

        val viewModel = crearViewModel()

        assertEquals(
            VerificacionResultadoState(FaseVerificacionResultado.ERROR, error = EstadoVerificacionError.SIN_INTERNET),
            viewModel.state.value,
        )

        viewModel.onAction(VerificacionResultadoAction.Reintentar)

        assertEquals(VerificacionResultadoState(FaseVerificacionResultado.APROBADO), viewModel.state.value)
    }

    @ParameterizedTest
    @EnumSource(EstadoVerificacionError::class)
    fun `cada EstadoVerificacionError llega al estado tal cual`(error: EstadoVerificacionError) = runTest {
        coEvery { repository.consultarEstado(solicitudId) } returns Result.Error(error)

        assertEquals(error, crearViewModel().state.value.error)
    }

    @Test
    fun `un error durante la espera de una verificacion PENDIENTE corta el sondeo`() = runTest {
        coEvery { repository.consultarEstado(solicitudId) } returnsMany listOf(
            resultado(EstadoVerificacion.PENDIENTE),
            Result.Error(EstadoVerificacionError.DESCONOCIDO),
        )

        val viewModel = crearViewModel()
        advanceTimeBy(ESPERA_ENTRE_CONSULTAS.inWholeMilliseconds * MAX_CONSULTAS_PENDIENTE)
        runCurrent()

        assertEquals(EstadoVerificacionError.DESCONOCIDO, viewModel.state.value.error)
        coVerify(exactly = 2) { repository.consultarEstado(solicitudId) }
    }

    @Test
    fun `sin solicitudId en el deep link consulta la verificacion mas reciente (null)`() = runTest {
        coEvery { repository.consultarEstado(null) } returns resultado(EstadoVerificacion.APROBADO)

        val viewModel = crearViewModel(solicitudIdArg = null)

        assertEquals(FaseVerificacionResultado.APROBADO, viewModel.state.value.fase)
        coVerify(exactly = 1) { repository.consultarEstado(null) }
    }

    @Test
    fun `un solicitudId en blanco se trata como ausente`() = runTest {
        coEvery { repository.consultarEstado(null) } returns resultado(EstadoVerificacion.NO_SOLICITADO)

        crearViewModel(solicitudIdArg = "  ")

        coVerify(exactly = 1) { repository.consultarEstado(null) }
    }

    @Test
    fun `una consulta en curso no se duplica con Reintentar`() = runTest {
        coEvery { repository.consultarEstado(solicitudId) } returns resultado(EstadoVerificacion.PENDIENTE)
        val viewModel = crearViewModel()

        viewModel.onAction(VerificacionResultadoAction.Reintentar)

        coVerify(exactly = 1) { repository.consultarEstado(solicitudId) }
        assertNull(viewModel.state.value.error)
    }
}
