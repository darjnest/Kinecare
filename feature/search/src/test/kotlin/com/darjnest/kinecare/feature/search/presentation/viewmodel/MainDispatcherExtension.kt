package com.darjnest.kinecare.feature.search.presentation.viewmodel

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
 * existe en un test JVM puro (sin Robolectric) — sin esto,
 * `SearchViewModel` lanzaría "Module with the Main dispatcher had failed to
 * initialize" apenas se construye.
 *
 * Se usa [UnconfinedTestDispatcher] (no [kotlinx.coroutines.test.StandardTestDispatcher])
 * a propósito: como no comparte el `TestCoroutineScheduler` de `runTest`,
 * necesitamos que las corrutinas lanzadas en `viewModelScope` corran de
 * forma eager/sincrónica en el momento del `launch` en vez de quedar en
 * cola esperando un `advanceUntilIdle()` que nunca las alcanzaría.
 *
 * Este es el primer ViewModel con tests del proyecto — reusa esta
 * extension (en vez de duplicarla) para los próximos ViewModels que
 * necesiten lo mismo.
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
