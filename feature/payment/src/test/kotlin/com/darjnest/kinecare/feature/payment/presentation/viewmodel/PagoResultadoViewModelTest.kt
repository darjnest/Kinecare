package com.darjnest.kinecare.feature.payment.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.darjnest.kinecare.feature.payment.domain.EstadoPagoError
import com.darjnest.kinecare.feature.payment.data.repository.PagoRepository
import com.darjnest.kinecare.core.common.domain.model.EstadoPago
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.payment.presentation.navigation.ARG_PAGO_ID
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

/**
 * El resultado nunca sale del deep link: `pagoId` solo identifica el pago y
 * cada fase viene de `PagoRepository.consultarEstado`. La espera entre
 * consultas usa el tiempo virtual de `runTest` (el `MainDispatcherExtension`
 * comparte su scheduler).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PagoResultadoViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val pagoRepository = mockk<PagoRepository>()

    private val pagoId = "pago-1"

    private fun crearViewModel(pagoIdArg: String? = pagoId): PagoResultadoViewModel {
        val args = if (pagoIdArg != null) mapOf(ARG_PAGO_ID to pagoIdArg) else emptyMap()
        return PagoResultadoViewModel(SavedStateHandle(args), pagoRepository)
    }

    private fun responde(vararg estados: EstadoPago) {
        coEvery { pagoRepository.consultarEstado(pagoId) } returnsMany estados.map { Result.Success(it) }
    }

    @Test
    fun `AUTORIZADO muestra pago aprobado con una sola consulta`() = runTest {
        responde(EstadoPago.AUTORIZADO)

        val viewModel = crearViewModel()

        assertEquals(PagoResultadoState(FasePagoResultado.APROBADO), viewModel.state.value)
        coVerify(exactly = 1) { pagoRepository.consultarEstado(pagoId) }
    }

    @Test
    fun `RECHAZADO y REEMBOLSADO se muestran sin reintentar la consulta`() = runTest {
        responde(EstadoPago.RECHAZADO)
        assertEquals(FasePagoResultado.RECHAZADO, crearViewModel().state.value.fase)

        responde(EstadoPago.REEMBOLSADO)
        assertEquals(FasePagoResultado.REEMBOLSADO, crearViewModel().state.value.fase)
    }

    @Test
    fun `mientras el pago esta PENDIENTE consulta de nuevo tras la espera hasta que se autoriza`() = runTest {
        responde(EstadoPago.PENDIENTE, EstadoPago.PENDIENTE, EstadoPago.AUTORIZADO)

        val viewModel = crearViewModel()

        assertEquals(FasePagoResultado.CONSULTANDO, viewModel.state.value.fase)
        coVerify(exactly = 1) { pagoRepository.consultarEstado(pagoId) }

        advanceTimeBy(ESPERA_ENTRE_CONSULTAS.inWholeMilliseconds - 1)
        coVerify(exactly = 1) { pagoRepository.consultarEstado(pagoId) }

        // `advanceTimeBy` no ejecuta lo agendado justo en el instante final: `runCurrent` si.
        advanceTimeBy(1)
        runCurrent()
        coVerify(exactly = 2) { pagoRepository.consultarEstado(pagoId) }
        assertEquals(FasePagoResultado.CONSULTANDO, viewModel.state.value.fase)

        advanceTimeBy(ESPERA_ENTRE_CONSULTAS.inWholeMilliseconds)
        runCurrent()
        assertEquals(FasePagoResultado.APROBADO, viewModel.state.value.fase)
        coVerify(exactly = 3) { pagoRepository.consultarEstado(pagoId) }
    }

    @Test
    fun `si sigue PENDIENTE tras todas las consultas queda en proceso sin consultar mas`() = runTest {
        coEvery { pagoRepository.consultarEstado(pagoId) } returns Result.Success(EstadoPago.PENDIENTE)
        val espera = ESPERA_ENTRE_CONSULTAS.inWholeMilliseconds
        val viewModel = crearViewModel()

        // Hay MAX consultas separadas por MAX - 1 esperas: hasta el ultimo instante sigue consultando.
        advanceTimeBy(espera * (MAX_CONSULTAS_PENDIENTE - 1) - 1)
        runCurrent()
        assertEquals(FasePagoResultado.CONSULTANDO, viewModel.state.value.fase)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(PagoResultadoState(FasePagoResultado.EN_PROCESO), viewModel.state.value)
        coVerify(exactly = MAX_CONSULTAS_PENDIENTE) { pagoRepository.consultarEstado(pagoId) }

        // Pasado el tiempo ya no hay mas consultas: el sondeo termino.
        advanceTimeBy(espera * 10)
        runCurrent()
        coVerify(exactly = MAX_CONSULTAS_PENDIENTE) { pagoRepository.consultarEstado(pagoId) }
    }

    @Test
    fun `Reintentar desde en proceso vuelve a consultar y puede aprobar`() = runTest {
        coEvery { pagoRepository.consultarEstado(pagoId) } returnsMany
            List(MAX_CONSULTAS_PENDIENTE) { Result.Success(EstadoPago.PENDIENTE) } +
            listOf(Result.Success(EstadoPago.AUTORIZADO))
        val viewModel = crearViewModel()
        advanceTimeBy(ESPERA_ENTRE_CONSULTAS.inWholeMilliseconds * MAX_CONSULTAS_PENDIENTE)
        runCurrent()
        assertEquals(FasePagoResultado.EN_PROCESO, viewModel.state.value.fase)

        viewModel.onAction(PagoResultadoAction.Reintentar)

        assertEquals(FasePagoResultado.APROBADO, viewModel.state.value.fase)
    }

    @Test
    fun `un error de la consulta lo muestra y Reintentar consulta de nuevo`() = runTest {
        coEvery { pagoRepository.consultarEstado(pagoId) } returnsMany listOf(
            Result.Error(EstadoPagoError.SIN_INTERNET),
            Result.Success(EstadoPago.AUTORIZADO),
        )

        val viewModel = crearViewModel()

        assertEquals(PagoResultadoState(FasePagoResultado.ERROR, EstadoPagoError.SIN_INTERNET), viewModel.state.value)

        viewModel.onAction(PagoResultadoAction.Reintentar)

        assertEquals(PagoResultadoState(FasePagoResultado.APROBADO), viewModel.state.value)
    }

    @Test
    fun `cada EstadoPagoError llega al estado tal cual`() = runTest {
        for (error in EstadoPagoError.entries) {
            coEvery { pagoRepository.consultarEstado(pagoId) } returns Result.Error(error)

            assertEquals(error, crearViewModel().state.value.error, "error $error")
        }
    }

    @Test
    fun `un error durante la espera de un pago PENDIENTE corta el sondeo`() = runTest {
        coEvery { pagoRepository.consultarEstado(pagoId) } returnsMany listOf(
            Result.Success(EstadoPago.PENDIENTE),
            Result.Error(EstadoPagoError.PASARELA_NO_DISPONIBLE),
        )

        val viewModel = crearViewModel()
        advanceTimeBy(ESPERA_ENTRE_CONSULTAS.inWholeMilliseconds * MAX_CONSULTAS_PENDIENTE)
        runCurrent()

        assertEquals(EstadoPagoError.PASARELA_NO_DISPONIBLE, viewModel.state.value.error)
        coVerify(exactly = 2) { pagoRepository.consultarEstado(pagoId) }
    }

    @Test
    fun `sin pagoId en el deep link no consulta y marca DATOS_INVALIDOS`() {
        val viewModel = crearViewModel(pagoIdArg = null)

        assertEquals(FasePagoResultado.ERROR, viewModel.state.value.fase)
        assertEquals(EstadoPagoError.DATOS_INVALIDOS, viewModel.state.value.error)
        coVerify(exactly = 0) { pagoRepository.consultarEstado(any()) }
    }

    @Test
    fun `una consulta en curso no se duplica con Reintentar`() = runTest {
        coEvery { pagoRepository.consultarEstado(pagoId) } returns Result.Success(EstadoPago.PENDIENTE)
        val viewModel = crearViewModel()

        viewModel.onAction(PagoResultadoAction.Reintentar)

        coVerify(exactly = 1) { pagoRepository.consultarEstado(pagoId) }
        assertNull(viewModel.state.value.error)
    }
}
