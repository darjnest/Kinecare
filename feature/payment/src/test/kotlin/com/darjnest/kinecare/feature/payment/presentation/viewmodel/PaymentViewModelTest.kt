package com.darjnest.kinecare.feature.payment.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.darjnest.kinecare.feature.payment.domain.IniciarPagoError
import com.darjnest.kinecare.feature.payment.data.repository.PagoRepository
import com.darjnest.kinecare.feature.payment.domain.IntentoPago
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.payment.presentation.navigation.ARG_MONTO_CLP
import com.darjnest.kinecare.feature.payment.presentation.navigation.ARG_RESERVA_ID
import com.darjnest.kinecare.feature.payment.presentation.navigation.ARG_TITULO
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class PaymentViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val pagoRepository = mockk<PagoRepository>()

    private val reservaId = "res-1"
    private val urlPago = "https://www.mercadopago.cl/checkout/v1/redirect?pref_id=abc"

    private fun crearViewModel(reservaIdArg: String? = reservaId): PaymentViewModel {
        val args = buildMap<String, Any> {
            if (reservaIdArg != null) put(ARG_RESERVA_ID, reservaIdArg)
            put(ARG_TITULO, "Kinesiología deportiva")
            put(ARG_MONTO_CLP, 25_000L)
        }
        return PaymentViewModel(SavedStateHandle(args), pagoRepository)
    }

    @Test
    fun `el estado inicial trae titulo y monto de la ruta, sin cargar ni error`() {
        val state = crearViewModel().state.value

        assertEquals("Kinesiología deportiva", state.titulo)
        assertEquals(25_000L, state.montoClp)
        assertFalse(state.cargando)
        assertNull(state.error)
    }

    @Test
    fun `Pagar abre la URL de pago por un evento y no deja cargando`() = runTest {
        coEvery { pagoRepository.iniciar(reservaId) } returns Result.Success(IntentoPago("pago-1", urlPago))
        val viewModel = crearViewModel()

        viewModel.events.test {
            viewModel.onAction(PaymentAction.Pagar)

            val evento = awaitItem() as PaymentEvent.AbrirPago
            assertEquals(urlPago, evento.url)
            expectNoEvents()
        }
        assertFalse(viewModel.state.value.cargando)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `el boton queda deshabilitado solo mientras carga y un segundo toque no repite la llamada`() = runTest {
        val respuesta = CompletableDeferred<Result<IntentoPago, IniciarPagoError>>()
        coEvery { pagoRepository.iniciar(reservaId) } coAnswers { respuesta.await() }
        val viewModel = crearViewModel()

        viewModel.onAction(PaymentAction.Pagar)
        assertTrue(viewModel.state.value.cargando)
        viewModel.onAction(PaymentAction.Pagar)
        coVerify(exactly = 1) { pagoRepository.iniciar(reservaId) }

        respuesta.complete(Result.Success(IntentoPago("pago-1", urlPago)))
        assertFalse(viewModel.state.value.cargando)
    }

    @Test
    fun `cada IniciarPagoError queda en el estado, sin evento y sin cargar`() = runTest {
        for (error in IniciarPagoError.entries) {
            coEvery { pagoRepository.iniciar(reservaId) } returns Result.Error(error)
            val viewModel = crearViewModel()

            viewModel.events.test {
                viewModel.onAction(PaymentAction.Pagar)

                expectNoEvents()
            }
            assertEquals(error, viewModel.state.value.error, "error $error")
            assertFalse(viewModel.state.value.cargando, "error $error")
        }
    }

    @Test
    fun `reintentar tras un error limpia el error y abre el pago`() = runTest {
        coEvery { pagoRepository.iniciar(reservaId) } returnsMany listOf(
            Result.Error(IniciarPagoError.PASARELA_NO_DISPONIBLE),
            Result.Success(IntentoPago("pago-1", urlPago)),
        )
        val viewModel = crearViewModel()

        viewModel.onAction(PaymentAction.Pagar)
        assertEquals(IniciarPagoError.PASARELA_NO_DISPONIBLE, viewModel.state.value.error)

        viewModel.events.test {
            viewModel.onAction(PaymentAction.Pagar)
            assertEquals(urlPago, (awaitItem() as PaymentEvent.AbrirPago).url)
        }
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `una URL https de otro host no se abre y se muestra como error`() = runTest {
        coEvery { pagoRepository.iniciar(reservaId) } returns
            Result.Success(IntentoPago("pago-1", "https://evil.com/?x=mercadopago.cl"))
        val viewModel = crearViewModel()

        viewModel.events.test {
            viewModel.onAction(PaymentAction.Pagar)

            expectNoEvents()
        }
        assertEquals(IniciarPagoError.DESCONOCIDO, viewModel.state.value.error)
    }

    @Test
    fun `una URL que no es https no se abre y se muestra como error`() = runTest {
        coEvery { pagoRepository.iniciar(reservaId) } returns
            Result.Success(IntentoPago("pago-1", "intent://evil#Intent;end"))
        val viewModel = crearViewModel()

        viewModel.events.test {
            viewModel.onAction(PaymentAction.Pagar)

            expectNoEvents()
        }
        assertEquals(IniciarPagoError.DESCONOCIDO, viewModel.state.value.error)
    }

    @Test
    fun `sin reservaId en la ruta no llama al backend y marca DATOS_INVALIDOS`() {
        val viewModel = crearViewModel(reservaIdArg = null)

        viewModel.onAction(PaymentAction.Pagar)

        assertEquals(IniciarPagoError.DATOS_INVALIDOS, viewModel.state.value.error)
        coVerify(exactly = 0) { pagoRepository.iniciar(any()) }
    }

    @Test
    fun `si no hay navegador se marca y el siguiente intento lo limpia`() = runTest {
        coEvery { pagoRepository.iniciar(reservaId) } returns Result.Success(IntentoPago("pago-1", urlPago))
        val viewModel = crearViewModel()

        viewModel.onAction(PaymentAction.NavegadorNoDisponible)
        assertTrue(viewModel.state.value.navegadorNoDisponible)

        viewModel.onAction(PaymentAction.Pagar)
        assertFalse(viewModel.state.value.navegadorNoDisponible)
    }

    @Test
    fun `volver atras es navegacion y no cambia el estado`() {
        val viewModel = crearViewModel()
        val antes = viewModel.state.value

        viewModel.onAction(PaymentAction.VolverAtras)

        assertEquals(antes, viewModel.state.value)
    }
}
