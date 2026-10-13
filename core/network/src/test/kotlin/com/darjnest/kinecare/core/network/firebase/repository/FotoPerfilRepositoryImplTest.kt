package com.darjnest.kinecare.core.network.firebase.repository

import android.net.Uri
import com.darjnest.kinecare.core.common.data.error.FotoPerfilError
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.core.network.firebase.storage.LectorImagen
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import javax.inject.Provider

class FotoPerfilRepositoryImplTest {

    private val storage = mockk<FirebaseStorage>()
    private val raiz = mockk<StorageReference>()
    private val referencia = mockk<StorageReference>()
    private val lector = mockk<LectorImagen>()
    private val repository = FotoPerfilRepositoryImpl(Provider { storage }, lector).apply { ahoraMillis = { 99L } }

    private val jpeg = byteArrayOf(1, 2, 3)

    @BeforeEach
    fun setUp() {
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
        every { storage.reference } returns raiz
        every { raiz.child(any()) } returns referencia
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    private fun subidaExitosa(url: String = "https://dl/foto?alt=media&token=t") {
        coEvery { lector.leerComoJpeg("content://foto/1") } returns Result.Success(jpeg)
        val subida = mockk<UploadTask>()
        coEvery { subida.await() } returns mockk()
        every { referencia.putBytes(any(), any<StorageMetadata>()) } returns subida
        val descarga = mockk<Task<Uri>>()
        val uri = mockk<Uri>()
        every { uri.toString() } returns url
        coEvery { descarga.await() } returns uri
        every { referencia.downloadUrl } returns descarga
    }

    @Test
    fun `sube el JPEG a la ruta del profesional y devuelve la URL versionada`() = runTest {
        subidaExitosa()

        val resultado = repository.subir("prof-1", RolUsuario.PROFESIONAL, "content://foto/1")

        assertEquals(Result.Success("https://dl/foto?alt=media&token=t&v=99"), resultado)
        verify { raiz.child("profesionales/prof-1/foto-perfil.jpg") }
        verify { referencia.putBytes(jpeg, any<StorageMetadata>()) }
    }

    @Test
    fun `sube la foto del cliente a su propia ruta`() = runTest {
        subidaExitosa()

        repository.subir("cli-1", RolUsuario.CLIENTE, "content://foto/1")

        verify { raiz.child("clientes/cli-1/foto-perfil.jpg") }
    }

    @Test
    fun `si la imagen no se puede leer no toca Storage`() = runTest {
        coEvery { lector.leerComoJpeg(any()) } returns Result.Error(FotoPerfilError.IMAGEN_INVALIDA)

        val resultado = repository.subir("cli-1", RolUsuario.CLIENTE, "content://foto/1")

        assertEquals(Result.Error(FotoPerfilError.IMAGEN_INVALIDA), resultado)
        verify(exactly = 0) { storage.reference }
    }

    @Test
    fun `un bucket inexistente degrada a ALMACENAMIENTO_NO_DISPONIBLE`() = runTest {
        coEvery { lector.leerComoJpeg(any()) } returns Result.Success(jpeg)
        val subida = mockk<UploadTask>()
        coEvery { subida.await() } throws excepcionDeStorage(StorageException.ERROR_OBJECT_NOT_FOUND)
        every { referencia.putBytes(any(), any<StorageMetadata>()) } returns subida

        val resultado = repository.subir("prof-1", RolUsuario.PROFESIONAL, "content://foto/1")

        assertEquals(Result.Error(FotoPerfilError.ALMACENAMIENTO_NO_DISPONIBLE), resultado)
    }

    @Test
    fun `sin bucket configurado el SDK lanza y se degrada sin romper`() = runTest {
        coEvery { lector.leerComoJpeg(any()) } returns Result.Success(jpeg)
        val sinBucket = FotoPerfilRepositoryImpl(Provider { error("sin bucket") }, lector)

        val resultado = sinBucket.subir("prof-1", RolUsuario.PROFESIONAL, "content://foto/1")

        assertEquals(Result.Error(FotoPerfilError.ALMACENAMIENTO_NO_DISPONIBLE), resultado)
    }

    @Test
    fun `mapea los errores de Storage a errores de dominio`() {
        assertEquals(FotoPerfilError.SIN_PERMISO, excepcionDeStorage(StorageException.ERROR_NOT_AUTHORIZED).aFotoPerfilError())
        assertEquals(FotoPerfilError.SIN_PERMISO, excepcionDeStorage(StorageException.ERROR_NOT_AUTHENTICATED).aFotoPerfilError())
        assertEquals(FotoPerfilError.SIN_INTERNET, excepcionDeStorage(StorageException.ERROR_RETRY_LIMIT_EXCEEDED).aFotoPerfilError())
        assertEquals(FotoPerfilError.ALMACENAMIENTO_NO_DISPONIBLE, excepcionDeStorage(StorageException.ERROR_BUCKET_NOT_FOUND).aFotoPerfilError())
        assertEquals(FotoPerfilError.ALMACENAMIENTO_NO_DISPONIBLE, excepcionDeStorage(StorageException.ERROR_QUOTA_EXCEEDED).aFotoPerfilError())
        assertEquals(FotoPerfilError.DESCONOCIDO, excepcionDeStorage(StorageException.ERROR_UNKNOWN).aFotoPerfilError())
        assertEquals(FotoPerfilError.SIN_INTERNET, excepcionDeStorage(StorageException.ERROR_UNKNOWN, IOException()).aFotoPerfilError())
        assertEquals(FotoPerfilError.SIN_INTERNET, FirebaseNetworkException("sin red").aFotoPerfilError())
        assertEquals(FotoPerfilError.DESCONOCIDO, RuntimeException("x").aFotoPerfilError())
        assertTrue(IOException().aFotoPerfilError() == FotoPerfilError.SIN_INTERNET)
    }

    private fun excepcionDeStorage(codigo: Int, causa: Throwable? = null): StorageException {
        val e = mockk<StorageException>(relaxed = true)
        every { e.errorCode } returns codigo
        every { e.cause } returns causa
        return e
    }
}
