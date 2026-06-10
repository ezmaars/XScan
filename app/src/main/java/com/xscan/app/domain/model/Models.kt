package com.xscan.app.domain.model

import android.net.Uri

/* ───────────────────────────── Documents ───────────────────────────── */

data class ScanDocument(
    val id: String,
    val name: String,
    val pages: List<ScanPage>,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    val pageCount: Int get() = pages.size
    val coverUri: Uri? get() = pages.firstOrNull()?.processedUri
    val sizeBytes: Long get() = pages.sumOf { it.sizeBytes }
}

data class ScanPage(
    val id: String,
    val originalUri: Uri,          // Raw capture, never mutated
    val processedUri: Uri,         // Result of crop + adjustments
    val corners: CornerQuad,       // Last applied perspective quad
    val adjustments: Adjustments,
    val sizeBytes: Long,
)

/* ───────────────────────────── Geometry ───────────────────────────── */

/** A point in normalized image space — both axes in 0f..1f. */
data class NPoint(val x: Float, val y: Float)

/** Four draggable corners, clockwise from top-left, in normalized space. */
data class CornerQuad(
    val topLeft: NPoint,
    val topRight: NPoint,
    val bottomRight: NPoint,
    val bottomLeft: NPoint,
) {
    companion object {
        /** Full-frame fallback when edge detection finds nothing. */
        val FULL = CornerQuad(
            topLeft = NPoint(0f, 0f),
            topRight = NPoint(1f, 0f),
            bottomRight = NPoint(1f, 1f),
            bottomLeft = NPoint(0f, 1f),
        )
    }
}

/* ─────────────────────────── Processing ────────────────────────────── */

enum class ColorMode { COLOR, BLACK_AND_WHITE }

data class Adjustments(
    val colorMode: ColorMode = ColorMode.COLOR,
    val brightness: Float = 0f,      // -1f..1f, 0 = as captured
    val contrast: Float = 0f,        // -1f..1f, 0 = as captured
    val rotationDegrees: Int = 0,    // 0 / 90 / 180 / 270
) {
    fun rotatedClockwise() = copy(rotationDegrees = (rotationDegrees + 90) % 360)
}

/* ───────────────────────────── Capture ─────────────────────────────── */

enum class FlashMode { OFF, ON, AUTO }

/** SureScan takes three frames and keeps the sharpest. */
enum class CaptureMode { SINGLE, SURE_SCAN }

const val SURE_SCAN_FRAMES = 3

/* ───────────────────────────── Export ──────────────────────────────── */

enum class ExportFormat { PDF, JPEG }

enum class AttachmentSize(val label: String, val maxLongEdgePx: Int, val jpegQuality: Int) {
    SMALL("Small", 1280, 60),
    MEDIUM("Medium", 2048, 75),
    ACTUAL("Actual", Int.MAX_VALUE, 92),
}

sealed interface ExportTarget {
    data object EmailToMyself : ExportTarget
    data object Share : ExportTarget          // System share sheet
    data object CloudPrint : ExportTarget
}

data class ExportRequest(
    val documentId: String,
    val format: ExportFormat,
    val size: AttachmentSize,
    val target: ExportTarget,
)
