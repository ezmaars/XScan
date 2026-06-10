package com.xscan.app.domain.repository

import android.net.Uri
import com.xscan.app.domain.model.Adjustments
import com.xscan.app.domain.model.CornerQuad
import com.xscan.app.domain.model.ExportRequest
import com.xscan.app.domain.model.ScanDocument
import com.xscan.app.domain.model.ScanPage
import kotlinx.coroutines.flow.Flow

/**
 * Domain-level result. Errors carry user-presentable causes; ViewModels
 * map them to copy — repositories never produce UI strings.
 */
sealed interface DomainResult<out T> {
    data class Success<T>(val value: T) : DomainResult<T>
    data class Failure(val cause: DomainError) : DomainResult<Nothing>
}

enum class DomainError { STORAGE_FULL, FILE_MISSING, PROCESSING_FAILED, EXPORT_FAILED, UNKNOWN }

/* ───────────────────────── Document store ──────────────────────────── */

interface DocumentRepository {

    /** Reactive source of truth for the dashboard. Sorted by updatedAt desc. */
    fun observeDocuments(query: String = ""): Flow<List<ScanDocument>>

    fun observeDocument(id: String): Flow<ScanDocument?>

    suspend fun createDocument(name: String, firstPage: ScanPage): DomainResult<ScanDocument>

    suspend fun renameDocument(id: String, name: String): DomainResult<Unit>

    suspend fun deleteDocument(id: String): DomainResult<Unit>

    /* Multipage editing */
    suspend fun addPage(documentId: String, page: ScanPage): DomainResult<Unit>

    suspend fun deletePage(documentId: String, pageId: String): DomainResult<Unit>

    suspend fun reorderPages(documentId: String, orderedPageIds: List<String>): DomainResult<Unit>

    suspend fun updatePage(documentId: String, page: ScanPage): DomainResult<Unit>
}

/* ──────────────────────── Image processing ─────────────────────────── */

/**
 * Boundary to the native/OpenCV layer. Implementations may be mocked;
 * the contract is what the ViewModels are wired against.
 */
interface ScanProcessor {

    /** Detect a document quad in the frame; null when nothing confident. */
    suspend fun detectEdges(imageUri: Uri): CornerQuad?

    /** Variance-of-Laplacian style score; SureScan keeps the max of 3. */
    suspend fun sharpnessScore(imageUri: Uri): Float

    /** Perspective-correct [source] by [quad], then apply [adjustments]. */
    suspend fun process(
        source: Uri,
        quad: CornerQuad,
        adjustments: Adjustments,
    ): DomainResult<ProcessedImage>
}

data class ProcessedImage(val uri: Uri, val sizeBytes: Long)

/* ───────────────────────────── Export ──────────────────────────────── */

interface DocumentExporter {

    /** Renders the document and hands off to the requested target. */
    suspend fun export(request: ExportRequest): DomainResult<Uri>

    /** Estimated output size for the size-selector UI. */
    suspend fun estimateSizeBytes(request: ExportRequest): Long
}
