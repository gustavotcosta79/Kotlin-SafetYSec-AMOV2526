package pt.isec.amov.safetysec.managers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File

class CameraManager(private val context: Context) {

    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private val storage = FirebaseStorage.getInstance()

    // Iniciar Câmara (Preview) com proteção contra falhas do Emulador
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: androidx.camera.core.Preview.SurfaceProvider,
        onCameraReady: () -> Unit = {}
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = androidx.camera.core.Preview.Builder().build()
                preview.setSurfaceProvider(surfaceProvider)

                // Tenta criar o gravador com estratégia de fallback para evitar erros de qualidade
                val recorder = try {
                    val qualitySelector = QualitySelector.from(
                        Quality.LOWEST,
                        FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
                    )
                    Recorder.Builder()
                        .setQualitySelector(qualitySelector)
                        .build()
                } catch (e: Exception) {
                    Log.e("CameraManager", "Falha qualidade. A usar defaults.", e)
                    Recorder.Builder().build()
                }

                videoCapture = VideoCapture.withOutput(recorder)
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    videoCapture
                )

                Log.d("CameraManager", "Câmara iniciada com sucesso.")
                onCameraReady()

            } catch (exc: Exception) {
                Log.e("CameraManager", "CRASH EVITADO: Erro fatal na câmara", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Gravar Vídeo
    @SuppressLint("MissingPermission")
    fun recordVideo(onVideoRecorded: (File) -> Unit) {
        val videoCapture = this.videoCapture ?: return

        val videoFile = File(context.externalCacheDir, "alerta_${System.currentTimeMillis()}.mp4")
        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        // Prepara a gravação
        val pendingRecording = videoCapture.output
            .prepareRecording(context, outputOptions)

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pendingRecording.withAudioEnabled()
        } else {
            Log.w("CameraManager", "AVISO: A gravar SEM áudio (permissão em falta).")
        }

        activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { recordEvent ->
            if (recordEvent is VideoRecordEvent.Finalize) {
                if (!recordEvent.hasError()) {
                    Log.d("CameraManager", "Vídeo gravado: ${videoFile.absolutePath}")
                    onVideoRecorded(videoFile)
                } else {
                    Log.e("CameraManager", "Erro gravação: ${recordEvent.error}")
                }
            }
        }

        // Parar automaticamente após 30 segundos
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            stopRecording()
        }, 30000)
    }

    fun stopRecording() {
        try {
            activeRecording?.stop()
            activeRecording = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 3. Upload para o Firebase Storage
    suspend fun uploadVideo(file: File): String? {
        return try {
            val ref = storage.reference.child("alert_videos/${file.name}")
            ref.putFile(Uri.fromFile(file)).await()
            ref.downloadUrl.await().toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}