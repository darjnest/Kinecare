package com.darjnest.kinecare.feature.search.presentation.view

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisTexto
import com.darjnest.kinecare.feature.search.presentation.viewmodel.ProfesionalDestacado
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

private val SANTIAGO = LatLng(-33.4489, -70.6693)
private const val ZOOM_CIUDAD = 11f
private const val ZOOM_UN_PIN = 13f
private const val MARGEN_ENCUADRE_PX = 120
private const val META_CLAVE_MAPS = "com.google.android.geo.API_KEY"

/**
 * Mapa con un pin por profesional que tiene [ProfesionalDestacado.zona] (zona aproximada, nunca la
 * ubicacion exacta). Si la app se compilo sin clave de Google Maps no se crea el mapa y se explica
 * por que, en vez de mostrar un recuadro gris sin contexto.
 */
@Composable
internal fun MapaProfesionales(
    pines: List<ProfesionalDestacado>,
    seleccionadoId: String?,
    onPinClick: (String) -> Unit,
    onMapaClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    if (!claveDeMapsConfigurada(context)) {
        MapaNoDisponible(modifier)
        return
    }

    val camara = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(SANTIAGO, ZOOM_CIUDAD) }
    var mapaCargado by remember { mutableStateOf(false) }
    // Encuadra todos los pines cuando el mapa esta listo y cada vez que cambia el conjunto (filtros).
    LaunchedEffect(mapaCargado, pines.map { it.id }) {
        if (mapaCargado) encuadrar(camara, pines)
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = camara,
        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
        onMapLoaded = { mapaCargado = true },
        onMapClick = { onMapaClick() },
    ) {
        pines.forEach { profesional ->
            val zona = profesional.zona ?: return@forEach
            Marker(
                state = rememberMarkerState(key = profesional.id, position = LatLng(zona.latitud, zona.longitud)),
                title = profesional.nombre,
                snippet = "Zona aproximada",
                icon = BitmapDescriptorFactory.defaultMarker(
                    if (profesional.id == seleccionadoId) BitmapDescriptorFactory.HUE_AZURE else BitmapDescriptorFactory.HUE_RED,
                ),
                onClick = {
                    onPinClick(profesional.id)
                    true // la tarjeta de la pantalla reemplaza a la ventana de informacion
                },
            )
        }
    }
}

private suspend fun encuadrar(camara: CameraPositionState, pines: List<ProfesionalDestacado>) {
    val puntos = pines.mapNotNull { p -> p.zona?.let { LatLng(it.latitud, it.longitud) } }.distinct()
    when (puntos.size) {
        0 -> camara.animate(CameraUpdateFactory.newLatLngZoom(SANTIAGO, ZOOM_CIUDAD))
        1 -> camara.animate(CameraUpdateFactory.newLatLngZoom(puntos.first(), ZOOM_UN_PIN))
        else -> {
            val limites = LatLngBounds.builder().apply { puntos.forEach { include(it) } }.build()
            camara.animate(CameraUpdateFactory.newLatLngBounds(limites, MARGEN_ENCUADRE_PX))
        }
    }
}

@Composable
private fun MapaNoDisponible(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "El mapa no est\u00e1 disponible en esta versi\u00f3n: falta configurar la clave de Google Maps.",
            style = MaterialTheme.typography.bodyMedium,
            color = LoginGrisTexto,
            textAlign = TextAlign.Center,
        )
    }
}

private fun claveDeMapsConfigurada(context: Context): Boolean = runCatching {
    val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
    !info.metaData?.getString(META_CLAVE_MAPS).isNullOrBlank()
}.getOrDefault(false)
