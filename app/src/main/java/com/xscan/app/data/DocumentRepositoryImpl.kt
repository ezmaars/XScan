package com.xscan.app.data

import android.content.Context
import android.net.Uri
import com.xscan.app.domain.model.Adjustments
import com.xscan.app.domain.model.ColorMode
import com.xscan.app.domain.model.CornerQuad
import com.xscan.app.domain.model.NPoint
import com.xscan.app.domain.model.ScanDocument
import com.xscan.app.domain.model.ScanPage
import com.xscan.app.domain.repository.DocumentRepository
import com.xscan.app.domain.repository.DomainError
import com.xscan.app.domain.repository.DomainResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * File-backed store: page images live in filesDir/xscan/, and the
 * catalog persists as JSON. A StateFlow is the in-memory source of truth
 * so every screen updates reactively.
 */
class DocumentRepositoryImpl(private val context: Context) : DocumentRepository {

    private val baseDir = File(context.filesDir, "xscan").apply { mkdirs() }
    private val catalogFile = File(baseDir, "documents.json")
    private val documents = MutableStateFlow<List<ScanDocument>>(emptyList())
    private val mutex = Mutex()

    init {
        documents.value = load()
    }

    override fun observeDocuments(query: String): Flow<List<ScanDocument>> =
        documents.map { all ->
            all.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                .sortedByDescending { it.updatedAtMillis }
        }

    override fun observeDocument(id: String): Flow<ScanDocument?> =
        documents.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun createDocument(
        name: String,
        firstPage: ScanPage,
    ): DomainResult<ScanDocument> = mutate {
        val stored = firstPage.intoPermanentStorage()
        val now = System.currentTimeMillis()
        val document = ScanDocument(
            id = UUID.randomUUID().toString(),
            name = name,
            pages = listOf(stored),
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        documents.value = documents.value + document
        document
    }

    override suspend fun renameDocument(id: String, name: String): DomainResult<Unit> = mutate {
        update(id) { it.copy(name = name) }
    }

    override suspend fun deleteDocument(id: String): DomainResult<Unit> = mutate {
        documents.value.firstOrNull { it.id == id }?.pages?.forEach { it.deleteFiles() }
        documents.value = documents.value.filterNot { it.id == id }
    }

    override suspend fun addPage(documentId: String, page: ScanPage): DomainResult<Unit> =
        mutate {
            val stored = page.intoPermanentStorage()
            update(documentId) { it.copy(pages = it.pages + stored) }
        }

    override suspend fun deletePage(documentId: String, pageId: String): DomainResult<Unit> =
        mutate {
            documents.value.firstOrNull { it.id == documentId }
                ?.pages?.firstOrNull { it.id == pageId }
                ?.deleteFiles()
            update(documentId) { doc ->
                doc.copy(pages = doc.pages.filterNot { it.id == pageId })
            }
        }

    override suspend fun reorderPages(
        documentId: String,
        orderedPageIds: List<String>,
    ): DomainResult<Unit> = mutate {
        update(documentId) { doc ->
            val byId = doc.pages.associateBy { it.id }
            val reordered = orderedPageIds.mapNotNull { byId[it] }
            if (reordered.size == doc.pages.size) doc.copy(pages = reordered) else doc
        }
    }

    override suspend fun updatePage(documentId: String, page: ScanPage): DomainResult<Unit> =
        mutate {
            update(documentId) { doc ->
                doc.copy(pages = doc.pages.map { if (it.id == page.id) page else it })
            }
        }

    /* ───────────────────────────── Plumbing ────────────────────────── */

    private suspend fun <T> mutate(block: () -> T): DomainResult<T> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val result = block()
                    persist()
                    DomainResult.Success(result)
                } catch (t: Throwable) {
                    DomainResult.Failure(DomainError.UNKNOWN)
                }
            }
        }

    private fun update(id: String, transform: (ScanDocument) -> ScanDocument) {
        documents.value = documents.value.map { doc ->
            if (doc.id == id) {
                transform(doc).copy(updatedAtMillis = System.currentTimeMillis())
            } else doc
        }
    }

    /** Copy cache-dir files into permanent app storage so they survive cleanup. */
    private fun ScanPage.intoPermanentStorage(): ScanPage {
        fun keep(uri: Uri, tag: String): Uri {
            val source = File(uri.path ?: return uri)
            if (!source.exists() || source.parentFile == baseDir) return uri
            val target = File(baseDir, "${tag}_${UUID.randomUUID()}.jpg")
            source.copyTo(target, overwrite = true)
            return Uri.fromFile(target)
        }
        return copy(
            originalUri = keep(originalUri, "original"),
            processedUri = keep(processedUri, "page"),
        )
    }

    private fun ScanPage.deleteFiles() {
        originalUri.path?.let { File(it).delete() }
        processedUri.path?.let { File(it).delete() }
    }

    /* ───────────────────────── JSON persistence ────────────────────── */

    private fun persist() {
        val array = JSONArray()
        documents.value.forEach { doc ->
            array.put(
                JSONObject().apply {
                    put("id", doc.id)
                    put("name", doc.name)
                    put("createdAt", doc.createdAtMillis)
                    put("updatedAt", doc.updatedAtMillis)
                    put(
                        "pages",
                        JSONArray().apply {
                            doc.pages.forEach { page -> put(page.toJson()) }
                        },
                    )
                },
            )
        }
        catalogFile.writeText(array.toString())
    }

    private fun load(): List<ScanDocument> = runCatching {
        if (!catalogFile.exists()) return emptyList()
        val array = JSONArray(catalogFile.readText())
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            val pagesJson = obj.getJSONArray("pages")
            ScanDocument(
                id = obj.getString("id"),
                name = obj.getString("name"),
                createdAtMillis = obj.getLong("createdAt"),
                updatedAtMillis = obj.getLong("updatedAt"),
                pages = (0 until pagesJson.length()).map { j ->
                    pagesJson.getJSONObject(j).toPage()
                },
            )
        }
    }.getOrDefault(emptyList())

    private fun ScanPage.toJson() = JSONObject().apply {
        put("id", id)
        put("original", originalUri.toString())
        put("processed", processedUri.toString())
        put("sizeBytes", sizeBytes)
        put("rotation", adjustments.rotationDegrees)
        put("brightness", adjustments.brightness.toDouble())
        put("contrast", adjustments.contrast.toDouble())
        put("bw", adjustments.colorMode == ColorMode.BLACK_AND_WHITE)
        put(
            "quad",
            listOf(corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft)
                .joinToString(";") { "${it.x},${it.y}" },
        )
    }

    private fun JSONObject.toPage(): ScanPage {
        val quadPoints = getString("quad").split(";").map {
            val (x, y) = it.split(",")
            NPoint(x.toFloat(), y.toFloat())
        }
        return ScanPage(
            id = getString("id"),
            originalUri = Uri.parse(getString("original")),
            processedUri = Uri.parse(getString("processed")),
            sizeBytes = getLong("sizeBytes"),
            corners = CornerQuad(quadPoints[0], quadPoints[1], quadPoints[2], quadPoints[3]),
            adjustments = Adjustments(
                rotationDegrees = getInt("rotation"),
                brightness = getDouble("brightness").toFloat(),
                contrast = getDouble("contrast").toFloat(),
                colorMode = if (getBoolean("bw")) ColorMode.BLACK_AND_WHITE else ColorMode.COLOR,
            ),
        )
    }
}
