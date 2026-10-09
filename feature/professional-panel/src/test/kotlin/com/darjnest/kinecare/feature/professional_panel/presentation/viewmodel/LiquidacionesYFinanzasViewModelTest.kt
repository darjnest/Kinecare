@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import com.darjnest.kinecare.core.common.data.error.ReservaError
import com.darjnest.kinecare.core.common.data.error.ServicioError
import com.darjnest.kinecare.core.common.data.repository.ReservaRepository
import com.darjnest.kinecare.core.common.data.repository.ServicioRepository
import com.darjnest.kinecare.core.common.domain.model.EstadoReserva
import com.darjnest.kinecare.core.common.domain.model.MetodoPago
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.Pago
import com.darjnest.kinecare.core.common.domain.model.Reserva
import com.darjnest.kinecare.core.common.domain.model.Servicio
import com.darjnest.kinecare.core.common.domain.model.TipoMetodoPago
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import com.darjnest.kinecare.core.common.domain.model.EstadoPago as EstadoPagoDominio

/** Jueves 2026-10-15, 09:00 en Chile (UTC-3, horario de verano). */
private val AHORA_FINANZAS = Instant.parse("2026-10-15T12:00:00Z")

class LiquidacionesYFinanzasViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val reservaRepository = mockk<ReservaRepository>()
    private val servicioRepository = mockk<ServicioRepository>()
    private val firebaseAuth = mockk<FirebaseAuth>()
    private val clock = object : Clock {
        override fun now() = AHORA_FINANZAS
    }
    private val uid = "prof-1"

    init {
        val firebaseUser = mockk<FirebaseUser>()
        every { firebaseUser.uid } returns uid
        every { firebaseAuth.currentUser } returns firebaseUser
        coEvery { servicioRepository.obtenerPorProfesional(uid) } returns Result.Success(
            listOf(servicioFinanzas("srv-1", "Kinesiología deportiva")),
        )
    }

    private fun crearViewModel() = LiquidacionesYFinanzasViewModel(reservaRepository, servicioRepository, firebaseAuth, clock)

    private fun conReservas(vararg reservas: Reserva) {
        coEvery { reservaRepository.obtenerPorProfesional(uid) } returns Result.Success(reservas.toList())
    }

    @Test
    fun `ingresos del mes suman solo reservas cobradas, atendidas y de este mes`() = runTest {
        conReservas(
            reservaPagada("a", AHORA_FINANZAS - 2.days, monto = 30_000),
            reservaPagada("b", AHORA_FINANZAS - 5.days, monto = 20_000, estado = EstadoReserva.COMPLETADA),
            // futura: aun no se atiende
            reservaPagada("futura", AHORA_FINANZAS + 3.days, monto = 99_000),
            // cobrada pero cancelada: no es ingreso
            reservaPagada("cancelada", AHORA_FINANZAS - 1.days, monto = 99_000, estado = EstadoReserva.CANCELADA_CLIENTE),
            // sin cobrar
            reservaPagada("sinpago", AHORA_FINANZAS - 1.days, monto = 99_000, estadoPago = EstadoPagoDominio.PENDIENTE),
            // mes anterior
            reservaPagada("septiembre", AHORA_FINANZAS - 20.days, monto = 99_000),
        )

        val resumen = crearViewModel().state.value.resumenMensual!!

        assertEquals("octubre 2026", resumen.mes)
        assertEquals(50_000L, resumen.ingresosBrutos)
        assertEquals(2, resumen.atenciones)
        assertEquals(10, resumen.comisionPlataformaPorcentaje)
    }

    @Test
    fun `promedio diario son las atenciones entre los dias transcurridos del mes`() = runTest {
        conReservas(
            reservaPagada("a", AHORA_FINANZAS - 1.days),
            reservaPagada("b", AHORA_FINANZAS - 2.days),
            reservaPagada("c", AHORA_FINANZAS - 3.days),
        )

        // 3 atenciones en 15 dias = 0.2
        assertEquals(0.2, crearViewModel().state.value.resumenMensual!!.promedioDiario, 0.0001)
    }

    @Test
    fun `variacion compara contra el mes anterior`() = runTest {
        conReservas(
            reservaPagada("oct", AHORA_FINANZAS - 1.days, monto = 15_000),
            reservaPagada("sep", AHORA_FINANZAS - 20.days, monto = 10_000),
        )

        assertEquals(50, crearViewModel().state.value.resumenMensual!!.variacionPorcentaje)
    }

    @Test
    fun `sin ingresos el mes anterior no hay variacion que mostrar`() = runTest {
        conReservas(reservaPagada("oct", AHORA_FINANZAS - 1.days))

        assertNull(crearViewModel().state.value.resumenMensual!!.variacionPorcentaje)
    }

    @Test
    fun `lo que no se puede calcular queda vacio para que la vista lo oculte`() = runTest {
        conReservas(reservaPagada("a", AHORA_FINANZAS - 1.days))

        val state = crearViewModel().state.value

        assertNull(state.resumenFinanciero)
        assertNull(state.cuentaBancaria)
        assertNull(state.resumenMensual!!.boletasEmitidas)
        assertFalse(state.siiConectado)
    }

    @Test
    fun `historial muestra honorario neto, de la mas reciente a la mas antigua`() = runTest {
        conReservas(
            reservaPagada("vieja", AHORA_FINANZAS - 10.days, monto = 20_000),
            reservaPagada("nueva", AHORA_FINANZAS - 1.days, monto = 30_000, ultimosDigitos = "4242"),
        )

        val historial = crearViewModel().state.value.historialPagos

        assertEquals(listOf("nueva", "vieja"), historial.map { it.id })
        val nueva = historial.first()
        assertEquals(27_000L, nueva.monto)
        assertEquals(EstadoPago.EXITOSA, nueva.estado)
        assertEquals("Kinesiología deportiva", nueva.titulo)
        assertEquals("14 oct", nueva.fechaTexto)
        assertEquals("Tarjeta •••• 4242 · Mercado Pago", nueva.medioTexto)
    }

    @Test
    fun `historial refleja rechazados, reembolsados y por cobrar, y omite solicitudes sin pago`() = runTest {
        conReservas(
            reservaPagada("rechazado", AHORA_FINANZAS - 1.days, estadoPago = EstadoPagoDominio.RECHAZADO),
            reservaPagada("reembolsado", AHORA_FINANZAS - 2.days, estado = EstadoReserva.CANCELADA_CLIENTE, estadoPago = EstadoPagoDominio.REEMBOLSADO),
            reservaPagada("porcobrar", AHORA_FINANZAS - 3.days, estadoPago = EstadoPagoDominio.PENDIENTE),
            reservaPagada("solicitada", AHORA_FINANZAS + 3.days, estado = EstadoReserva.SOLICITADA, estadoPago = EstadoPagoDominio.PENDIENTE),
        )

        val historial = crearViewModel().state.value.historialPagos

        assertEquals(
            listOf(EstadoPago.RECHAZADA, EstadoPago.REEMBOLSADA, EstadoPago.PENDIENTE),
            historial.map { it.estado },
        )
        assertEquals("Por cobrar", historial.last().medioTexto)
    }

    @Test
    fun `historial se limita a los pagos mas recientes`() = runTest {
        conReservas(*Array(MAX_PAGOS_HISTORIAL + 5) { reservaPagada("r$it", AHORA_FINANZAS - (it + 1).days) })

        val historial = crearViewModel().state.value.historialPagos

        assertEquals(MAX_PAGOS_HISTORIAL, historial.size)
        assertEquals("r0", historial.first().id)
    }

    @Test
    fun `si falla el catalogo de servicios los pagos usan un titulo generico`() = runTest {
        coEvery { servicioRepository.obtenerPorProfesional(uid) } returns Result.Error(ServicioError.SIN_INTERNET)
        conReservas(reservaPagada("a", AHORA_FINANZAS - 1.days))

        assertEquals("Sesión", crearViewModel().state.value.historialPagos.single().titulo)
    }

    @Test
    fun `si falla la carga avisa el error y reintentar la recupera`() = runTest {
        coEvery { reservaRepository.obtenerPorProfesional(uid) } returns Result.Error(ReservaError.SIN_INTERNET)
        val viewModel = crearViewModel()

        assertTrue(viewModel.state.value.errorCarga)
        assertNull(viewModel.state.value.resumenMensual)

        conReservas(reservaPagada("a", AHORA_FINANZAS - 1.days))
        viewModel.onAction(LiquidacionesYFinanzasAction.Reintentar)

        assertFalse(viewModel.state.value.errorCarga)
        assertEquals(1, viewModel.state.value.historialPagos.size)
        coVerify(exactly = 2) { reservaRepository.obtenerPorProfesional(uid) }
    }

    @Test
    fun `las acciones que dependen del SII avisan que llegan pronto y el aviso se consume`() = runTest {
        conReservas()
        val viewModel = crearViewModel()

        viewModel.onAction(LiquidacionesYFinanzasAction.DescargarCertificadoAnual)
        assertEquals("Disponible próximamente.", viewModel.state.value.mensaje)

        viewModel.onAction(LiquidacionesYFinanzasAction.MensajeMostrado)
        assertNull(viewModel.state.value.mensaje)
    }
}

private fun reservaPagada(
    id: String,
    fechaHora: Instant,
    monto: Long = 30_000,
    estado: EstadoReserva = EstadoReserva.CONFIRMADA,
    estadoPago: EstadoPagoDominio = EstadoPagoDominio.AUTORIZADO,
    ultimosDigitos: String? = null,
) = Reserva(
    id = id,
    clienteId = "cli-1",
    profesionalId = "prof-1",
    servicioId = "srv-1",
    modalidad = ModalidadServicio.CONSULTA,
    fechaHora = fechaHora,
    direccion = null,
    estado = estado,
    pago = Pago(
        id = "pago-$id",
        reservaId = id,
        monto = monto,
        metodo = MetodoPago(TipoMetodoPago.TARJETA, ultimosDigitos, ""),
        estado = estadoPago,
        idTransaccionPasarela = null,
    ),
    comisionPorcentaje = 0.1,
)

private fun servicioFinanzas(id: String, nombre: String) = Servicio(
    id = id,
    nombre = nombre,
    descripcion = "",
    modalidad = ModalidadServicio.CONSULTA,
    duracionMinutos = 60,
    precio = 30_000,
)
