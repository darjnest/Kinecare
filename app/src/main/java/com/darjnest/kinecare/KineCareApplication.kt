package com.darjnest.kinecare

import android.app.Application
import com.darjnest.kinecare.core.network.security.PinningOverrideRemoto
import com.darjnest.kinecare.push.crearCanalCitas
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class KineCareApplication : Application() {

    @Inject
    lateinit var pinningOverrideRemoto: PinningOverrideRemoto

    override fun onCreate() {
        super.onCreate()
        crearCanalCitas(this)
        // Trae el interruptor remoto del certificate pinning sin bloquear el arranque. Remote Config
        // usa otro host que las Cloud Functions, asi que llega aunque el pinning las bloquee.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { pinningOverrideRemoto.refrescar() }
    }
}
