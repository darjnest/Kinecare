plugins {
    // Sin version: AGP 9 (built-in Kotlin) ya deja el plugin de Kotlin en el
    // classpath del build; fijar una version aqui choca con esa integracion.
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        // kotlinx.datetime.Instant es un typealias de kotlin.time.Instant,
        // que todavia requiere opt-in explicito en Kotlin 2.2.
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

tasks.test {
    useJUnitPlatform()
}
