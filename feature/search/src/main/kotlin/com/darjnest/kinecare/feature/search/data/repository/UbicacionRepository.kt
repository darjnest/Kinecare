package com.darjnest.kinecare.feature.search.data.repository

import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.search.domain.UbicacionError

interface UbicacionRepository {
    suspend fun obtenerUbicacionActual(): Result<String, UbicacionError>
}
