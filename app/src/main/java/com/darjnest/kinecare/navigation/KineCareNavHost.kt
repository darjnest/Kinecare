package com.darjnest.kinecare.navigation

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.util.Consumer
import androidx.navigation.NavController
import androidx.navigation.navOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.feature.auth.presentation.navigation.AuthRoute
import com.darjnest.kinecare.feature.auth.presentation.navigation.authGraph
import com.darjnest.kinecare.feature.auth.presentation.viewmodel.AuthAction
import com.darjnest.kinecare.feature.auth.presentation.viewmodel.AuthViewModel
import com.darjnest.kinecare.feature.booking.presentation.navigation.BookingRoute
import com.darjnest.kinecare.feature.booking.presentation.navigation.bookingGraph
import com.darjnest.kinecare.feature.client_panel.presentation.navigation.FavoritosRoute
import com.darjnest.kinecare.feature.client_panel.presentation.navigation.MiPerfilClienteRoute
import com.darjnest.kinecare.feature.client_panel.presentation.navigation.MisCitasRoute
import com.darjnest.kinecare.feature.client_panel.presentation.navigation.client_panelGraph
import com.darjnest.kinecare.feature.payment.presentation.navigation.ConectarMercadoPagoRoute
import com.darjnest.kinecare.feature.payment.presentation.navigation.PaymentRoute
import com.darjnest.kinecare.feature.payment.presentation.navigation.paymentGraph
import com.darjnest.kinecare.feature.professional_panel.presentation.navigation.ProfessionalPanelRoute
import com.darjnest.kinecare.feature.professional_panel.presentation.navigation.professional_panelGraph
import com.darjnest.kinecare.feature.professional_profile.presentation.navigation.ProfessionalProfileRoute
import com.darjnest.kinecare.feature.professional_profile.presentation.navigation.professional_profileGraph
import com.darjnest.kinecare.feature.reviews.presentation.navigation.CrearResenaRoute
import com.darjnest.kinecare.feature.reviews.presentation.navigation.ReviewsRoute
import com.darjnest.kinecare.feature.reviews.presentation.navigation.reviewsGraph
import com.darjnest.kinecare.feature.search.presentation.navigation.SearchRoute
import com.darjnest.kinecare.feature.search.presentation.navigation.searchGraph
import com.darjnest.kinecare.feature.verification.presentation.navigation.VerificationRoute
import com.darjnest.kinecare.feature.verification.presentation.navigation.verificationGraph

/**
 * Grafo raiz. Hoy es plano (todas las features cuelgan directo de Auth)
 * porque ninguna feature tiene logica real todavia (docs/TASKS.md Fase 1).
 * Al iniciar sesion o registrarse, el rol del Usuario autenticado decide
 * el home (SearchRoute para Cliente, ProfessionalPanelRoute para
 * Profesional) y AuthRoute sale del back stack.
 */
@Composable
fun KineCareNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    EntregarDeepLinksNuevos(navController)

    NavHost(
        navController = navController,
        startDestination = AuthRoute,
        modifier = modifier,
    ) {
        authGraph(
            onSesionIniciada = { usuario ->
                val destino = when (usuario.rol) {
                    RolUsuario.CLIENTE -> SearchRoute
                    RolUsuario.PROFESIONAL -> ProfessionalPanelRoute
                }
                navController.navigate(destino) {
                    popUpTo(AuthRoute) { inclusive = true }
                }
            },
        )
        val onCerrarSesionCliente: () -> Unit = {
            authViewModel.onAction(AuthAction.CerrarSesion)
            navController.navigate(AuthRoute) {
                popUpTo(SearchRoute) { inclusive = true }
            }
        }
        val onProfesionalClick: (String) -> Unit = { profesionalId ->
            navController.navigate(ProfessionalProfileRoute(profesionalId))
        }
        searchGraph(
            onProfesionalClick = onProfesionalClick,
            onCerrarSesion = onCerrarSesionCliente,
            onIrAMisCitas = { navController.navigate(MisCitasRoute) { launchSingleTop = true } },
            onIrAFavoritos = { navController.navigate(FavoritosRoute) { launchSingleTop = true } },
            onIrAMiPerfil = { navController.navigate(MiPerfilClienteRoute) { launchSingleTop = true } },
        )
        client_panelGraph(
            navController = navController,
            onProfesionalClick = onProfesionalClick,
            onIrAExplorar = { navController.navigate(SearchRoute) { launchSingleTop = true } },
            onIrAMisCitas = { navController.navigate(MisCitasRoute) { launchSingleTop = true } },
            onIrAFavoritos = { navController.navigate(FavoritosRoute) { launchSingleTop = true } },
            onIrAMiPerfil = { navController.navigate(MiPerfilClienteRoute) { launchSingleTop = true } },
            onCerrarSesion = onCerrarSesionCliente,
            onDejarResena = { reservaId, profesionalId ->
                navController.navigate(CrearResenaRoute(reservaId, profesionalId))
            },
            onPagar = { reservaId, titulo, montoClp ->
                navController.navigate(PaymentRoute(reservaId, titulo, montoClp))
            },
        )
        val onCerrarSesionProfesional: () -> Unit = {
            authViewModel.onAction(AuthAction.CerrarSesion)
            navController.navigate(AuthRoute) {
                popUpTo(ProfessionalPanelRoute) { inclusive = true }
            }
        }
        val onVerResenas: (String) -> Unit = { profesionalId ->
            navController.navigate(ReviewsRoute(profesionalId))
        }
        professional_profileGraph(
            onVolver = { navController.popBackStack() },
            onVerResenas = onVerResenas,
            onReservar = { profesionalId, servicioId ->
                navController.navigate(BookingRoute(profesionalId, servicioId))
            },
        )
        bookingGraph(
            onVolver = { navController.popBackStack() },
            // La reserva ya esta creada: el flujo sale del back stack para que
            // "atras" desde Mis Citas no vuelva a la confirmacion.
            onIrAMisCitas = {
                navController.navigate(MisCitasRoute) {
                    popUpTo<BookingRoute> { inclusive = true }
                    launchSingleTop = true
                }
            },
        )
        paymentGraph(
            navController = navController,
            onVolver = { navController.popBackStack() },
            // Si el cliente salio de Mis Citas para pagar, vuelve a la que ya esta en el back
            // stack (que se recarga al reanudar); si llego por un deep link en frio, no hay
            // ninguna y se abre como raiz.
            onIrAMisCitas = {
                if (!navController.popBackStack<MisCitasRoute>(inclusive = false)) {
                    navController.navigate(MisCitasRoute) { popUpTo(navController.graph.id) { inclusive = true } }
                }
            },
            // Un pago rechazado se reintenta desde la pantalla Pagar, que sigue debajo en el
            // back stack; sin ella (deep link en frio) se reintenta desde Mis Citas.
            onReintentarPago = {
                if (!navController.popBackStack<PaymentRoute>(inclusive = false)) {
                    navController.navigate(MisCitasRoute) { popUpTo(navController.graph.id) { inclusive = true } }
                }
            },
            onIrAlPanelProfesional = {
                if (!navController.popBackStack<ProfessionalPanelRoute>(inclusive = false)) {
                    navController.navigate(ProfessionalPanelRoute) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            },
            // Sin sesion lo unico util es el login; al iniciar sesion `AuthRoute` lleva al home del rol.
            onIniciarSesion = {
                navController.navigate(AuthRoute) { popUpTo(navController.graph.id) { inclusive = true } }
            },
        )
        verificationGraph(
            onVolver = { navController.popBackStack() },
            // "Listo" tras el resultado vuelve a Verificar identidad, que sigue debajo en el back
            // stack y refresca su estado al reanudar; sin ella (deep link en frio) se abre el panel.
            onListo = {
                if (!navController.popBackStack<VerificationRoute>(inclusive = false)) {
                    navController.navigate(ProfessionalPanelRoute) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            },
            // Sin sesion lo unico util es el login; al iniciar sesion `AuthRoute` lleva al home del rol.
            onIniciarSesion = {
                navController.navigate(AuthRoute) { popUpTo(navController.graph.id) { inclusive = true } }
            },
        )
        professional_panelGraph(
            navController,
            onCerrarSesion = onCerrarSesionProfesional,
            onVerResenas = onVerResenas,
            onConectarMercadoPago = { navController.navigate(ConectarMercadoPagoRoute) },
            onVerificarIdentidad = { navController.navigate(VerificationRoute) { launchSingleTop = true } },
        )
        reviewsGraph(onVolver = { navController.popBackStack() })
    }
}

/**
 * Navigation Compose procesa el deep link de la Activity solo al crear el
 * grafo (arranque en frio); no reacciona a `onNewIntent`. Como `MainActivity`
 * es `singleTask`, el retorno desde el Custom Tab (`kinecare://pago/...`,
 * `kinecare://mp/...`, `kinecare://verificacion/...`) llega a la Activity ya abierta por `onNewIntent`, asi
 * que se entrega aqui al `NavController`.
 *
 * Se navega con `navigate(uri)` y no con `handleDeepLink(intent)`: este ultimo
 * (con `FLAG_ACTIVITY_NEW_TASK`) reinicia la Activity y vacia el back stack, y el
 * cliente perderia Mis Citas bajo la pantalla de resultado. Un deep link sin
 * destino conocido se ignora, y no depende de que haya sesion (cada pantalla
 * de resultado consulta al backend y maneja `SIN_SESION`).
 */
@Composable
private fun EntregarDeepLinksNuevos(navController: NavController) {
    val activity = LocalContext.current.buscarActivity() ?: return
    DisposableEffect(navController, activity) {
        val oyente = Consumer<Intent> { intent ->
            val uri = intent.data ?: return@Consumer
            if (uri.scheme == ESQUEMA_DEEP_LINK && navController.graph.hasDeepLink(uri)) {
                navController.navigate(uri, navOptions { launchSingleTop = true })
            }
        }
        activity.addOnNewIntentListener(oyente)
        onDispose { activity.removeOnNewIntentListener(oyente) }
    }
}

private const val ESQUEMA_DEEP_LINK = "kinecare"

private fun Context.buscarActivity(): ComponentActivity? {
    var contexto: Context? = this
    while (contexto is ContextWrapper) {
        if (contexto is ComponentActivity) return contexto
        contexto = contexto.baseContext
    }
    return null
}
