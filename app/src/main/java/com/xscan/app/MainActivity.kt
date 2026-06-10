package com.xscan.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xscan.app.presentation.enhance.EnhanceRoute
import com.xscan.app.presentation.export.DocumentRoute
import com.xscan.app.presentation.export.DocumentViewModel
import com.xscan.app.presentation.library.LibraryRoute
import com.xscan.app.presentation.library.LibraryViewModel
import com.xscan.app.presentation.scan.CaptureRoute
import com.xscan.app.presentation.scan.CaptureViewModel
import com.xscan.app.presentation.scan.CropRoute
import com.xscan.app.presentation.scan.CropViewModel
import com.xscan.app.presentation.scan.EnhanceViewModel
import com.xscan.app.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as XScanApp).container
        setContent {
            AppTheme {
                XScanNavHost(container)
            }
        }
    }
}

/** Tiny factory helper so ViewModels can take plain constructor arguments. */
@Suppress("UNCHECKED_CAST")
private fun <VM : ViewModel> factory(create: () -> VM) = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}

private object Routes {
    const val LIBRARY = "library"
    const val CAPTURE = "capture?documentId={documentId}"
    const val CROP = "crop/{imageUri}?quad={quad}&documentId={documentId}"
    const val DOCUMENT = "document/{documentId}"
    const val ENHANCE = "enhance/{sourceUri}?documentId={documentId}"

    fun capture(documentId: String? = null) =
        "capture?documentId=${documentId.orEmpty()}"

    fun crop(imageUri: Uri, quad: String?, documentId: String?) =
        "crop/${Uri.encode(imageUri.toString())}" +
            "?quad=${Uri.encode(quad.orEmpty())}&documentId=${documentId.orEmpty()}"

    fun document(documentId: String) = "document/$documentId"

    fun enhance(sourceUri: Uri, documentId: String?) =
        "enhance/${Uri.encode(sourceUri.toString())}?documentId=${documentId.orEmpty()}"
}

private val optionalString = navArgument("documentId") {
    type = NavType.StringType
    defaultValue = ""
}

@Composable
private fun XScanNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val context = LocalContext.current

    NavHost(navController = navController, startDestination = Routes.LIBRARY) {

        composable(Routes.LIBRARY) {
            val vm: LibraryViewModel = viewModel(
                factory = factory { LibraryViewModel(container.repository) },
            )
            LibraryRoute(
                viewModel = vm,
                onNavigateToDocument = { navController.navigate(Routes.document(it)) },
                onNavigateToCapture = { navController.navigate(Routes.capture()) },
                onShowMessage = { context.toast(it) },
            )
        }

        composable(
            route = Routes.CAPTURE,
            arguments = listOf(optionalString),
        ) { entry ->
            val documentId = entry.arguments?.getString("documentId")?.ifBlank { null }
            val vm: CaptureViewModel = viewModel(
                factory = factory { CaptureViewModel(container.processor) },
            )
            CaptureRoute(
                viewModel = vm,
                onNavigateToCrop = { uri, quad ->
                    navController.navigate(
                        Routes.crop(
                            imageUri = uri,
                            quad = quad?.let { CropViewModel.encodeQuad(it) },
                            documentId = documentId,
                        ),
                    )
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.CROP,
            arguments = listOf(
                navArgument("imageUri") { type = NavType.StringType },
                navArgument("quad") { type = NavType.StringType; defaultValue = "" },
                optionalString,
            ),
        ) { entry ->
            val imageUri = checkNotNull(entry.arguments?.getString("imageUri"))
            val quad = entry.arguments?.getString("quad")?.ifBlank { null }
            val documentId = entry.arguments?.getString("documentId")?.ifBlank { null }
            val vm: CropViewModel = viewModel(
                factory = factory { CropViewModel(imageUri, quad, container.processor) },
            )
            CropRoute(
                viewModel = vm,
                onNavigateToEnhance = { processedUri ->
                    navController.navigate(Routes.enhance(processedUri, documentId))
                },
                onNavigateBackToCamera = { navController.popBackStack() },
                onShowMessage = { context.toast(it) },
            )
        }

        composable(
            route = Routes.ENHANCE,
            arguments = listOf(
                navArgument("sourceUri") { type = NavType.StringType },
                optionalString,
            ),
        ) { entry ->
            val sourceUri = checkNotNull(entry.arguments?.getString("sourceUri"))
            val documentId = entry.arguments?.getString("documentId")?.ifBlank { null }
            val vm: EnhanceViewModel = viewModel(
                factory = factory {
                    EnhanceViewModel(
                        sourceUriString = sourceUri,
                        appendToDocumentId = documentId,
                        processor = container.processor,
                        repository = container.repository,
                    )
                },
            )
            EnhanceRoute(
                viewModel = vm,
                onNavigateToDocument = { id ->
                    navController.navigate(Routes.document(id)) {
                        // The whole scan flow collapses; back returns to the library.
                        popUpTo(Routes.LIBRARY)
                    }
                },
                onShowMessage = { context.toast(it) },
            )
        }

        composable(
            route = Routes.DOCUMENT,
            arguments = listOf(navArgument("documentId") { type = NavType.StringType }),
        ) { entry ->
            val documentId = checkNotNull(entry.arguments?.getString("documentId"))
            val vm: DocumentViewModel = viewModel(
                factory = factory {
                    DocumentViewModel(documentId, container.repository, container.exporter)
                },
            )
            DocumentRoute(
                viewModel = vm,
                onNavigateToCapture = { navController.navigate(Routes.capture(it)) },
                onLaunchShare = { uri, mime -> context.shareFile(uri, mime) },
                onLaunchEmail = { uri, mime, subject -> context.emailFile(uri, mime, subject) },
                onLaunchPrint = { uri, jobName -> context.printPdf(uri, jobName) },
                onShowMessage = { context.toast(it) },
            )
        }
    }
}
