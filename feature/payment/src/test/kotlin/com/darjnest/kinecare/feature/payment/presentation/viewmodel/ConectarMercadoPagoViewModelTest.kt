package com.darjnest.kinecare.feature.payment.presentation.viewmodel

import app.cash.turbine.test
import com.darjnest.kinecare.feature.payment.domain.ConectarMercadoPagoError
import com.darjnest.kinecare.feature.payment.data.repository.PagoRepository
import com.darjnest.kinecare.core.common.result.Result
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

class ConectarMercadoPagoViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val pagoRepository = mockk<PagoRepository>()
    private val urlAutorizacion = "https://auth.mercadopago.cl/authorization?client_id=1&state=xyz"

    private fun crearViewModel() = ConectarMercadoPagoViewModel(pagoRepository)

    @Test
    fun `Conectar abre la URL de autorizacion por un evento`() = runTest {
        coEvery { pagoRepository.obtenerUrlConexion() } returns Result.Success(urlAutorizacion)
        val viewModel = crearViewModel()

        viewModel.events.test {
            viewModel.onAction(ConectarMercadoPagoAction.Conectar)

            assertEquals(urlAutorizacion, (awaitItem() as ConectarMercadoPagoEvent.AbrirAutorizacion).url)
            expectNoEvents()
        }
        assertFalse(viewModel.state.value.cargando)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `el boton queda deshabilitado solo mientras carga y un segundo toque no repite la llamada`() = runTest {
        val respuesta = CompletableDeferred<Result<String, ConectarMercadoPagoError>>()
        coEvery { pagoRepository.obtenerUrlConexion() } coAnswers { respuesta.await() }
        val viewModel = crearViewModel()

        viewModel.onAction(ConectarMercadoPagoAction.Conectar)
        assertTrue(viewModel.state.value.cargando)
        viewModel.onAction(ConectarMercadoPagoAction.Conectar)
        coVerify(exactly = 1) { pagoRepository.obtenerUrlConexion() }

        respuesta.complete(Result.Success(urlAutorizacion))
        assertFalse(viewModel.state.value.cargando)
    }

    @Test
    fun `cada ConectarMercadoPagoError queda en el estado y sin evento`() = runTest {
        for (error in ConectarMercadoPagoError.entries) {
            coEvery { pagoRepository.obtenerUrlConexion() } returns Result.Error(error)
            val viewModel = crearViewModel()

            viewModel.events.test {
                viewModel.onAction(ConectarMercadoPagoAction.Conectar)

                expectNoEvents()
            }
            assertEquals(error, viewModel.state.value.error, "error $error")
            assertFalse(viewModel.state.value.cargando, "error $error")
        }
    }

    @Test
    fun `una URL https de otro host no se abre y se muestra como error`() = runTest {
        coEvery { pagoRepository.obtenerUrlConexion() } returns Result.Success("https://mercadopago.cl.evil.com/authorization")
        val viewModel = crearViewModel()

        viewModel.events.test {
            viewModel.onAction(ConectarMercadoPagoAction.Conectar)

            expectNoEvents()
        }
        assertEquals(ConectarMercadoPagoError.DESCONOCIDO, viewModel.state.value.error)
    }

    @Test
    fun `una URL que no es https no se abre y se muestra como error`() = runTest {
        coEvery { pagoRepository.obtenerUrlConexion() } returns Result.Success("file:///etc/hosts")
        val viewModel = crearViewModel()

        viewModel.events.test {
            viewModel.onAction(ConectarMercadoPagoAction.Conectar)

            expectNoEvents()
        }
        assertEquals(ConectarMercadoPagoError.DESCONOCIDO, viewModel.state.value.error)
    }

    @Test
    fun `si no hay navegador se marca y el siguiente intento lo limpia`() = runTest {
        coEvery { pagoRepository.obtenerUrlConexion() } returns Result.Success(urlAutorizacion)
        val viewModel = crearViewModel()

        viewModel.onAction(ConectarMercadoPagoAction.NavegadorNoDisponible)
        assertTrue(viewModel.state.value.navegadorNoDisponible)

        viewModel.onAction(ConectarMercadoPagoAction.Conectar)
        assertFalse(viewModel.state.value.navegadorNoDisponible)
    }
}
