@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.booking.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.darjnest.kinecare.core.common.data.error.ClienteError
import com.darjnest.kinecare.core.common.data.error.CrearReservaError
import com.darjnest.kinecare.core.common.data.error.ProfesionalError
import com.darjnest.kinecare.core.common.data.repository.ClienteRepository
import com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository
import com.darjnest.kinecare.core.common.data.repository.ReservaRepository
import com.darjnest.kinecare.core.common.domain.model.Cliente
import com.darjnest.kinecare.core.common.domain.model.Direccion
import com.darjnest.kinecare.core.common.domain.model.Disponibilidad
import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.Profesional
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.Servicio
import com.darjnest.kinecare.core.common.domain.model.SolicitudReserva
import com.darjnest.kinecare.core.common.domain.model.Usuario
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.core.common.util.ZonaHorariaChile
import com.darjnest.kinecare.feature.booking.presentation.navigation.ARG_PROFESIONAL_ID
import com.darjnest.kinecare.feature.booking.presentation.navigation.ARG_SERVICIO_ID
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.time.Clock

/**
 * "Ahora" fijo: lunes 2026-10-05 12:00Z = 09:00 en Santiago (UTC-3 en octubre).
 * El profesional atiende los lunes de 09:00 a 13:00, asi que con la
 * anticipacion minima de 60 min el primer cupo de hoy es 10:00. Con el
 * horizonte de 14 dias los unicos dias ofrecidos son el lunes 5 y el lunes 12.
 */
class BookingViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val profesionalRepository = mockk<ProfesionalRepository>()
    private val clienteRepository = mockk<ClienteRepository>()
    private val reservaRepository = mockk<ReservaRepository>()
    private val firebaseAuth = mockk<FirebaseAuth>()

    private val profesionalId = "prof-1"
    private val uid = "cli-1"

    private val ahora: Instant = Instant.parse("2026-10-05T12:00:00Z")
    private val reloj = object : Clock {
        override fun now(): Instant = ahora
    }

    private val hoy = LocalDate(2026, 10, 5)
    private val proximoLunes = LocalDate(2026, 10, 12)

    private val domicilio = servicio("s-dom", ModalidadServicio.DOMICILIO, 60)
    private val consulta = servicio("s-con", ModalidadServicio.CONSULTA, 60)
    private val consultaLarga = servicio("s-con2", ModalidadServicio.CONSULTA, 120)
    private val pausado = servicio("s-pausado", ModalidadServicio.ONLINE, 60, activo = false)

    private val direccionGuardada = Direccion(
        calle = "Av. Providencia",
        numero = "1234",
        comuna = "Providencia",
        ciudad = "Santiago",
        lat = -33.42,
        lng = -70.61,
        indicaciones = "Depto 5B",
    )

    private fun servicio(
        id: String,
        modalidad: ModalidadServicio,
        duracion: Int,
        activo: Boolean = true,
    ) = Servicio(
        id = id,
        nombre = "Servicio $id",
        descripcion = "",
        modalidad = modalidad,
        duracionMinutos = duracion,
        precio = 30_000,
        activo = activo,
    )

    private fun usuario(id: String, nombre: String) = Usuario(
        id = id,
        nombre = nombre,
        rut = "1-9",
        email = "$id@test.cl",
        correoContacto = null,
        telefono = null,
        rol = RolUsuario.PROFESIONAL,
        fotoUrl = null,
        fechaRegistro = Instant.fromEpochMilliseconds(0),
    )

    private val turnoDeLunes = listOf(
        Disponibilidad(DayOfWeek.MONDAY, LocalTime(9, 0), LocalTime(13, 0), activo = true),
    )

    private fun profesional(
        servicios: List<Servicio> = listOf(domicilio, consulta, consultaLarga, pausado),
        disponibilidad: List<Disponibilidad> = turnoDeLunes,
    ) = Profesional(
        usuario = usuario(profesionalId, "Dra. Paula Rojas"),
        especialidades = listOf("Kinesiologia"),
        rnpi = "123",
        servicios = servicios,
        insignias = emptyList(),
        disponibilidad = disponibilidad,
        calificacionPromedio = 4.5,
        totalResenas = 10,
        descripcion = "",
        estadoVerificacionGeneral = EstadoVerificacion.PENDIENTE,
    )

    private fun cliente(direcciones: List<Direccion> = listOf(direccionGuardada)) = Cliente(
        usuario = usuario(uid, "Cliente").copy(rol = RolUsuario.CLIENTE),
        direcciones = direcciones,
        metodosPago = emptyList(),
        favoritos = emptyList(),
    )

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
        profesional: Result<Profesional, ProfesionalError> = Result.Success(profesional()),
        direcciones: Result<Cliente, ClienteError> = Result.Success(cliente()),
        conSesion: Boolean = true,
        profesionalIdArg: String? = profesionalId,
        servicioIdArg: String? = null,
    ): BookingViewModel {
        iniciarSesion(if (conSesion) uid else null)
        coEvery { profesionalRepository.obtenerPorId(any()) } returns profesional
        coEvery { clienteRepository.obtenerPorId(any()) } returns direcciones
        val args = buildMap {
            if (profesionalIdArg != null) put(ARG_PROFESIONAL_ID, profesionalIdArg)
            if (servicioIdArg != null) put(ARG_SERVICIO_ID, servicioIdArg)
        }
        return BookingViewModel(
            SavedStateHandle(args),
            profesionalRepository,
            clienteRepository,
            reservaRepository,
            firebaseAuth,
            reloj,
        )
    }

    private fun instante(fecha: LocalDate, hora: Int, minuto: Int = 0): Instant =
        LocalDateTime(fecha, LocalTime(hora, minuto)).toInstant(ZonaHorariaChile)

    private fun BookingViewModel.llenarDireccion(
        calle: String = "Los Leones",
        numero: String = "10",
        comuna: String = "Providencia",
        ciudad: String = "Santiago",
        indicaciones: String = "",
    ) {
        onAction(BookingAction.CambiarCampoDireccion(CampoDireccion.CALLE, calle))
        onAction(BookingAction.CambiarCampoDireccion(CampoDireccion.NUMERO, numero))
        onAction(BookingAction.CambiarCampoDireccion(CampoDireccion.COMUNA, comuna))
        onAction(BookingAction.CambiarCampoDireccion(CampoDireccion.CIUDAD, ciudad))
        onAction(BookingAction.CambiarCampoDireccion(CampoDireccion.INDICACIONES, indicaciones))
    }

    /** Lleva el flujo hasta REVISION con [servicio] y el horario [horario] del dia [fecha]. */
    private fun BookingViewModel.avanzarHastaRevision(
        servicio: Servicio,
        fecha: LocalDate = hoy,
        horario: Instant = instante(hoy, 11),
        indicaciones: String = "",
    ) {
        onAction(BookingAction.SeleccionarServicio(servicio.id))
        onAction(BookingAction.Continuar)
        onAction(BookingAction.SeleccionarFecha(fecha))
        onAction(BookingAction.SeleccionarHorario(horario))
        onAction(BookingAction.Continuar)
        if (servicio.modalidad == ModalidadServicio.DOMICILIO) {
            llenarDireccion(indicaciones = indicaciones)
            onAction(BookingAction.Continuar)
        }
        assertEquals(PasoReserva.REVISION, state.value.paso)
    }

    // ---------------------------------------------------------------- carga

    @Test
    fun `al cargar solo expone los servicios activos y el nombre del profesional`() = runTest {
        val viewModel = crearViewModel()

        val state = viewModel.state.value
        assertFalse(state.cargando)
        assertNull(state.errorCarga)
        assertEquals("Dra. Paula Rojas", state.profesionalNombre)
        assertEquals(listOf(domicilio, consulta, consultaLarga), state.servicios)
        assertEquals(listOf(ModalidadServicio.DOMICILIO, ModalidadServicio.CONSULTA), state.modalidades)
        coVerify(exactly = 1) { profesionalRepository.obtenerPorId(profesionalId) }
    }

    @Test
    fun `mientras carga el estado esta en cargando y sin servicios`() = runTest {
        iniciarSesion()
        val gate = CompletableDeferred<Result<Profesional, ProfesionalError>>()
        coEvery { profesionalRepository.obtenerPorId(any()) } coAnswers { gate.await() }
        coEvery { clienteRepository.obtenerPorId(any()) } returns Result.Success(cliente())
        val viewModel = BookingViewModel(
            SavedStateHandle(mapOf(ARG_PROFESIONAL_ID to profesionalId)),
            profesionalRepository,
            clienteRepository,
            reservaRepository,
            firebaseAuth,
            reloj,
        )

        assertTrue(viewModel.state.value.cargando)
        assertTrue(viewModel.state.value.servicios.isEmpty())

        gate.complete(Result.Success(profesional()))
        assertFalse(viewModel.state.value.cargando)
        assertEquals(3, viewModel.state.value.servicios.size)
    }

    @Test
    fun `cada ProfesionalError al cargar queda en errorCarga`() = runTest {
        ProfesionalError.entries.forEach { error ->
            val state = crearViewModel(profesional = Result.Error(error)).state.value

            assertEquals(error, state.errorCarga)
            assertFalse(state.cargando, "cargando colgado para $error")
            assertTrue(state.servicios.isEmpty())
        }
    }

    @Test
    fun `Reintentar vuelve a cargar y limpia el error`() = runTest {
        val viewModel = crearViewModel(profesional = Result.Error(ProfesionalError.SIN_INTERNET))
        assertEquals(ProfesionalError.SIN_INTERNET, viewModel.state.value.errorCarga)

        coEvery { profesionalRepository.obtenerPorId(any()) } returns Result.Success(profesional())
        viewModel.onAction(BookingAction.Reintentar)

        val state = viewModel.state.value
        assertNull(state.errorCarga)
        assertFalse(state.cargando)
        assertEquals(3, state.servicios.size)
        coVerify(exactly = 2) { profesionalRepository.obtenerPorId(profesionalId) }
    }

    @Test
    fun `Reintentar muestra cargando y quita el error mientras espera`() = runTest {
        val viewModel = crearViewModel(profesional = Result.Error(ProfesionalError.DESCONOCIDO))
        val gate = CompletableDeferred<Result<Profesional, ProfesionalError>>()
        coEvery { profesionalRepository.obtenerPorId(any()) } coAnswers { gate.await() }

        viewModel.onAction(BookingAction.Reintentar)

        assertTrue(viewModel.state.value.cargando)
        assertNull(viewModel.state.value.errorCarga)
        gate.complete(Result.Success(profesional()))
        assertFalse(viewModel.state.value.cargando)
    }

    @Test
    fun `sin profesionalId queda en NO_ENCONTRADO sin llamar al repositorio`() = runTest {
        listOf(null, "", "   ").forEach { id ->
            val state = crearViewModel(profesionalIdArg = id).state.value

            assertEquals(ProfesionalError.NO_ENCONTRADO, state.errorCarga)
            assertFalse(state.cargando)
        }
        coVerify(exactly = 0) { profesionalRepository.obtenerPorId(any()) }
    }

    // ------------------------------------------------------- preseleccion

    @Test
    fun `un servicioId de la ruta preselecciona servicio y modalidad y calcula los dias`() = runTest {
        val viewModel = crearViewModel(servicioIdArg = consultaLarga.id)

        val state = viewModel.state.value
        assertEquals(consultaLarga, state.servicioSeleccionado)
        assertEquals(ModalidadServicio.CONSULTA, state.modalidadSeleccionada)
        // 120 min dentro de 09:00-13:00: cupos 09:00 y 11:00. Hoy 09:00 queda
        // dentro de la anticipacion minima, asi que solo sobrevive 11:00.
        assertEquals(listOf(hoy, proximoLunes), state.dias.map { it.fecha })
        assertEquals(listOf(instante(hoy, 11)), state.dias[0].horarios)
        assertEquals(listOf(instante(proximoLunes, 9), instante(proximoLunes, 11)), state.dias[1].horarios)
        assertNull(state.fechaSeleccionada)
        assertEquals(PasoReserva.MODALIDAD, state.paso)
        assertTrue(state.puedeContinuar)
    }

    @Test
    fun `un servicio pausado o inexistente en la ruta se ignora`() = runTest {
        listOf(pausado.id, "no-existe").forEach { id ->
            val state = crearViewModel(servicioIdArg = id).state.value

            assertNull(state.servicioSeleccionado, "se preselecciono $id")
            assertNull(state.modalidadSeleccionada)
            assertTrue(state.dias.isEmpty())
            assertFalse(state.puedeContinuar)
        }
    }

    @Test
    fun `con una sola modalidad y un solo servicio queda preseleccionado`() = runTest {
        val viewModel = crearViewModel(profesional = Result.Success(profesional(servicios = listOf(domicilio, pausado))))

        val state = viewModel.state.value
        assertEquals(ModalidadServicio.DOMICILIO, state.modalidadSeleccionada)
        assertEquals(domicilio, state.servicioSeleccionado)
        assertEquals(2, state.dias.size)
    }

    @Test
    fun `con una sola modalidad y varios servicios selecciona la modalidad pero no el servicio`() = runTest {
        val viewModel = crearViewModel(profesional = Result.Success(profesional(servicios = listOf(consulta, consultaLarga))))

        val state = viewModel.state.value
        assertEquals(ModalidadServicio.CONSULTA, state.modalidadSeleccionada)
        assertNull(state.servicioSeleccionado)
        assertFalse(state.puedeContinuar)
    }

    @Test
    fun `una modalidad con un solo servicio activo lo selecciona al elegirla`() = runTest {
        val viewModel = crearViewModel()
        assertNull(viewModel.state.value.modalidadSeleccionada)

        viewModel.onAction(BookingAction.SeleccionarModalidad(ModalidadServicio.DOMICILIO))

        assertEquals(domicilio, viewModel.state.value.servicioSeleccionado)
        assertTrue(viewModel.state.value.puedeContinuar)
    }

    @Test
    fun `una modalidad con varios servicios exige elegir el servicio`() = runTest {
        val viewModel = crearViewModel()

        viewModel.onAction(BookingAction.SeleccionarModalidad(ModalidadServicio.CONSULTA))

        assertEquals(ModalidadServicio.CONSULTA, viewModel.state.value.modalidadSeleccionada)
        assertEquals(listOf(consulta, consultaLarga), viewModel.state.value.serviciosDeModalidad)
        assertNull(viewModel.state.value.servicioSeleccionado)
        assertFalse(viewModel.state.value.puedeContinuar)

        viewModel.onAction(BookingAction.SeleccionarServicio(consulta.id))
        assertEquals(consulta, viewModel.state.value.servicioSeleccionado)
        assertTrue(viewModel.state.value.puedeContinuar)
    }

    @Test
    fun `una modalidad sin servicios activos o un servicio desconocido se ignoran`() = runTest {
        val viewModel = crearViewModel()

        viewModel.onAction(BookingAction.SeleccionarModalidad(ModalidadServicio.ONLINE))
        viewModel.onAction(BookingAction.SeleccionarServicio(pausado.id))
        viewModel.onAction(BookingAction.SeleccionarServicio("no-existe"))

        assertNull(viewModel.state.value.modalidadSeleccionada)
        assertNull(viewModel.state.value.servicioSeleccionado)
    }

    @Test
    fun `cambiar de modalidad descarta el servicio de la modalidad anterior`() = runTest {
        val viewModel = crearViewModel(servicioIdArg = consulta.id)
        assertEquals(consulta, viewModel.state.value.servicioSeleccionado)

        viewModel.onAction(BookingAction.SeleccionarModalidad(ModalidadServicio.DOMICILIO))

        assertEquals(domicilio, viewModel.state.value.servicioSeleccionado)
        assertEquals(ModalidadServicio.DOMICILIO, viewModel.state.value.modalidadSeleccionada)
    }

    @Test
    fun `cambiar de servicio reinicia la fecha y el horario`() = runTest {
        val viewModel = crearViewModel()
        viewModel.onAction(BookingAction.SeleccionarServicio(consulta.id))
        viewModel.onAction(BookingAction.Continuar)
        viewModel.onAction(BookingAction.SeleccionarHorario(instante(hoy, 11)))
        assertEquals(hoy, viewModel.state.value.fechaSeleccionada)
        assertEquals(instante(hoy, 11), viewModel.state.value.horarioSeleccionado)
        viewModel.onAction(BookingAction.PasoAnterior)

        viewModel.onAction(BookingAction.SeleccionarServicio(consultaLarga.id))

        val state = viewModel.state.value
        assertEquals(consultaLarga, state.servicioSeleccionado)
        assertNull(state.fechaSeleccionada)
        assertNull(state.horarioSeleccionado)
        assertEquals(listOf(instante(hoy, 11)), state.dias.first().horarios)
    }

    @Test
    fun `volver a elegir el mismo servicio conserva la fecha y el horario`() = runTest {
        val viewModel = crearViewModel()
        viewModel.onAction(BookingAction.SeleccionarServicio(consulta.id))
        viewModel.onAction(BookingAction.Continuar)
        viewModel.onAction(BookingAction.SeleccionarHorario(instante(hoy, 11)))
        viewModel.onAction(BookingAction.PasoAnterior)

        viewModel.onAction(BookingAction.SeleccionarServicio(consulta.id))

        assertEquals(hoy, viewModel.state.value.fechaSeleccionada)
        assertEquals(instante(hoy, 11), viewModel.state.value.horarioSeleccionado)
    }

    // ------------------------------------------------------- fecha y hora

    @Test
    fun `los cupos de hoy respetan la anticipacion minima de 60 minutos`() = runTest {
        val viewModel = crearViewModel(servicioIdArg = consulta.id)

        val horariosHoy = viewModel.state.value.dias.first { it.fecha == hoy }.horarios
        assertEquals(listOf(instante(hoy, 10), instante(hoy, 11), instante(hoy, 12)), horariosHoy)
    }

    @Test
    fun `SeleccionarFecha cambia el dia y descarta el horario elegido`() = runTest {
        val viewModel = crearViewModel(servicioIdArg = consulta.id)
        viewModel.onAction(BookingAction.Continuar)
        viewModel.onAction(BookingAction.SeleccionarHorario(instante(hoy, 11)))

        viewModel.onAction(BookingAction.SeleccionarFecha(proximoLunes))

        val state = viewModel.state.value
        assertEquals(proximoLunes, state.fechaSeleccionada)
        assertNull(state.horarioSeleccionado)
        assertEquals(4, state.horariosDelDia.size)
        assertFalse(state.puedeContinuar)
    }

    @Test
    fun `SeleccionarFecha ignora una fecha que no se ofrece`() = runTest {
        val viewModel = crearViewModel(servicioIdArg = consulta.id)
        viewModel.onAction(BookingAction.Continuar)
        viewModel.onAction(BookingAction.SeleccionarHorario(instante(hoy, 11)))

        // Martes (el profesional no atiende) y un lunes fuera del horizonte de 14 dias.
        viewModel.onAction(BookingAction.SeleccionarFecha(LocalDate(2026, 10, 6)))
        viewModel.onAction(BookingAction.SeleccionarFecha(LocalDate(2026, 10, 19)))

        assertEquals(hoy, viewModel.state.value.fechaSeleccionada)
        assertEquals(instante(hoy, 11), viewModel.state.value.horarioSeleccionado)
    }

    @Test
    fun `SeleccionarHorario ignora un horario que no se ofrece`() = runTest {
        val viewModel = crearViewModel(servicioIdArg = consulta.id)
        viewModel.onAction(BookingAction.Continuar)

        // Dentro de la anticipacion minima, fuera del turno, a mitad de cupo y de otro dia.
        viewModel.onAction(BookingAction.SeleccionarHorario(instante(hoy, 9)))
        viewModel.onAction(BookingAction.SeleccionarHorario(instante(hoy, 13)))
        viewModel.onAction(BookingAction.SeleccionarHorario(instante(hoy, 10, 30)))
        viewModel.onAction(BookingAction.SeleccionarHorario(instante(proximoLunes, 10)))

        assertNull(viewModel.state.value.horarioSeleccionado)
        assertFalse(viewModel.state.value.puedeContinuar)

        viewModel.onAction(BookingAction.SeleccionarHorario(instante(hoy, 10)))
        assertEquals(instante(hoy, 10), viewModel.state.value.horarioSeleccionado)
        assertTrue(viewModel.state.value.puedeContinuar)
    }

    @Test
    fun `sin disponibilidad no hay dias y no se puede elegir horario`() = runTest {
        val viewModel = crearViewModel(
            profesional = Result.Success(profesional(disponibilidad = emptyList())),
            servicioIdArg = consulta.id,
        )

        viewModel.onAction(BookingAction.Continuar)

        val state = viewModel.state.value
        assertEquals(PasoReserva.FECHA_HORA, state.paso)
        assertTrue(state.dias.isEmpty())
        assertNull(state.fechaSeleccionada)
        assertFalse(state.puedeContinuar)
    }

    // -------------------------------------------------------------- pasos

    @Test
    fun `un servicio que no es a domicilio salta el paso de direccion`() = runTest {
        val viewModel = crearViewModel(servicioIdArg = consulta.id)

        val state = viewModel.state.value
        assertFalse(state.requiereDireccion)
        assertEquals(
            listOf(PasoReserva.MODALIDAD, PasoReserva.FECHA_HORA, PasoReserva.REVISION),
            state.pasos,
        )

        viewModel.onAction(BookingAction.Continuar)
        assertEquals(2, viewModel.state.value.numeroPaso)
        viewModel.onAction(BookingAction.SeleccionarHorario(instante(hoy, 10)))
        viewModel.onAction(BookingAction.Continuar)
        assertEquals(PasoReserva.REVISION, viewModel.state.value.paso)
        assertEquals(3, viewModel.state.value.numeroPaso)
    }

    @Test
    fun `un servicio a domicilio tiene cuatro pasos incluyendo la direccion`() = runTest {
        val viewModel = crearViewModel(servicioIdArg = domicilio.id)

        assertTrue(viewModel.state.value.requiereDireccion)
        assertEquals(PasoReserva.entries, viewModel.state.value.pasos)

        viewModel.onAction(BookingAction.Continuar)
        viewModel.onAction(BookingAction.SeleccionarHorario(instante(hoy, 10)))
        viewModel.onAction(BookingAction.Continuar)
        assertEquals(PasoReserva.DIRECCION, viewModel.state.value.paso)
        assertEquals(3, viewModel.state.value.numeroPaso)
        viewModel.llenarDireccion()
        viewModel.onAction(BookingAction.Continuar)
        assertEquals(PasoReserva.REVISION, viewModel.state.value.paso)
        assertEquals(4, viewModel.state.value.numeroPaso)
    }

    @Test
    fun `Continuar no avanza si el paso actual esta incompleto`() = runTest {
        val viewModel = crearViewModel()

        // MODALIDAD sin servicio.
        viewModel.onAction(BookingAction.Continuar)
        assertEquals(PasoReserva.MODALIDAD, viewModel.state.value.paso)

        // FECHA_HORA sin horario.
        viewModel.onAction(BookingAction.SeleccionarServicio(domicilio.id))
        viewModel.onAction(BookingAction.Continuar)
        assertEquals(PasoReserva.FECHA_HORA, viewModel.state.value.paso)
        viewModel.onAction(BookingAction.Continuar)
        assertEquals(PasoReserva.FECHA_HORA, viewModel.state.value.paso)

        // DIRECCION sin campos obligatorios.
        viewModel.onAction(BookingAction.SeleccionarHorario(instante(hoy, 10)))
        viewModel.onAction(BookingAction.Continuar)
        assertEquals(PasoReserva.DIRECCION, viewModel.state.value.paso)
        viewModel.onAction(BookingAction.Continuar)
        assertEquals(PasoReserva.DIRECCION, viewModel.state.value.paso)
    }

    @Test
    fun `al entrar a FECHA_HORA se preselecciona el primer dia disponible`() = runTest {
        val viewModel = crearViewModel(servicioIdArg = consulta.id)
        assertNull(viewModel.state.value.fechaSeleccionada)

        viewModel.onAction(BookingAction.Continuar)

        val state = viewModel.state.value
        assertEquals(PasoReserva.FECHA_HORA, state.paso)
        assertEquals(hoy, state.fechaSeleccionada)
        assertNull(state.horarioSeleccionado)
        assertEquals(3, state.horariosDelDia.size)
    }

    @Test
    fun `volver y avanzar de nuevo a FECHA_HORA conserva el dia y el horario si siguen vigentes`() = runTest {
        val viewModel = crearViewModel(servicioIdArg = consulta.id)
        viewModel.onAction(BookingAction.Continuar)
        viewModel.onAction(BookingAction.SeleccionarFecha(proximoLunes))
        viewModel.onAction(BookingAction.SeleccionarHorario(instante(proximoLunes, 10)))
        viewModel.onAction(BookingAction.PasoAnterior)

        viewModel.onAction(BookingAction.Continuar)

        assertEquals(proximoLunes, viewModel.state.value.fechaSeleccionada)
        assertEquals(instante(proximoLunes, 10), viewModel.state.value.horarioSeleccionado)
    }

    @Test
    fun `PasoAnterior vuelve un paso, saltando la direccion si no aplica`() = runTest {
        val viewModel = crearViewModel(servicioIdArg = consulta.id)
        viewModel.avanzarHastaRevision(consulta, horario = instante(hoy, 10))

        viewModel.onAction(BookingAction.PasoAnterior)
        assertEquals(PasoReserva.FECHA_HORA, viewModel.state.value.paso)
        viewModel.onAction(BookingAction.PasoAnterior)
        assertEquals(PasoReserva.MODALIDAD, viewModel.state.value.paso)
    }

    @Test
    fun `PasoAnterior desde REVISION de un servicio a domicilio vuelve a la direccion`() = runTest {
        val viewModel = crearViewModel()
        viewModel.avanzarHastaRevision(domicilio)

        viewModel.onAction(BookingAction.PasoAnterior)

        assertEquals(PasoReserva.DIRECCION, viewModel.state.value.paso)
    }

    @Test
    fun `PasoAnterior en el primer paso no hace nada`() = runTest {
        val viewModel = crearViewModel(servicioIdArg = consulta.id)
        val antes = viewModel.state.value

        viewModel.onAction(BookingAction.PasoAnterior)

        assertEquals(antes, viewModel.state.value)
        assertEquals(PasoReserva.MODALIDAD, viewModel.state.value.paso)
    }

    // --------------------------------------------------------- direcciones

    @Test
    fun `carga las direcciones guardadas del cliente autenticado`() = runTest {
        val viewModel = crearViewModel()

        assertEquals(listOf(direccionGuardada), viewModel.state.value.direccionesGuardadas)
        coVerify(exactly = 1) { clienteRepository.obtenerPorId(uid) }
    }

    @Test
    fun `si falla la carga de direcciones la lista queda vacia y no bloquea el flujo`() = runTest {
        ClienteError.entries.forEach { error ->
            val viewModel = crearViewModel(direcciones = Result.Error(error), servicioIdArg = domicilio.id)

            val state = viewModel.state.value
            assertTrue(state.direccionesGuardadas.isEmpty(), "direcciones no vacias para $error")
            assertNull(state.errorCarga)
            assertFalse(state.cargando)
            assertTrue(state.puedeContinuar)
        }
    }

    @Test
    fun `sin sesion no consulta el cliente y la lista queda vacia`() = runTest {
        val viewModel = crearViewModel(conSesion = false, servicioIdArg = domicilio.id)

        assertTrue(viewModel.state.value.direccionesGuardadas.isEmpty())
        assertNull(viewModel.state.value.errorCarga)
        assertTrue(viewModel.state.value.puedeContinuar)
        coVerify(exactly = 0) { clienteRepository.obtenerPorId(any()) }
    }

    @Test
    fun `UsarDireccionGuardada llena el formulario incluyendo coordenadas e indicaciones`() = runTest {
        val viewModel = crearViewModel()

        viewModel.onAction(BookingAction.UsarDireccionGuardada(direccionGuardada))

        assertEquals(
            FormularioDireccion(
                calle = "Av. Providencia",
                numero = "1234",
                comuna = "Providencia",
                ciudad = "Santiago",
                indicaciones = "Depto 5B",
                lat = -33.42,
                lng = -70.61,
            ),
            viewModel.state.value.direccion,
        )
        assertTrue(viewModel.state.value.direccion.esValido)
    }

    @Test
    fun `UsarDireccionGuardada sin indicaciones deja el campo vacio`() = runTest {
        val viewModel = crearViewModel()

        viewModel.onAction(BookingAction.UsarDireccionGuardada(direccionGuardada.copy(indicaciones = null)))

        assertEquals("", viewModel.state.value.direccion.indicaciones)
    }

    @Test
    fun `editar un campo de la direccion guardada descarta las coordenadas`() = runTest {
        listOf(CampoDireccion.CALLE, CampoDireccion.NUMERO, CampoDireccion.COMUNA, CampoDireccion.CIUDAD)
            .forEach { campo ->
                val viewModel = crearViewModel()
                viewModel.onAction(BookingAction.UsarDireccionGuardada(direccionGuardada))

                viewModel.onAction(BookingAction.CambiarCampoDireccion(campo, "Otro"))

                val direccion = viewModel.state.value.direccion
                assertNull(direccion.lat, "lat conservada tras editar $campo")
                assertNull(direccion.lng, "lng conservada tras editar $campo")
            }
    }

    @Test
    fun `editar las indicaciones conserva las coordenadas`() = runTest {
        val viewModel = crearViewModel()
        viewModel.onAction(BookingAction.UsarDireccionGuardada(direccionGuardada))

        viewModel.onAction(BookingAction.CambiarCampoDireccion(CampoDireccion.INDICACIONES, "Tocar timbre"))

        val direccion = viewModel.state.value.direccion
        assertEquals("Tocar timbre", direccion.indicaciones)
        assertEquals(-33.42, direccion.lat)
        assertEquals(-70.61, direccion.lng)
    }

    @Test
    fun `calle numero y comuna son obligatorios y la ciudad no`() = runTest {
        val viewModel = crearViewModel()
        assertFalse(viewModel.state.value.direccion.esValido)

        viewModel.onAction(BookingAction.CambiarCampoDireccion(CampoDireccion.CALLE, "Los Leones"))
        viewModel.onAction(BookingAction.CambiarCampoDireccion(CampoDireccion.NUMERO, "10"))
        assertFalse(viewModel.state.value.direccion.esValido)

        viewModel.onAction(BookingAction.CambiarCampoDireccion(CampoDireccion.COMUNA, "   "))
        assertFalse(viewModel.state.value.direccion.esValido)

        viewModel.onAction(BookingAction.CambiarCampoDireccion(CampoDireccion.COMUNA, "Providencia"))
        assertTrue(viewModel.state.value.direccion.esValido)
        assertEquals("", viewModel.state.value.direccion.ciudad)
    }

    @Test
    fun `los campos de la direccion se recortan a su largo maximo`() = runTest {
        val viewModel = crearViewModel()

        viewModel.onAction(
            BookingAction.CambiarCampoDireccion(CampoDireccion.CALLE, "x".repeat(LARGO_MAXIMO_CAMPO_DIRECCION + 20)),
        )
        viewModel.onAction(
            BookingAction.CambiarCampoDireccion(CampoDireccion.INDICACIONES, "y".repeat(LARGO_MAXIMO_INDICACIONES + 20)),
        )

        assertEquals(LARGO_MAXIMO_CAMPO_DIRECCION, viewModel.state.value.direccion.calle.length)
        assertEquals(LARGO_MAXIMO_INDICACIONES, viewModel.state.value.direccion.indicaciones.length)
    }

    // ----------------------------------------------------------- confirmar

    @Test
    fun `Confirmar a domicilio envia la direccion recortada y marca la reserva creada`() = runTest {
        val capturada = slot<SolicitudReserva>()
        coEvery { reservaRepository.crear(capture(capturada)) } returns Result.Success("res-99")
        val viewModel = crearViewModel()
        viewModel.onAction(BookingAction.SeleccionarServicio(domicilio.id))
        viewModel.onAction(BookingAction.Continuar)
        viewModel.onAction(BookingAction.SeleccionarHorario(instante(hoy, 11)))
        viewModel.onAction(BookingAction.Continuar)
        viewModel.llenarDireccion(
            calle = "  Los Leones ",
            numero = " 10 ",
            comuna = " Providencia ",
            ciudad = " Santiago ",
            indicaciones = "  Depto 5B ",
        )
        viewModel.onAction(BookingAction.Continuar)

        viewModel.onAction(BookingAction.Confirmar)

        assertEquals(
            SolicitudReserva(
                profesionalId = profesionalId,
                servicioId = domicilio.id,
                fechaHora = instante(hoy, 11),
                direccion = Direccion(
                    calle = "Los Leones",
                    numero = "10",
                    comuna = "Providencia",
                    ciudad = "Santiago",
                    lat = null,
                    lng = null,
                    indicaciones = "Depto 5B",
                ),
            ),
            capturada.captured,
        )
        val state = viewModel.state.value
        assertEquals("res-99", state.reservaCreadaId)
        assertFalse(state.enviando)
        assertNull(state.errorEnvio)
        assertFalse(state.puedeContinuar)
    }

    @Test
    fun `Confirmar con indicaciones en blanco las envia como null`() = runTest {
        val capturada = slot<SolicitudReserva>()
        coEvery { reservaRepository.crear(capture(capturada)) } returns Result.Success("res-1")
        val viewModel = crearViewModel()
        viewModel.avanzarHastaRevision(domicilio, indicaciones = "    ")

        viewModel.onAction(BookingAction.Confirmar)

        assertNull(capturada.captured.direccion!!.indicaciones)
    }

    @Test
    fun `Confirmar a una direccion guardada sin editar conserva las coordenadas`() = runTest {
        val capturada = slot<SolicitudReserva>()
        coEvery { reservaRepository.crear(capture(capturada)) } returns Result.Success("res-1")
        val viewModel = crearViewModel(servicioIdArg = domicilio.id)
        viewModel.onAction(BookingAction.Continuar)
        viewModel.onAction(BookingAction.SeleccionarHorario(instante(hoy, 11)))
        viewModel.onAction(BookingAction.Continuar)
        viewModel.onAction(BookingAction.UsarDireccionGuardada(direccionGuardada))
        viewModel.onAction(BookingAction.Continuar)

        viewModel.onAction(BookingAction.Confirmar)

        assertEquals(direccionGuardada, capturada.captured.direccion)
    }

    @Test
    fun `Confirmar un servicio que no es a domicilio envia direccion null aunque el formulario tenga datos`() = runTest {
        val capturada = slot<SolicitudReserva>()
        coEvery { reservaRepository.crear(capture(capturada)) } returns Result.Success("res-2")
        val viewModel = crearViewModel()
        // El cliente llena la direccion para un servicio a domicilio y luego cambia a consulta.
        viewModel.onAction(BookingAction.SeleccionarServicio(domicilio.id))
        viewModel.onAction(BookingAction.Continuar)
        viewModel.onAction(BookingAction.SeleccionarHorario(instante(hoy, 11)))
        viewModel.onAction(BookingAction.Continuar)
        viewModel.llenarDireccion()
        viewModel.onAction(BookingAction.PasoAnterior)
        viewModel.onAction(BookingAction.PasoAnterior)
        viewModel.onAction(BookingAction.SeleccionarServicio(consulta.id))
        viewModel.onAction(BookingAction.Continuar)
        viewModel.onAction(BookingAction.SeleccionarHorario(instante(hoy, 11)))
        viewModel.onAction(BookingAction.Continuar)
        assertEquals(PasoReserva.REVISION, viewModel.state.value.paso)

        viewModel.onAction(BookingAction.Confirmar)

        assertEquals(consulta.id, capturada.captured.servicioId)
        assertNull(capturada.captured.direccion)
        assertEquals("res-2", viewModel.state.value.reservaCreadaId)
    }

    @Test
    fun `Confirmar fuera de REVISION no llama al repositorio`() = runTest {
        val viewModel = crearViewModel(servicioIdArg = consulta.id)

        viewModel.onAction(BookingAction.Confirmar)
        viewModel.onAction(BookingAction.Continuar)
        viewModel.onAction(BookingAction.Confirmar)

        coVerify(exactly = 0) { reservaRepository.crear(any()) }
        assertFalse(viewModel.state.value.enviando)
    }

    @Test
    fun `mientras envia enviando es true y un segundo Confirmar no llama otra vez al repositorio`() = runTest {
        val gate = CompletableDeferred<Result<String, CrearReservaError>>()
        coEvery { reservaRepository.crear(any()) } coAnswers { gate.await() }
        val viewModel = crearViewModel()
        viewModel.avanzarHastaRevision(consulta)

        viewModel.onAction(BookingAction.Confirmar)
        assertTrue(viewModel.state.value.enviando)
        assertFalse(viewModel.state.value.puedeContinuar)
        viewModel.onAction(BookingAction.Confirmar)
        viewModel.onAction(BookingAction.PasoAnterior)

        coVerify(exactly = 1) { reservaRepository.crear(any()) }
        assertEquals(PasoReserva.REVISION, viewModel.state.value.paso)

        gate.complete(Result.Success("res-3"))
        assertFalse(viewModel.state.value.enviando)
        assertEquals("res-3", viewModel.state.value.reservaCreadaId)
        viewModel.onAction(BookingAction.Confirmar)
        coVerify(exactly = 1) { reservaRepository.crear(any()) }
    }

    @Test
    fun `HORARIO_OCUPADO vuelve a FECHA_HORA, quita ese cupo y conserva el error`() = runTest {
        coEvery { reservaRepository.crear(any()) } returns Result.Error(CrearReservaError.HORARIO_OCUPADO)
        val viewModel = crearViewModel()
        viewModel.avanzarHastaRevision(consulta, horario = instante(hoy, 11))

        viewModel.onAction(BookingAction.Confirmar)

        val state = viewModel.state.value
        assertEquals(PasoReserva.FECHA_HORA, state.paso)
        assertEquals(CrearReservaError.HORARIO_OCUPADO, state.errorEnvio)
        assertNull(state.horarioSeleccionado)
        assertFalse(state.enviando)
        assertNull(state.reservaCreadaId)
        assertEquals(hoy, state.fechaSeleccionada)
        assertEquals(listOf(instante(hoy, 10), instante(hoy, 12)), state.horariosDelDia)
        assertEquals(4, state.dias.first { it.fecha == proximoLunes }.horarios.size)
        assertFalse(state.puedeContinuar)
    }

    @Test
    fun `si el cupo rechazado era el unico del dia, ese dia desaparece y se elige el siguiente`() = runTest {
        coEvery { reservaRepository.crear(any()) } returns Result.Error(CrearReservaError.HORARIO_OCUPADO)
        val viewModel = crearViewModel()
        // Con 120 min, hoy solo queda el cupo de las 11:00.
        viewModel.avanzarHastaRevision(consultaLarga, horario = instante(hoy, 11))

        viewModel.onAction(BookingAction.Confirmar)

        val state = viewModel.state.value
        assertEquals(PasoReserva.FECHA_HORA, state.paso)
        assertEquals(listOf(proximoLunes), state.dias.map { it.fecha })
        assertEquals(proximoLunes, state.fechaSeleccionada)
        assertNull(state.horarioSeleccionado)
    }

    @Test
    fun `FUERA_DE_HORARIO y ANTICIPACION_INSUFICIENTE tambien devuelven a FECHA_HORA sin ese cupo`() = runTest {
        listOf(CrearReservaError.FUERA_DE_HORARIO, CrearReservaError.ANTICIPACION_INSUFICIENTE).forEach { error ->
            coEvery { reservaRepository.crear(any()) } returns Result.Error(error)
            val viewModel = crearViewModel()
            viewModel.avanzarHastaRevision(consulta, horario = instante(hoy, 11))

            viewModel.onAction(BookingAction.Confirmar)

            val state = viewModel.state.value
            assertEquals(PasoReserva.FECHA_HORA, state.paso, "paso para $error")
            assertEquals(error, state.errorEnvio)
            assertNull(state.horarioSeleccionado)
            assertFalse(instante(hoy, 11) in state.horariosDelDia)
        }
    }

    @Test
    fun `tras HORARIO_OCUPADO elegir otro horario limpia el error y permite confirmar de nuevo`() = runTest {
        coEvery { reservaRepository.crear(any()) } returnsMany listOf(
            Result.Error(CrearReservaError.HORARIO_OCUPADO),
            Result.Success("res-4"),
        )
        val viewModel = crearViewModel()
        viewModel.avanzarHastaRevision(consulta, horario = instante(hoy, 11))
        viewModel.onAction(BookingAction.Confirmar)

        viewModel.onAction(BookingAction.SeleccionarHorario(instante(hoy, 12)))
        assertNull(viewModel.state.value.errorEnvio)
        viewModel.onAction(BookingAction.Continuar)
        viewModel.onAction(BookingAction.Confirmar)

        assertEquals("res-4", viewModel.state.value.reservaCreadaId)
        coVerify(exactly = 2) { reservaRepository.crear(any()) }
    }

    @Test
    fun `DIRECCION_REQUERIDA vuelve al paso de direccion conservando el horario`() = runTest {
        coEvery { reservaRepository.crear(any()) } returns Result.Error(CrearReservaError.DIRECCION_REQUERIDA)
        val viewModel = crearViewModel()
        viewModel.avanzarHastaRevision(domicilio, horario = instante(hoy, 11))

        viewModel.onAction(BookingAction.Confirmar)

        val state = viewModel.state.value
        assertEquals(PasoReserva.DIRECCION, state.paso)
        assertEquals(CrearReservaError.DIRECCION_REQUERIDA, state.errorEnvio)
        assertEquals(instante(hoy, 11), state.horarioSeleccionado)
        assertFalse(state.enviando)
        assertNull(state.reservaCreadaId)
    }

    @Test
    fun `SIN_INTERNET se queda en REVISION con el error y permite reintentar`() = runTest {
        coEvery { reservaRepository.crear(any()) } returnsMany listOf(
            Result.Error(CrearReservaError.SIN_INTERNET),
            Result.Success("res-5"),
        )
        val viewModel = crearViewModel()
        viewModel.avanzarHastaRevision(consulta)

        viewModel.onAction(BookingAction.Confirmar)

        val state = viewModel.state.value
        assertEquals(PasoReserva.REVISION, state.paso)
        assertEquals(CrearReservaError.SIN_INTERNET, state.errorEnvio)
        assertFalse(state.enviando)
        assertEquals(instante(hoy, 11), state.horarioSeleccionado)
        assertTrue(state.puedeContinuar)

        viewModel.onAction(BookingAction.Confirmar)

        assertNull(viewModel.state.value.errorEnvio)
        assertEquals("res-5", viewModel.state.value.reservaCreadaId)
    }

    @Test
    fun `los demas errores de crearReserva se quedan en REVISION con el error`() = runTest {
        val conRedireccion = setOf(
            CrearReservaError.HORARIO_OCUPADO,
            CrearReservaError.FUERA_DE_HORARIO,
            CrearReservaError.ANTICIPACION_INSUFICIENTE,
            CrearReservaError.DIRECCION_REQUERIDA,
        )
        (CrearReservaError.entries - conRedireccion).forEach { error ->
            coEvery { reservaRepository.crear(any()) } returns Result.Error(error)
            val viewModel = crearViewModel()
            viewModel.avanzarHastaRevision(consulta)

            viewModel.onAction(BookingAction.Confirmar)

            val state = viewModel.state.value
            assertEquals(PasoReserva.REVISION, state.paso, "paso para $error")
            assertEquals(error, state.errorEnvio)
            assertFalse(state.enviando, "enviando colgado para $error")
            assertNull(state.reservaCreadaId)
            assertTrue(state.puedeContinuar, "no se puede reintentar tras $error")
        }
    }

    @Test
    fun `PasoAnterior tras un error de envio limpia el mensaje`() = runTest {
        coEvery { reservaRepository.crear(any()) } returns Result.Error(CrearReservaError.SIN_INTERNET)
        val viewModel = crearViewModel()
        viewModel.avanzarHastaRevision(consulta)
        viewModel.onAction(BookingAction.Confirmar)

        viewModel.onAction(BookingAction.PasoAnterior)

        assertNull(viewModel.state.value.errorEnvio)
        assertEquals(PasoReserva.FECHA_HORA, viewModel.state.value.paso)
    }

    // ----------------------------------------------------------- navegacion

    @Test
    fun `las acciones de navegacion no cambian el estado`() = runTest {
        val viewModel = crearViewModel(servicioIdArg = consulta.id)
        viewModel.onAction(BookingAction.Continuar)
        val antes = viewModel.state.value

        viewModel.onAction(BookingAction.VolverAtras)
        viewModel.onAction(BookingAction.IrAMisCitas)
        viewModel.onAction(BookingAction.Finalizar)

        assertEquals(antes, viewModel.state.value)
        coVerify(exactly = 0) { reservaRepository.crear(any()) }
    }
}
