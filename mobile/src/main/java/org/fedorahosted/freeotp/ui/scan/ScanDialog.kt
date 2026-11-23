package org.fedorahosted.freeotp.ui.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.fedorahosted.freeotp.R
import java.util.concurrent.Executors

private const val LOGTAG = "ScanDialog"

/** Fade from the camera to the recognized code, before the token is added. */
private const val FADE_MS = 2000

fun hasCamera(context: Context): Boolean =
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

/**
 * A square camera viewfinder that scans for an `otpauth://` QR code. Asks for the camera
 * permission first and closes if it is denied. Once a code is recognized, the camera stops, the
 * phone vibrates and the viewfinder fades into a rendering of the code; then [onScanned] gets
 * its text.
 *
 * [camera] shows the viewfinder and reports a decoded text or an error; tests replace it.
 */
@Composable
fun ScanDialog(
    onScanned: (String) -> Unit,
    onDismiss: () -> Unit,
    camera: @Composable (onDecoded: (String) -> Unit, onError: () -> Unit, modifier: Modifier) -> Unit =
        { onDecoded, onError, modifier -> CameraViewfinder(onDecoded, onError, modifier) },
) {
    val context = LocalContext.current
    var permitted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) permitted = true else onDismiss()
    }
    LaunchedEffect(Unit) {
        if (!permitted) requestPermission.launch(Manifest.permission.CAMERA)
    }

    var scanned by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val cameraAlpha by animateFloatAsState(
        targetValue = if (scanned == null) 1f else 0f,
        animationSpec = tween(FADE_MS, easing = LinearOutSlowInEasing),
        label = "camera",
    )

    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color.Black)
                .testTag("scan"),
        ) {
            when {
                failed -> Text(
                    stringResource(R.string.error_camera_open),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                )
                permitted -> {
                    // Removing the viewfinder stops the camera; it stays while fading out.
                    if (scanned == null || cameraAlpha > 0f) {
                        camera(
                            { text -> if (scanned == null) scanned = text },
                            { failed = true },
                            Modifier
                                .fillMaxSize()
                                .alpha(cameraAlpha),
                        )
                    }
                    scanned?.let { ScannedCode(it, onScanned) }
                    if (scanned == null) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                                .size(32.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannedCode(text: String, onScanned: (String) -> Unit) {
    val context = LocalContext.current
    val image = remember(text) { qrCodeBitmap(text).asImageBitmap() }
    val alpha = remember(text) { Animatable(0f) }
    val currentOnScanned by rememberUpdatedState(onScanned)

    LaunchedEffect(text) {
        vibrate(context)
        alpha.animateTo(1f, tween(FADE_MS, easing = LinearOutSlowInEasing))
        currentOnScanned(text)
    }

    Image(
        image,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.None,
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha.value)
            .testTag("scan_result"),
    )
}

/**
 * CameraX preview plus QR analysis, bound to the current lifecycle (so the camera pauses while
 * the app is in the background) and unbound when this leaves the composition.
 */
@Composable
private fun CameraViewfinder(onDecoded: (String) -> Unit, onError: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnDecoded by rememberUpdatedState(onDecoded)
    val currentOnError by rememberUpdatedState(onError)
    val previewView = remember {
        PreviewView(context).apply {
            // A TextureView: it fades with the dialog and keeps the last frame when stopped.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)

    DisposableEffect(lifecycleOwner) {
        val main = ContextCompat.getMainExecutor(context)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(analysisExecutor, QrAnalyzer { text -> main.execute { currentOnDecoded(text) } }) }

        var disposed = false
        var provider: ProcessCameraProvider? = null
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (disposed) return@addListener
            try {
                val p = future.get()
                provider = p
                p.bindToLifecycle(lifecycleOwner, cameraSelector(p), preview, analysis)
            } catch (e: Exception) {
                Log.e(LOGTAG, "Can't open the camera", e)
                currentOnError()
            }
        }, main)

        onDispose {
            disposed = true
            provider?.unbind(preview, analysis)
            analysis.clearAnalyzer()
            analysisExecutor.shutdown()
        }
    }
}

/** The back camera, else the front one, else an external one. */
private fun cameraSelector(provider: ProcessCameraProvider): CameraSelector =
    listOf(CameraSelector.DEFAULT_BACK_CAMERA, CameraSelector.DEFAULT_FRONT_CAMERA)
        .firstOrNull { provider.hasCamera(it) }
        ?: CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_EXTERNAL).build()
            .takeIf { provider.hasCamera(it) }
        ?: throw IllegalStateException("No camera available")

private fun vibrate(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService<VibratorManager>()?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService<Vibrator>()
    } ?: return
    vibrator.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
}
