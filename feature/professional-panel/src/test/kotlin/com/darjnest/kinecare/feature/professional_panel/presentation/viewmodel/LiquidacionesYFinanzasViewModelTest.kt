package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import app.cash.turbine.test
import com.darjnest.kinecare.core.common.data.error.MercadoPagoError
import com.darjnest.kinecare.core.common.data.repository.MercadoPagoRepository
import com.darjnest.kinecare.core.common.domain.model.EstadoMercadoPago
import com.darjnest.kinecare.core.common.result.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class LiquidacionesYFinanzasViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val mercadoPagoRepository = mockk<MercadoPagoRepository>()
    private val firebaseAuth = mockk<FirebaseAuth>()

    private val uid = "prof-1"
    private val url = "https://auth.mercadopago.cl/authorization?client_id=1"

    init {
        val firebaseUser = mockk<FirebaseUser>()
        every { firebaseUser.uid } returns uid
        every { firebaseAuth.currentUser } returns firebaseUser
    }

    private fun crearViewModel() = LiquidacionesYFinanzasViewModel(mercadoPagoRepository, firebaseAuth)

    private fun conEstado(conectado: Boolean) {
        coEvery { mercadoPagoRepository.obtenerEstado(uid) } returns
            Result.Success(if (conectado) EstadoMercadoPago(conectado = true) else EstadoMercadoPago.NoConectada)
    }

    // region carga inicial

    @Test
    fun `al inicializar con la cuenta conectada la tarjeta queda Conectada`() = runTest {
        conEstado(conectado = true)

        val viewModel = crearViewModel()

        assertEquals(CobrosMercadoPago.Conectada, viewModel.state.value.cobrosMercadoPago)
        coVerify(exactly = 1) { mercadoPagoRepository.obtenerEstado(uid) }
    }

    @Test
    fun `al inicializar sin cuenta conectada la tarjeta queda NoConectada`() = runTest {
        conEstado(conectado = false)

        val viewModel = crearViewModel()

        assertEquals(CobrosMercadoPago.NoConectada, viewModel.state.value.cobrosMercadoPago)
    }

    @Test
    fun `mientras lee el estado la tarjeta muestra Cargando`() = runTest {
        val gate = CompletableDeferred<Result<EstadoMercadoPago, MercadoPagoError>>()
        coEvery { mercadoPagoRepository.obtenerEstado(uid) } coAnswers { gate.await() }

        val viewModel = crearViewModel()

        assertEquals(CobrosMercadoPago.Cargando, viewModel.state.value.cobrosMercadoPago)
        gate.complete(Result.Success(EstadoMercadoPago.NoConectada))
        assertEquals(CobrosMercadoPago.NoConectada, viewModel.state.value.cobrosMercadoPago)
    }

    @Test
    fun `un error de lectura deja la tarjeta en Error con su motivo`() = runTest {
        coEvery { mercadoPagoRepository.obtenerEstado(uid) } returns Result.Error(MercadoPagoError.SIN_INTERNET)

        val viewModel = crearViewModel()

        assertEquals(CobrosMercadoPago.Error(MercadoPagoError.SIN_INTERNET), viewModel.state.value.cobrosMercadoPago)
    }

    @Test
    fun `sin sesion no consulta el repositorio y muestra SIN_SESION`() = runTest {
        every { firebaseAuth.currentUser } returns null

        val viewModel = crearViewModel()

        assertEquals(CobrosMercadoPago.Error(MercadoPagoError.SIN_SESION), viewModel.state.value.cobrosMercadoPago)
        coVerify(exactly = 0) { mercadoPagoRepository.obtenerEstado(any()) }
    }

    @Test
    fun `reintentar tras un error vuelve a leer y se recupera`() = runTest {
        coEvery { mercadoPagoRepository.obtenerEstado(uid) } returns Result.Error(MercadoPagoError.DESCONOCIDO)
        val viewModel = crearViewModel()
        conEstado(conectado = true)

        viewModel.onAction(LiquidacionesYFinanzasAction.ReintentarMercadoPago)

        assertEquals(CobrosMercadoPago.Conectada, viewModel.state.value.cobrosMercadoPago)
        coVerify(exactly = 2) { mercadoPagoRepository.obtenerEstado(uid) }
    }

    // endregion

    // region releer al volver a primer plano

    @Test
    fun `el primer ON_RESUME no repite la lectura del arranque`() = runTest {
        conEstado(conectado = false)
        val viewModel = crearViewModel()

        viewModel.onAction(LiquidacionesYFinanzasAction.PantallaReanudada)

        coVerify(exactly = 1) { mercadoPagoRepository.obtenerEstado(uid) }
    }

    @Test
    fun `los ON_RESUME siguientes releen el estado sin pasar por Cargando`() = runTest {
        conEstado(conectado = false)
        val viewModel = crearViewModel()
        viewModel.onAction(LiquidacionesYFinanzasAction.PantallaReanudada)
        conEstado(conectado = true)

        viewModel.state.test {
            assertEquals(CobrosMercadoPago.NoConectada, awaitItem().cobrosMercadoPago)
            viewModel.onAction(LiquidacionesYFinanzasAction.PantallaReanudada)
            // Releer no parpadea: pasa directo de NoConectada a Conectada.
            assertEquals(CobrosMercadoPago.Conectada, awaitItem().cobrosMercadoPago)
            expectNoEvents()
        }
        coVerify(exactly = 2) { mercadoPagoRepository.obtenerEstado(uid) }
    }

    @Test
    fun `releer no se hace mientras hay una operacion en curso`() = runTest {
        conEstado(conectado = false)
        val gate = CompletableDeferred<Result<String, MercadoPagoError>>()
        coEvery { mercadoPagoRepository.iniciarConexion() } coAnswers { gate.await() }
        val viewModel = crearViewModel()
        viewModel.onAction(LiquidacionesYFinanzasAction.PantallaReanudada)
        viewModel.onAction(LiquidacionesYFinanzasAction.ConectarMercadoPago)

        viewModel.onAction(LiquidacionesYFinanzasAction.PantallaReanudada)

        coVerify(exactly = 1) { mercadoPagoRepository.obtenerEstado(uid) }
    }

    // endregion

    // region conectar

    @Test
    fun `conectar pone la url en el state y la operacion deja de estar en curso`() = runTest {
        conEstado(conectado = false)
        coEvery { mercadoPagoRepository.iniciarConexion() } returns Result.Success(url)
        val viewModel = crearViewModel()

        viewModel.onAction(LiquidacionesYFinanzasAction.ConectarMercadoPago)

        val estado = viewModel.state.value
        assertEquals(url, estado.urlConexionPendiente)
        assertFalse(estado.operacionMercadoPagoEnCurso)
        assertNull(estado.errorOperacionMercadoPago)
    }

    @Test
    fun `conectar emite la url una sola vez y limpiarla la quita`() = runTest {
        conEstado(conectado = false)
        coEvery { mercadoPagoRepository.iniciarConexion() } returns Result.Success(url)
        val viewModel = crearViewModel()

        viewModel.state.test {
            skipItems(1)
            viewModel.onAction(LiquidacionesYFinanzasAction.ConectarMercadoPago)
            val urls = mutableListOf<String?>()
            urls += awaitItem().urlConexionPendiente // operacion en curso
            urls += awaitItem().urlConexionPendiente // url lista
            viewModel.onAction(LiquidacionesYFinanzasAction.UrlConexionMercadoPagoAbierta)
            urls += awaitItem().urlConexionPendiente
            expectNoEvents()
            assertEquals(listOf(null, url, null), urls)
        }
        coVerify(exactly = 1) { mercadoPagoRepository.iniciarConexion() }
    }

    @Test
    fun `con la url pendiente un segundo toque en conectar no llama otra vez`() = runTest {
        conEstado(conectado = false)
        coEvery { mercadoPagoRepository.iniciarConexion() } returns Result.Success(url)
        val viewModel = crearViewModel()

        viewModel.onAction(LiquidacionesYFinanzasAction.ConectarMercadoPago)
        viewModel.onAction(LiquidacionesYFinanzasAction.ConectarMercadoPago)

        coVerify(exactly = 1) { mercadoPagoRepository.iniciarConexion() }
        assertTrue(viewModel.state.value.mercadoPagoOcupado)
    }

    @Test
    fun `doble toque en conectar mientras espera la funcion llama una sola vez`() = runTest {
        conEstado(conectado = false)
        val gate = CompletableDeferred<Result<String, MercadoPagoError>>()
        coEvery { mercadoPagoRepository.iniciarConexion() } coAnswers { gate.await() }
        val viewModel = crearViewModel()

        viewModel.onAction(LiquidacionesYFinanzasAction.ConectarMercadoPago)
        assertTrue(viewModel.state.value.operacionMercadoPagoEnCurso)
        viewModel.onAction(LiquidacionesYFinanzasAction.ConectarMercadoPago)
        gate.complete(Result.Success(url))

        coVerify(exactly = 1) { mercadoPagoRepository.iniciarConexion() }
        assertEquals(url, viewModel.state.value.urlConexionPendiente)
    }

    @Test
    fun `un error al conectar lo expone, no deja url y permite reintentar`() = runTest {
        conEstado(conectado = false)
        coEvery { mercadoPagoRepository.iniciarConexion() } returnsMany listOf(
            Result.Error(MercadoPagoError.SIN_INTERNET),
            Result.Success(url),
        )
        val viewModel = crearViewModel()

        viewModel.onAction(LiquidacionesYFinanzasAction.ConectarMercadoPago)

        var estado = viewModel.state.value
        assertEquals(MercadoPagoError.SIN_INTERNET, estado.errorOperacionMercadoPago)
        assertNull(estado.urlConexionPendiente)
        assertFalse(estado.operacionMercadoPagoEnCurso)
        assertEquals(CobrosMercadoPago.NoConectada, estado.cobrosMercadoPago)

        viewModel.onAction(LiquidacionesYFinanzasAction.ConectarMercadoPago)

        estado = viewModel.state.value
        assertNull(estado.errorOperacionMercadoPago)
        assertEquals(url, estado.urlConexionPendiente)
    }

    @Test
    fun `si no se pudo abrir el navegador limpia la url y avisa sin quedar colgado`() = runTest {
        conEstado(conectado = false)
        coEvery { mercadoPagoRepository.iniciarConexion() } returns Result.Success(url)
        val viewModel = crearViewModel()
        viewModel.onAction(LiquidacionesYFinanzasAction.ConectarMercadoPago)

        viewModel.onAction(LiquidacionesYFinanzasAction.UrlConexionMercadoPagoNoSePudoAbrir)

        val estado = viewModel.state.value
        assertNull(estado.urlConexionPendiente)
        assertTrue(estado.errorAbrirNavegadorMercadoPago)
        assertFalse(estado.mercadoPagoOcupado)
    }

    @Test
    fun `conectar no hace nada si la cuenta ya esta conectada`() = runTest {
        conEstado(conectado = true)
        val viewModel = crearViewModel()

        viewModel.onAction(LiquidacionesYFinanzasAction.ConectarMercadoPago)

        coVerify(exactly = 0) { mercadoPagoRepository.iniciarConexion() }
    }

    @Test
    fun `conectar sin sesion expone SIN_SESION del repositorio`() = runTest {
        conEstado(conectado = false)
        coEvery { mercadoPagoRepository.iniciarConexion() } returns Result.Error(MercadoPagoError.SIN_SESION)
        val viewModel = crearViewModel()

        viewModel.onAction(LiquidacionesYFinanzasAction.ConectarMercadoPago)

        assertEquals(MercadoPagoError.SIN_SESION, viewModel.state.value.errorOperacionMercadoPago)
    }

    // endregion

    // region desconectar

    @Test
    fun `pedir desconectar abre la confirmacion sin llamar al repositorio`() = runTest {
        conEstado(conectado = true)
        val viewModel = crearViewModel()

        viewModel.onAction(LiquidacionesYFinanzasAction.PedirDesconectarMercadoPago)

        assertTrue(viewModel.state.value.confirmandoDesconexionMercadoPago)
        coVerify(exactly = 0) { mercadoPagoRepository.desconectar() }
    }

    @Test
    fun `cancelar la confirmacion no desconecta`() = runTest {
        conEstado(conectado = true)
        val viewModel = crearViewModel()
        viewModel.onAction(LiquidacionesYFinanzasAction.PedirDesconectarMercadoPago)

        viewModel.onAction(LiquidacionesYFinanzasAction.CancelarDesconectarMercadoPago)

        assertFalse(viewModel.state.value.confirmandoDesconexionMercadoPago)
        assertEquals(CobrosMercadoPago.Conectada, viewModel.state.value.cobrosMercadoPago)
        coVerify(exactly = 0) { mercadoPagoRepository.desconectar() }
    }

    @Test
    fun `no se puede pedir desconectar si la cuenta no esta conectada`() = runTest {
        conEstado(conectado = false)
        val viewModel = crearViewModel()

        viewModel.onAction(LiquidacionesYFinanzasAction.PedirDesconectarMercadoPago)

        assertFalse(viewModel.state.value.confirmandoDesconexionMercadoPago)
    }

    @Test
    fun `confirmar desconectar actualiza la tarjeta a NoConectada y cierra la confirmacion`() = runTest {
        conEstado(conectado = true)
        coEvery { mercadoPagoRepository.desconectar() } returns Result.Success(Unit)
        val viewModel = crearViewModel()
        viewModel.onAction(LiquidacionesYFinanzasAction.PedirDesconectarMercadoPago)

        viewModel.onAction(LiquidacionesYFinanzasAction.ConfirmarDesconectarMercadoPago)

        val estado = viewModel.state.value
        assertEquals(CobrosMercadoPago.NoConectada, estado.cobrosMercadoPago)
        assertFalse(estado.confirmandoDesconexionMercadoPago)
        assertFalse(estado.operacionMercadoPagoEnCurso)
        coVerify(exactly = 1) { mercadoPagoRepository.desconectar() }
    }

    @Test
    fun `si desconectar falla la cuenta sigue conectada y se expone el error`() = runTest {
        conEstado(conectado = true)
        coEvery { mercadoPagoRepository.desconectar() } returns Result.Error(MercadoPagoError.SIN_INTERNET)
        val viewModel = crearViewModel()
        viewModel.onAction(LiquidacionesYFinanzasAction.PedirDesconectarMercadoPago)

        viewModel.onAction(LiquidacionesYFinanzasAction.ConfirmarDesconectarMercadoPago)

        val estado = viewModel.state.value
        assertEquals(CobrosMercadoPago.Conectada, estado.cobrosMercadoPago)
        assertEquals(MercadoPagoError.SIN_INTERNET, estado.errorOperacionMercadoPago)
        assertFalse(estado.operacionMercadoPagoEnCurso)
        assertFalse(estado.confirmandoDesconexionMercadoPago)
    }

    @Test
    fun `doble confirmacion mientras desconecta llama una sola vez`() = runTest {
        conEstado(conectado = true)
        val gate = CompletableDeferred<Result<Unit, MercadoPagoError>>()
        coEvery { mercadoPagoRepository.desconectar() } coAnswers { gate.await() }
        val viewModel = crearViewModel()
        viewModel.onAction(LiquidacionesYFinanzasAction.PedirDesconectarMercadoPago)

        viewModel.onAction(LiquidacionesYFinanzasAction.ConfirmarDesconectarMercadoPago)
        viewModel.onAction(LiquidacionesYFinanzasAction.ConfirmarDesconectarMercadoPago)
        gate.complete(Result.Success(Unit))

        coVerify(exactly = 1) { mercadoPagoRepository.desconectar() }
        assertEquals(CobrosMercadoPago.NoConectada, viewModel.state.value.cobrosMercadoPago)
    }

    @Test
    fun `confirmar desconectar sin haberlo pedido sobre una cuenta no conectada no llama al repositorio`() = runTest {
        conEstado(conectado = false)
        val viewModel = crearViewModel()

        viewModel.onAction(LiquidacionesYFinanzasAction.ConfirmarDesconectarMercadoPago)

        coVerify(exactly = 0) { mercadoPagoRepository.desconectar() }
    }

    // endregion
}
