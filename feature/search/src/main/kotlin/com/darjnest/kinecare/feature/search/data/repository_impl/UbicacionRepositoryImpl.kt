package com.darjnest.kinecare.feature.search.data.repository_impl

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.search.data.repository.UbicacionRepository
import com.darjnest.kinecare.feature.search.domain.UbicacionError
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume

class UbicacionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationProviderClient: FusedLocationProviderClient,
) : UbicacionRepository {

    @SuppressLint("MissingPermission")
    override suspend fun obtenerUbicacionActual(): Result<String, UbicacionError> {
        if (!tienePermisoDeUbicacion()) return Result.Error(UbicacionError.PERMISO_DENEGADO)

        return try {
            val ubicacion = fusedLocationProviderClient
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .await() ?: return Result.Error(UbicacionError.UBICACION_NO_DISPONIBLE)

            val direccion = geocodificar(ubicacion.latitude, ubicacion.longitude)
                ?: return Result.Error(UbicacionError.UBICACION_NO_DISPONIBLE)
            Result.Success(direccion)
        } catch (e: Exception) {
            Result.Error(UbicacionError.DESCONOCIDO)
        }
    }

    private fun tienePermisoDeUbicacion(): Boolean {
        val fina = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val gruesa = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fina == PackageManager.PERMISSION_GRANTED || gruesa == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun geocodificar(latitud: Double, longitud: Double): String? {
        val geocoder = Geocoder(context, Locale.forLanguageTag("es-CL"))
        val direccion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocodificarAsync(geocoder, latitud, longitud)
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(latitud, longitud, 1)?.firstOrNull()
        }
        return direccion?.let(::formatearDireccion)
    }

    private suspend fun geocodificarAsync(geocoder: Geocoder, latitud: Double, longitud: Double): Address? =
        suspendCancellableCoroutine { continuation ->
            geocoder.getFromLocation(latitud, longitud, 1) { direcciones ->
                continuation.resume(direcciones.firstOrNull())
            }
        }

    private fun formatearDireccion(direccion: Address): String {
        val comuna = direccion.subAdminArea ?: direccion.locality
        val region = direccion.adminArea
        return listOfNotNull(comuna, region).joinToString(", ").ifBlank { "Ubicación actual" }
    }
}
