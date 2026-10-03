@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import com.darjnest.kinecare.core.common.data.error.ReservaError
import com.darjnest.kinecare.core.common.data.error.ResponderReservaError
import com.darjnest.kinecare.core.common.data.error.ServicioError
import com.darjnest.kinecare.core.common.data.error.UsuarioError
import com.darjnest.kinecare.core.common.data.repository.ReservaRepository
import com.darjnest.kinecare.core.common.data.repository.ServicioRepository
import com.darjnest.kinecare.core.common.data.repository.UsuarioRepository
import com.darjnest.kinecare.core.common.domain.model.Direccion
import com.darjnest.kinecare.core.common.domain.model.EstadoPago
import com.darjnest.kinecare.core.common.domain.model.EstadoReserva
import com.darjnest.kinecare.core.common.domain.model.MetodoPago
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.Pago
import com.darjnest.kinecare.core.common.domain.model.Reserva
import com.darjnest.kinecare.core.common.domain.model.RespuestaReserva
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.Servicio
import com.darjnest.kinecare.core.common.domain.model.TipoMetodoPago
import com.darjnest.kinecare.core.common.domain.model.Usuario
import com.darjnest.kinecare.core.common.result.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Jueves 2026-10-01, 09:00 en Chile (UTC-3, horario de verano). Semana del lunes 28/09. */
private val AHORA = Instant.parse("2026-10-01T12:00:00Z")

class SolicitudesDeAtencionViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val reservaRepository = mockk<ReservaRepository>()
    private val usuarioRepository = mockk<UsuarioRepository>()
    private val servicioRepository = mockk<ServicioRepository>()
    private val firebaseAuth = mockk<FirebaseAuth>()
    private val clock = object : Clock {
        override fun now() = AHORA
    }

    private val uid = "prof-1"

    init {
        val firebaseUser = mockk<FirebaseUser>()
        every { firebaseUser.uid } returns uid
        every { firebaseAuth.currentUser } returns firebaseUser
        coEvery { usuarioRepository.obtenerPorId(any()) } answers {
            Result.Success(usuario(firstArg(), "Nombre ${firstArg<String>()}"))
        }
        coEvery { servicioRepository.obtenerPorProfesional(uid) } returns Result.Success(
            listOf(servicio("srv-1", "Kinesiología deportiva"), servicio("srv-2", "Masoterapia")),
        )
    }

    private fun crearViewModel() =
        SolicitudesDeAtencionViewModel(reservaRepository, usuarioRepository, servicioRepository, firebaseAuth, clock)

    private fun conReservas(vararg reservas: Reserva) {
        coEvery { reservaRepository.obtenerPorProfesional(uid) } returns Result.Success(reservas.toList())
    }

    @Test
    fun `pendientes son solo las SOLICITADA futuras, de la mas proxima a la mas lejana`() = runTest {
        conReservas(
            reserva("lejana", AHORA + 3.days),
            reserva("confirmada", AHORA + 1.days, estado = EstadoReserva.CONFIRMADA),
            reserva("proxima", AHORA + 3.hours),
            reserva("vencida", AHORA - 1.hours),
        )

        val state = crearViewModel().state.value

        assertEquals(listOf("proxima", "lejana"), state.solicitudesPendientes.map { it.id })
        assertFalse(state.cargando)
        assertFalse(state.errorCarga)
    }

    @Test
    fun `mapea la reserva con nombres reales, honorario neto y modalidad`() = runTest {
        conReservas(
            reserva(
                "r1",
                AHORA + 3.hours,
                clienteId = "cli-9",
                servicioId = "srv-2",
                modalidad = ModalidadServicio.DOMICILIO,
                direccion = Direccion("Av. Pocuro", "2150", "Providencia", "Santiago", null, null, null),
                monto = 25_000,
                comision = 0.1,
            ),
        )

        val solicitud = crearViewModel().state.value.solicitudesPendientes.single()

        assertEquals("Nombre cli-9", solicitud.pacienteNombre)
        assertEquals("Masoterapia", solicitud.servicioNombre)
        assertEquals(22_500L, solicitud.honorarioClp)
        assertEquals(ModalidadSolicitud.A_DOMICILIO, solicitud.modalidad)
        assertEquals("A Domicilio", solicitud.modalidadTexto)
        assertEquals("Av. Pocuro 2150, Providencia", solicitud.direccion)
        assertEquals("Hoy, 12:00 hrs", solicitud.fechaHoraTexto)
        // Datos que el dominio todavia no tiene: la vista los oculta.
        assertNull(solicitud.calificacion)
        assertNull(solicitud.pacienteVerificadoTexto)
        assertNull(solicitud.motivoConsulta)
    }

    @Test
    fun `una cita dentro de 24 h es urgente y una lejana no`() = runTest {
        conReservas(reserva("pronto", AHORA + 3.hours), reserva("despues", AHORA + 2.days + 5.hours))

        val (pronto, despues) = crearViewModel().state.value.solicitudesPendientes

        assertEquals(NivelUrgenciaSolicitud.URGENTE, pronto.urgencia)
        assertEquals("Urgente • Cita en 3 h", pronto.tiempoRestanteTexto)
        assertEquals(NivelUrgenciaSolicitud.NORMAL, despues.urgencia)
        assertEquals("Cita en 2 días", despues.tiempoRestanteTexto)
    }

    @Test
    fun `la custodia solo se muestra con el pago autorizado`() = runTest {
        conReservas(
            reserva("pendiente", AHORA + 3.hours),
            reserva("pagada", AHORA + 4.hours, estadoPago = EstadoPago.AUTORIZADO),
        )

        val (pendiente, pagada) = crearViewModel().state.value.solicitudesPendientes

        assertNull(pendiente.custodiaTexto)
        assertNull(pendiente.avisoPagoTexto)
        assertEquals("Pago en custodia KineCare", pagada.custodiaTexto)
        assertTrue(pagada.avisoPagoTexto!!.isNotBlank())
    }

    @Test
    fun `si fallan las lecturas de nombres usa textos genericos sin bloquear la pantalla`() = runTest {
        coEvery { usuarioRepository.obtenerPorId(any()) } returns Result.Error(UsuarioError.DESCONOCIDO)
        coEvery { servicioRepository.obtenerPorProfesional(uid) } returns Result.Error(ServicioError.DESCONOCIDO)
        conReservas(reserva("r1", AHORA + 3.hours))

        val solicitud = crearViewModel().state.value.solicitudesPendientes.single()

        assertEquals("Paciente", solicitud.pacienteNombre)
        assertEquals("Sesión", solicitud.servicioNombre)
    }

    @Test
    fun `lee cada paciente una sola vez aunque tenga varias reservas`() = runTest {
        conReservas(
            reserva("r1", AHORA + 3.hours, clienteId = "cli-1"),
            reserva("r2", AHORA + 5.hours, clienteId = "cli-1"),
            reserva("r3", AHORA + 6.hours, clienteId = "cli-2"),
        )

        crearViewModel()

        coVerify(exactly = 1) { usuarioRepository.obtenerPorId("cli-1") }
        coVerify(exactly = 1) { usuarioRepository.obtenerPorId("cli-2") }
    }

    @Test
    fun `historial muestra las resueltas de la mas reciente a la mas antigua con su estado`() = runTest {
        conReservas(
            reserva("completada", AHORA - 2.days, estado = EstadoReserva.COMPLETADA),
            reserva("vencida", AHORA - 1.hours),
            reserva("rechazada", AHORA + 1.days, estado = EstadoReserva.RECHAZADA),
            reserva("cancelada", AHORA + 2.days, estado = EstadoReserva.CANCELADA_CLIENTE),
            reserva("confirmada", AHORA + 3.days, estado = EstadoReserva.CONFIRMADA),
        )

        val historial = crearViewModel().state.value.historial

        assertEquals(listOf("confirmada", "cancelada", "rechazada", "vencida", "completada"), historial.map { it.id })
        assertEquals(
            listOf(
                EstadoSolicitudResuelta.CONFIRMADA,
                EstadoSolicitudResuelta.CANCELADA,
                EstadoSolicitudResuelta.RECHAZADA,
                EstadoSolicitudResuelta.VENCIDA,
                EstadoSolicitudResuelta.COMPLETADA,
            ),
            historial.map { it.estado },
        )
        assertEquals("Cancelada por el paciente", historial[1].estadoTexto)
        assertEquals("Vencida sin respuesta", historial[3].estadoTexto)
    }

    @Test
    fun `cuenta solo las completadas de esta semana (lunes a domingo, hora de Chile)`() = runTest {
        conReservas(
            // Lunes 28/09 00:30 en Chile: esta semana.
            reserva("lunes", Instant.parse("2026-09-28T03:30:00Z"), estado = EstadoReserva.COMPLETADA),
            reserva("ayer", AHORA - 1.days, estado = EstadoReserva.COMPLETADA),
            // Domingo 27/09 23:30 en Chile: semana anterior aunque en UTC ya sea lunes.
            reserva("domingo", Instant.parse("2026-09-28T02:30:00Z"), estado = EstadoReserva.COMPLETADA),
            reserva("confirmada", AHORA - 2.hours, estado = EstadoReserva.CONFIRMADA),
        )

        val state = crearViewModel().state.value

        assertEquals(2, state.completadasEstaSemana)
        assertNull(state.porcentajeRespuestaATiempo)
    }

    @Test
    fun `si la carga falla marca el error y Reintentar vuelve a cargar`() = runTest {
        coEvery { reservaRepository.obtenerPorProfesional(uid) } returns Result.Error(ReservaError.SIN_INTERNET)
        val viewModel = crearViewModel()

        assertTrue(viewModel.state.value.errorCarga)
        assertFalse(viewModel.state.value.cargando)

        conReservas(reserva("r1", AHORA + 3.hours))
        viewModel.onAction(SolicitudesDeAtencionAction.Reintentar)

        assertFalse(viewModel.state.value.errorCarga)
        assertEquals(1, viewModel.state.value.solicitudesPendientes.size)
    }

    @Test
    fun `sin sesion no consulta nada`() = runTest {
        every { firebaseAuth.currentUser } returns null

        val viewModel = crearViewModel()

        coVerify(exactly = 0) { reservaRepository.obtenerPorProfesional(any()) }
        assertTrue(viewModel.state.value.solicitudesPendientes.isEmpty())
    }

    @Nested
    inner class Responder {

        @Test
        fun `aceptar mueve la solicitud al historial como confirmada y avisa`() = runTest {
            conReservas(reserva("r1", AHORA + 3.hours, clienteId = "cli-1"), reserva("r2", AHORA + 5.hours))
            coEvery { reservaRepository.responder("r1", RespuestaReserva.ACEPTAR) } returns
                Result.Success(EstadoReserva.CONFIRMADA)
            val viewModel = crearViewModel()

            viewModel.onAction(SolicitudesDeAtencionAction.AceptarSolicitud("r1"))

            val state = viewModel.state.value
            assertEquals(listOf("r2"), state.solicitudesPendientes.map { it.id })
            assertEquals(EstadoSolicitudResuelta.CONFIRMADA, state.historial.single { it.id == "r1" }.estado)
            assertEquals("Cita con Nombre cli-1 confirmada.", state.mensaje)
            assertNull(state.respondiendoId)
            // Sin recarga: el estado lo devuelve la funcion.
            coVerify(exactly = 1) { reservaRepository.obtenerPorProfesional(uid) }
        }

        @Test
        fun `rechazar pide confirmacion y no llama a la funcion hasta confirmar`() = runTest {
            conReservas(reserva("r1", AHORA + 3.hours))
            coEvery { reservaRepository.responder("r1", RespuestaReserva.RECHAZAR) } returns
                Result.Success(EstadoReserva.RECHAZADA)
            val viewModel = crearViewModel()

            viewModel.onAction(SolicitudesDeAtencionAction.RechazarSolicitud("r1"))

            assertEquals("r1", viewModel.state.value.solicitudPorRechazar?.id)
            coVerify(exactly = 0) { reservaRepository.responder(any(), any()) }

            viewModel.onAction(SolicitudesDeAtencionAction.ConfirmarRechazo)

            val state = viewModel.state.value
            assertNull(state.solicitudPorRechazar)
            assertTrue(state.solicitudesPendientes.isEmpty())
            assertEquals(EstadoSolicitudResuelta.RECHAZADA, state.historial.single().estado)
            coVerify(exactly = 1) { reservaRepository.responder("r1", RespuestaReserva.RECHAZAR) }
        }

        @Test
        fun `cancelar el rechazo cierra el dialogo sin llamar a la funcion`() = runTest {
            conReservas(reserva("r1", AHORA + 3.hours))
            val viewModel = crearViewModel()

            viewModel.onAction(SolicitudesDeAtencionAction.RechazarSolicitud("r1"))
            viewModel.onAction(SolicitudesDeAtencionAction.CancelarRechazo)

            assertNull(viewModel.state.value.solicitudPorRechazar)
            assertEquals(1, viewModel.state.value.solicitudesPendientes.size)
            coVerify(exactly = 0) { reservaRepository.responder(any(), any()) }
        }

        @Test
        fun `mientras responde ignora otra respuesta y expone el id en curso`() = runTest {
            conReservas(reserva("r1", AHORA + 3.hours), reserva("r2", AHORA + 5.hours))
            val respuesta = CompletableDeferred<Result<EstadoReserva, ResponderReservaError>>()
            coEvery { reservaRepository.responder("r1", RespuestaReserva.ACEPTAR) } coAnswers { respuesta.await() }
            val viewModel = crearViewModel()

            viewModel.onAction(SolicitudesDeAtencionAction.AceptarSolicitud("r1"))
            assertEquals("r1", viewModel.state.value.respondiendoId)

            viewModel.onAction(SolicitudesDeAtencionAction.AceptarSolicitud("r2"))
            viewModel.onAction(SolicitudesDeAtencionAction.RechazarSolicitud("r2"))
            assertNull(viewModel.state.value.solicitudPorRechazar)

            respuesta.complete(Result.Success(EstadoReserva.CONFIRMADA))

            assertNull(viewModel.state.value.respondiendoId)
            coVerify(exactly = 0) { reservaRepository.responder("r2", any()) }
        }

        @Test
        fun `sin internet avisa y la solicitud sigue pendiente sin recargar`() = runTest {
            conReservas(reserva("r1", AHORA + 3.hours))
            coEvery { reservaRepository.responder(any(), any()) } returns Result.Error(ResponderReservaError.SIN_INTERNET)
            val viewModel = crearViewModel()

            viewModel.onAction(SolicitudesDeAtencionAction.AceptarSolicitud("r1"))

            val state = viewModel.state.value
            assertEquals(1, state.solicitudesPendientes.size)
            assertEquals("Sin conexión. Revisa tu internet e inténtalo de nuevo.", state.mensaje)
            assertNull(state.respondiendoId)
            coVerify(exactly = 1) { reservaRepository.obtenerPorProfesional(uid) }
        }

        @Test
        fun `si ya fue respondida avisa y recarga para mostrar el estado real`() = runTest {
            conReservas(reserva("r1", AHORA + 3.hours))
            coEvery { reservaRepository.responder(any(), any()) } returns
                Result.Error(ResponderReservaError.RESERVA_YA_RESPONDIDA)
            val viewModel = crearViewModel()
            conReservas(reserva("r1", AHORA + 3.hours, estado = EstadoReserva.CANCELADA_CLIENTE))

            viewModel.onAction(SolicitudesDeAtencionAction.AceptarSolicitud("r1"))

            val state = viewModel.state.value
            assertEquals("Esta solicitud ya fue respondida o cancelada.", state.mensaje)
            assertTrue(state.solicitudesPendientes.isEmpty())
            assertEquals(EstadoSolicitudResuelta.CANCELADA, state.historial.single().estado)
            coVerify(exactly = 2) { reservaRepository.obtenerPorProfesional(uid) }
        }

        @Test
        fun `MensajeMostrado limpia el aviso`() = runTest {
            conReservas(reserva("r1", AHORA + 3.hours))
            coEvery { reservaRepository.responder(any(), any()) } returns Result.Success(EstadoReserva.CONFIRMADA)
            val viewModel = crearViewModel()
            viewModel.onAction(SolicitudesDeAtencionAction.AceptarSolicitud("r1"))

            viewModel.onAction(SolicitudesDeAtencionAction.MensajeMostrado)

            assertNull(viewModel.state.value.mensaje)
        }
    }

    @Nested
    inner class Textos {

        @Test
        fun `fecha y hora en hora de Chile con hoy, manana o dia abreviado`() {
            assertEquals("Hoy, 17:30 hrs", Instant.parse("2026-10-01T20:30:00Z").aFechaHoraTexto(AHORA))
            assertEquals("Mañana, 09:00 hrs", Instant.parse("2026-10-02T12:00:00Z").aFechaHoraTexto(AHORA))
            assertEquals("lun 05/10, 10:00 hrs", Instant.parse("2026-10-05T13:00:00Z").aFechaHoraTexto(AHORA))
            // 02:30 UTC del viernes sigue siendo jueves en Chile.
            assertEquals("Hoy, 23:30 hrs", Instant.parse("2026-10-02T02:30:00Z").aFechaHoraTexto(AHORA))
        }

        @Test
        fun `tiempo restante en minutos, horas o dias`() {
            assertEquals("Cita en 45 min", textoTiempoRestante(45.minutes))
            assertEquals("Cita en 3 h", textoTiempoRestante(3.hours + 59.minutes))
            assertEquals("Cita en 1 día", textoTiempoRestante(1.days + 2.hours))
            assertEquals("Cita en 3 días", textoTiempoRestante(3.days))
        }
    }
}

private fun reserva(
    id: String,
    fechaHora: Instant,
    estado: EstadoReserva = EstadoReserva.SOLICITADA,
    clienteId: String = "cli-1",
    servicioId: String = "srv-1",
    modalidad: ModalidadServicio = ModalidadServicio.CONSULTA,
    direccion: Direccion? = null,
    monto: Long = 30_000,
    comision: Double = 0.1,
    estadoPago: EstadoPago = EstadoPago.PENDIENTE,
) = Reserva(
    id = id,
    clienteId = clienteId,
    profesionalId = "prof-1",
    servicioId = servicioId,
    modalidad = modalidad,
    fechaHora = fechaHora,
    direccion = direccion,
    estado = estado,
    pago = Pago(
        id = "",
        reservaId = id,
        monto = monto,
        metodo = MetodoPago(TipoMetodoPago.TARJETA, null, ""),
        estado = estadoPago,
        idTransaccionPasarela = null,
    ),
    comisionPorcentaje = comision,
)

private fun usuario(id: String, nombre: String) = Usuario(
    id = id,
    nombre = nombre,
    rut = "11.111.111-1",
    email = "$id@test.cl",
    correoContacto = null,
    telefono = null,
    rol = RolUsuario.CLIENTE,
    fotoUrl = null,
    fechaRegistro = AHORA,
)

private fun servicio(id: String, nombre: String) = Servicio(
    id = id,
    nombre = nombre,
    descripcion = "",
    modalidad = ModalidadServicio.CONSULTA,
    duracionMinutos = 60,
    precio = 30_000,
)
