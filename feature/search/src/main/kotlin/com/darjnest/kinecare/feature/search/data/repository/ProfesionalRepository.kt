package com.darjnest.kinecare.feature.search.data.repository

import com.darjnest.kinecare.core.common.domain.model.Profesional
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.search.domain.ProfesionalError

interface ProfesionalRepository {
    suspend fun buscarPorEspecialidad(especialidad: String): Result<List<Profesional>, ProfesionalError>
}
