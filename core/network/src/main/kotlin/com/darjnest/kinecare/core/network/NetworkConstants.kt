package com.darjnest.kinecare.core.network

/**
 * Region donde se despliegan las Cloud Functions (`functions/src`). Igual a
 * la de Firestore (`nam5`, multi-region de EE.UU.) para no cruzar regiones
 * en cada lectura de la funcion.
 */
internal const val CLOUD_FUNCTIONS_REGION = "us-central1"

/**
 * URL base de las funciones del proyecto Firebase activo. El `projectId`
 * sale de `google-services.json` del flavor (`qa`/`prod`), asi QA nunca
 * llama a las funciones de produccion.
 */
internal fun urlBaseCloudFunctions(projectId: String): String =
    "https://$CLOUD_FUNCTIONS_REGION-$projectId.cloudfunctions.net/"
