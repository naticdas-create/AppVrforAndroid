package com.lunaro.vr.hand.hand

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Captura a câmera frontal (CameraX) e roda o MediaPipe HandLandmarker em tempo real.
 * Só emite dados reais do modelo: se nenhuma mão for detectada, a lista vem vazia.
 */
class HandTracker(
    private val context: Context,
    private val owner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onFrame: (FrameResult) -> Unit
) {
    data class FrameResult(
        val hands: List<List<NormalizedLandmark>>,
        val imgW: Int,
        val imgH: Int,
        val latencyMs: Long
    )

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var landmarker: HandLandmarker? = null
    private var provider: ProcessCameraProvider? = null

    fun start() {
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { result, input ->
                val hands = result.landmarks().map { it.toList() }
                val latency = SystemClock.uptimeMillis() - result.timestampMs()
                onFrame(FrameResult(hands, input.width, input.height, latency))
            }
            .setErrorListener { it.printStackTrace() }
            .build()
        landmarker = HandLandmarker.createFromOptions(context, options)

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val p = future.get()
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analysis.setAnalyzer(executor) { proxy -> analyze(proxy) }
            p.unbindAll()
            p.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))
    }

    private fun analyze(proxy: androidx.camera.core.ImageProxy) {
        try {
            val raw = Bitmap.createBitmap(proxy.width, proxy.height, Bitmap.Config.ARGB_8888)
            proxy.planes[0].buffer.rewind()
            raw.copyPixelsFromBuffer(proxy.planes[0].buffer)
            val m = Matrix().apply {
                postRotate(proxy.imageInfo.rotationDegrees.toFloat())
                postScale(-1f, 1f) // espelha, como o preview da câmera frontal
            }
            val bmp = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            landmarker?.detectAsync(BitmapImageBuilder(bmp).build(), SystemClock.uptimeMillis())
        } finally {
            proxy.close()
        }
    }

    fun stop() {
        provider?.unbindAll()
        landmarker?.close()
        landmarker = null
        executor.shutdown()
    }
}
