package com.darjnest.kinecare.feature.payment.presentation.viewmodel

import app.cash.turbine.test
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.payment.data.repository.PagoRepository
import com.darjnest.kinecare.feature.payment.domain.CobroComision
import com.darjnest.kinecare.feature.payment.domain.PagarComisionError
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

class PagarComisionViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val pagoRepository = mockk<PagoRepository>()
    private val urlPago = "https://www.mercadopago.cl/checkout/v1/redirect?pref_id=1-abc"
    private val cobro = CobroComision(cobroId = "c1", urlPago = urlPago, monto = 6_500)

    private fun crearViewModel() = PagarComisionViewModel(pagoRepository)

    @Test
    fun `Pagar abre el Checkout de Mercado Pago por un evento`() = runTest {
        coEvery { pagoRepository.pagarComisiones() } returns Result.Success(cobro)
        val viewModel = crearViewModel()

        viewModel.events.test {
            viewModel.onAction(PagarComisionAction.Pagar)

            assertEquals(urlPago, (awaitItem() as PagarComisionEvent.AbrirPago).url)
            expectNoEvents()
        }
        assertFalse(viewModel.state.value.cargando)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `un segundo toque mientras carga no repite la llamada`() = runTest {
        val respuesta = CompletableDeferred<Result<CobroComision, PagarComisionError>>()
        coEvery { pagoRepository.pagarComisiones() } coAnswers { respuesta.await() }
        val viewModel = crearViewModel()

        viewModel.onAction(PagarComisionAction.Pagar)
        assertTrue(viewModel.state.value.cargando)
        viewModel.onAction(PagarComisionAction.Pagar)
        coVerify(exactly = 1) { pagoRepository.pagarComisiones() }

        respuesta.complete(Result.Success(cobro))
        assertFalse(viewModel.state.value.cargando)
    }

    @Test
    fun `cada PagarComisionError queda en el estado y sin evento`() = runTest {
        for (error in PagarComisionError.entries) {
            coEvery { pagoRepository.pagarComisiones() } returns Result.Error(error)
            val viewModel = crearViewModel()

            viewModel.events.test {
                viewModel.onAction(PagarComisionAction.Pagar)

                expectNoEvents()
            }
            assertEquals(error, viewModel.state.value.error, "error $error")
            assertFalse(viewModel.state.value.cargando, "error $error")
        }
    }

    @Test
    fun `una URL de otro host no se abre y se muestra como error`() = runTest {
        coEvery { pagoRepository.pagarComisiones() } returns
            Result.Success(cobro.copy(urlPago = "https://mercadopago.cl.evil.com/checkout"))
        val viewModel = crearViewModel()

        viewModel.events.test {
            viewModel.onAction(PagarComisionAction.Pagar)

            expectNoEvents()
        }
        assertEquals(PagarComisionError.DESCONOCIDO, viewModel.state.value.error)
    }

    @Test
    fun `si no hay navegador se marca y el siguiente intento lo limpia`() = runTest {
        coEvery { pagoRepository.pagarComisiones() } returns Result.Success(cobro)
        val viewModel = crearViewModel()

        viewModel.onAction(PagarComisionAction.NavegadorNoDisponible)
        assertTrue(viewModel.state.value.navegadorNoDisponible)

        viewModel.onAction(PagarComisionAction.Pagar)
        assertFalse(viewModel.state.value.navegadorNoDisponible)
    }
}
