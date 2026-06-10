package com.xscan.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.core.content.FileProvider
import com.xscan.app.data.DocumentExporterImpl
import com.xscan.app.data.DocumentRepositoryImpl
import com.xscan.app.data.ScanProcessorImpl
import com.xscan.app.domain.repository.DocumentExporter
import com.xscan.app.domain.repository.DocumentRepository
import com.xscan.app.domain.repository.ScanProcessor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class XScanApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Hand-rolled dependency container — one place where everything is built. */
class AppContainer(context: Context) {
    val repository: DocumentRepository = DocumentRepositoryImpl(context)
    val processor: ScanProcessor = ScanProcessorImpl(context)
    val exporter: DocumentExporter = DocumentExporterImpl(context, repository)
}

/* ────────────────────── System hand-off helpers ────────────────────── */

fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

private fun Context.contentUriFor(fileUri: String): Uri? {
    val path = Uri.parse(fileUri).path ?: return null
    return FileProvider.getUriForFile(this, "$packageName.fileprovider", File(path))
}

fun Context.shareFile(fileUri: String, mimeType: String) {
    val content = contentUriFor(fileUri) ?: return toast("File not found")
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, content)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, "Send document"))
}

fun Context.emailFile(fileUri: String, mimeType: String, subject: String) {
    val content = contentUriFor(fileUri) ?: return toast("File not found")
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_STREAM, content)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // Steer the chooser toward mail apps without excluding them.
        selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
    }
    try {
        startActivity(Intent.createChooser(intent, "Email to myself"))
    } catch (t: Throwable) {
        toast("No email app found")
    }
}

/** Hands a finished PDF to Android's print framework (printer or save-as-PDF). */
fun Context.printPdf(fileUri: String, jobName: String) {
    val path = Uri.parse(fileUri).path ?: return toast("File not found")
    val file = File(path)
    val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager

    val adapter = object : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: android.os.Bundle?,
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(file.name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback,
        ) {
            try {
                FileInputStream(file).use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        input.copyTo(output)
                    }
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (t: Throwable) {
                callback.onWriteFailed(t.message)
            }
        }
    }

    printManager.print(jobName, adapter, PrintAttributes.Builder().build())
}
