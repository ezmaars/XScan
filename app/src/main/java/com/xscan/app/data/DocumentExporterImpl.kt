package com.xscan.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.xscan.app.domain.model.ExportFormat
import com.xscan.app.domain.model.ExportRequest
import com.xscan.app.domain.model.ScanDocument
import com.xscan.app.domain.repository.DocumentExporter
import com.xscan.app.domain.repository.DocumentRepository
import com.xscan.app.domain.repository.DomainError
import com.xscan.app.domain.repository.DomainResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

class DocumentExporterImpl(
    private val context: Context,
    private val repository: DocumentRepository,
) : DocumentExporter {

    private val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }

    override suspend fun export(request: ExportRequest): DomainResult<Uri> =
        withContext(Dispatchers.IO) {
            val document = repository.observeDocument(request.documentId).first()
                ?: return@withContext DomainResult.Failure(DomainError.FILE_MISSING)
            try {
                val file = when (request.format) {
                    ExportFormat.PDF -> renderPdf(document, request)
                    ExportFormat.JPEG -> renderJpeg(document, request)
                } ?: return@withContext DomainResult.Failure(DomainError.EXPORT_FAILED)
                DomainResult.Success(Uri.fromFile(file))
            } catch (t: Throwable) {
                DomainResult.Failure(DomainError.EXPORT_FAILED)
            }
        }

    override suspend fun estimateSizeBytes(request: ExportRequest): Long =
        withContext(Dispatchers.IO) {
            val document = repository.observeDocument(request.documentId).first()
                ?: return@withContext 0L
            // Heuristic: stored pages were saved at quality 90 / 2200px long edge;
            // scale by the requested quality and resolution.
            val qualityFactor = request.size.jpegQuality / 90f
            val edgeFactor = min(1f, request.size.maxLongEdgePx / 2200f)
            val pages = if (request.format == ExportFormat.JPEG) {
                document.pages.take(1)
            } else document.pages
            pages.sumOf { page ->
                (page.sizeBytes * qualityFactor * edgeFactor * edgeFactor).toLong()
            }
        }

    /* ───────────────────────────── Renderers ───────────────────────── */

    private fun renderPdf(document: ScanDocument, request: ExportRequest): File? {
        val pdf = PdfDocument()
        try {
            document.pages.forEachIndexed { index, page ->
                val bitmap = loadScaled(page.processedUri, request.size.maxLongEdgePx)
                    ?: return null
                val info = PdfDocument.PageInfo
                    .Builder(bitmap.width, bitmap.height, index + 1)
                    .create()
                val pdfPage = pdf.startPage(info)
                pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdf.finishPage(pdfPage)
                bitmap.recycle()
            }
            val out = File(exportDir, "${document.name.toSafeFileName()}.pdf")
            FileOutputStream(out).use { pdf.writeTo(it) }
            return out
        } finally {
            pdf.close()
        }
    }

    /** JPEG can only hold one image — the first page goes out. */
    private fun renderJpeg(document: ScanDocument, request: ExportRequest): File? {
        val page = document.pages.firstOrNull() ?: return null
        val bitmap = loadScaled(page.processedUri, request.size.maxLongEdgePx) ?: return null
        val out = File(exportDir, "${document.name.toSafeFileName()}.jpg")
        FileOutputStream(out).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, request.size.jpegQuality, stream)
        }
        bitmap.recycle()
        return out
    }

    private fun loadScaled(uri: Uri, maxLongEdge: Int): Bitmap? {
        val path = uri.path ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null

        var sample = 1
        if (maxLongEdge != Int.MAX_VALUE) {
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxLongEdge) {
                sample *= 2
            }
        }
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
            inSampleSize = sample
        })
    }

    private fun String.toSafeFileName(): String =
        replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "Scan" }
}
