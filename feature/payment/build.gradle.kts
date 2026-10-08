plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.darjnest.kinecare.feature.payment"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    // `PagoRepositoryImpl` vive en esta feature (solo la usa `:feature:payment`)
    // y usa de `:core:network` la interfaz Retrofit `CloudFunctionsApi`, sus
    // DTOs y el helper `llamarCallable`. `ProfesionalRepository` es una
    // interfaz de `:core:common` cuya implementacion vive en `:core:network`.
    // Ademas se inyecta `FirebaseAuth` para resolver el uid del profesional que
    // vuelve de vincular su cuenta de Mercado Pago.
    implementation(project(":core:network"))

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    // `Response<CallableResponse<T>>` de `CloudFunctionsApi` (Retrofit).
    implementation(libs.retrofit.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    // `PagoRepositoryImplTest`: `Task.await()` mockeado y cuerpos de error de
    // Retrofit (`ResponseBody`).
    testImplementation(libs.kotlinx.coroutines.play.services)
    testImplementation(libs.okhttp.core)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
