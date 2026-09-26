package com.darjnest.kinecare.feature.client_panel.presentation.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Reemplaza `Dispatchers.Main` por un [TestDispatcher] mientras corre el
 * test. `viewModelScope.launch` usa `Dispatchers.Main.immediate`, que no
 * existe en un test JVM puro (sin Robolectric) — sin esto, cualquier
 * `ViewModel` de este modulo lanzaria "Module with the Main dispatcher had
 * failed to initialize" apenas se construye.
 *
 * Copia identica de `feature/search/.../MainDispatcherExtension.kt`: no se
 * comparte directamente porque las features nunca se dependen entre si
 * (docs/ARCHITECTURE.md), tampoco en el `test` sourceset.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherExtension(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : BeforeEachCallback, AfterEachCallback {

    override fun beforeEach(context: ExtensionContext) {
        Dispatchers.setMain(dispatcher)
    }

    override fun afterEach(context: ExtensionContext) {
        Dispatchers.resetMain()
    }
}
