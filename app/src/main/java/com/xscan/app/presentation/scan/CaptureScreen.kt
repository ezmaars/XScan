package com.xscan.app.presentation.scan

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xscan.app.domain.model.CaptureMode
import com.xscan.app.domain.model.CornerQuad
import com.xscan.app.domain.model.FlashMode
import com.xscan.app.ui.components.EmptyState
import com.xscan.app.ui.components.GoldThreadLoading
import com.xscan.app.ui.components.IconActionButton
import com.xscan.app.ui.theme.AppTheme
import com.xscan.app.ui.theme.pressFeedback
import java.io.File

/**
 * Route layer: owns the camera controller and runtime permission, translates
 * [CaptureEffect.TakePicture] into a CameraX still capture, and feeds the
 * result back as a [CaptureEvent].
 */
@Composable
fun CaptureRoute(
    viewModel: CaptureViewModel,
    onNavigateToCrop: (imageUri: Uri, quad: CornerQuad?) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onEvent(CaptureEvent.PermissionResult(granted)) }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }

    // Keep hardware flash in sync with UI state.
    LaunchedEffect(state.flashMode) {
        controller.imageCaptureFlashMode = when (state.flashMode) {
            FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CaptureEffect.TakePicture -> {
                    val file = File.createTempFile("scan_", ".jpg", context.cacheDir)
                    val options = ImageCapture.OutputFileOptions.Builder(file).build()
                    controller.takePicture(
                        options,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                viewModel.onEvent(CaptureEvent.FrameCaptured(Uri.fromFile(file)))
                            }

                            override fun onError(exception: ImageCaptureException) {
                                viewModel.onEvent(CaptureEvent.CaptureFailed(exception))
                            }
                        },
                    )
                }
                is CaptureEffect.NavigateToCrop ->
                    onNavigateToCrop(effect.imageUri, effect.detectedQuad)
                is CaptureEffect.NavigateBack -> onNavigateBack()
            }
        }
    }

    CaptureScreen(state = state, controller = controller, onEvent = viewModel::onEvent)
}

@Composable
fun CaptureScreen(
    state: CaptureUiState,
    controller: LifecycleCameraController,
    onEvent: (CaptureEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(AppTheme.colors.canvas)) {
        if (state.permissionDenied) {
            EmptyState(
                icon = Icons.Outlined.NoPhotography,
                title = "Camera is off",
                body = "XScan needs the camera to capture documents. " +
                    "Allow camera access in your phone's Settings, then come back.",
                actionText = "Close",
                onAction = { onEvent(CaptureEvent.CloseClicked) },
            )
            return
        }

        if (state.permissionGranted) {
            CameraPreview(controller = controller, modifier = Modifier.fillMaxSize())
        }

        CaptureTopBar(
            flashMode = state.flashMode,
            onFlash = { onEvent(CaptureEvent.FlashToggled) },
            onClose = { onEvent(CaptureEvent.CloseClicked) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(AppTheme.spacing.md),
        )

        CaptureBottomBar(
            state = state,
            onEvent = onEvent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = AppTheme.spacing.lg),
        )
    }
}

@Composable
private fun CameraPreview(controller: LifecycleCameraController, modifier: Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                controller.bindToLifecycle(lifecycleOwner)
                this.controller = controller
            }
        },
    )
}

@Composable
private fun CaptureTopBar(
    flashMode: FlashMode,
    onFlash: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        IconActionButton(
            icon = Icons.Outlined.Close,
            contentDescription = "Close camera",
            onClick = onClose,
            onScrim = true,
        )
        IconActionButton(
            icon = when (flashMode) {
                FlashMode.OFF -> Icons.Outlined.FlashOff
                FlashMode.ON -> Icons.Outlined.FlashOn
                FlashMode.AUTO -> Icons.Outlined.FlashAuto
            },
            contentDescription = "Flash: ${flashMode.name.lowercase()}",
            onClick = onFlash,
            active = flashMode != FlashMode.OFF,
            onScrim = true,
        )
    }
}

@Composable
private fun CaptureBottomBar(
    state: CaptureUiState,
    onEvent: (CaptureEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) {
            when (val phase = state.phase) {
                is CapturePhase.Capturing -> PhaseLabel(
                    "Hold steady \u00B7 ${phase.frame} of ${phase.of}",
                )
                is CapturePhase.Analyzing -> GoldThreadLoading(label = "Finding the page")
                is CapturePhase.Failed -> PhaseLabel("Capture failed \u2014 tap to retry")
                is CapturePhase.Ready -> if (state.captureMode == CaptureMode.SURE_SCAN) {
                    PhaseLabel("SureScan takes three frames and keeps the sharpest")
                }
            }
        }

        Spacer(Modifier.height(AppTheme.spacing.lg))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xl),
        ) {
            SureScanToggle(
                enabled = state.captureMode == CaptureMode.SURE_SCAN,
                onToggle = { onEvent(CaptureEvent.SureScanToggled) },
            )
            ShutterButton(
                busy = state.phase !is CapturePhase.Ready && state.phase !is CapturePhase.Failed,
                onClick = {
                    if (state.phase is CapturePhase.Failed) onEvent(CaptureEvent.RetryClicked)
                    else onEvent(CaptureEvent.ShutterClicked)
                },
            )
            Spacer(Modifier.size(64.dp))   // Balance the row around the shutter.
        }
    }
}

@Composable
private fun PhaseLabel(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(AppTheme.shapes.control))
            .background(AppTheme.colors.scrim)
            .padding(horizontal = AppTheme.spacing.md, vertical = AppTheme.spacing.xs),
    ) {
        Text(text, style = AppTheme.typography.bodySecondary, color = AppTheme.colors.inkPrimary)
    }
}

/** 72dp shutter: paper-white core in a ring; champagne while busy. */
@Composable
private fun ShutterButton(busy: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(72.dp)
            .pressFeedback(interaction)
            .clip(CircleShape)
            .border(2.dp, AppTheme.colors.inkPrimary, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = !busy,
                onClick = onClick,
            )
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(if (busy) AppTheme.colors.accent else AppTheme.colors.inkPrimary),
        )
    }
}

@Composable
private fun SureScanToggle(enabled: Boolean, onToggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(64.dp)
            .pressFeedback(interaction)
            .clip(CircleShape)
            .background(AppTheme.colors.scrim)
            .border(
                AppTheme.shapes.hairline,
                if (enabled) AppTheme.colors.accent else AppTheme.colors.outline,
                CircleShape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onToggle,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "\u00D73",
                style = AppTheme.typography.titleSection,
                color = if (enabled) AppTheme.colors.accent else AppTheme.colors.inkSecondary,
            )
            Text(
                text = "SURE",
                style = AppTheme.typography.overline,
                color = if (enabled) AppTheme.colors.accent else AppTheme.colors.inkTertiary,
            )
        }
    }
}
