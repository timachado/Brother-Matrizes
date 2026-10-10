package com.timachado.brothermatrizes

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import com.timachado.brothermatrizes.core.embroidery.ConvertedMatrix
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryLoadResult
import com.timachado.brothermatrizes.core.embroidery.EmbroideryLoader
import com.timachado.brothermatrizes.core.embroidery.GeneratedMatrixPipeline
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.MatrixExporter
import com.timachado.brothermatrizes.core.archive.ArchiveCategory
import com.timachado.brothermatrizes.core.archive.SafeArchiveExtractor
import com.timachado.brothermatrizes.core.account.BrotherMatrizesAccountService
import com.timachado.brothermatrizes.core.library.LibraryActivityStore
import com.timachado.brothermatrizes.core.library.SentMatrixRecord
import com.timachado.brothermatrizes.core.project.ActiveDesignStore
import com.timachado.brothermatrizes.core.project.ProjectBackupStore
import com.timachado.brothermatrizes.core.project.ProjectStore
import com.timachado.brothermatrizes.core.project.SavedProjectSummary
import com.timachado.brothermatrizes.core.storage.DurablePendingDocument
import com.timachado.brothermatrizes.core.storage.PendingDocumentStore
import com.timachado.brothermatrizes.core.storage.SafeInputReader
import com.timachado.brothermatrizes.core.settings.UiPreferencesStore
import com.timachado.brothermatrizes.core.settings.AppColorMode
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.BrotherMatrizesTheme
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var accountOpenRequest by
        mutableStateOf(
            0
        )

    private var externalOpenUri by
        mutableStateOf<Uri?>(
            null
        )

    private fun captureExternalOpenIntent(
        sourceIntent: Intent?
    ) {
        val uri =
            when (
                sourceIntent?.action
            ) {
                Intent.ACTION_VIEW ->
                    sourceIntent.data

                Intent.ACTION_SEND ->
                    IntentCompat
                        .getParcelableExtra(
                            sourceIntent,
                            Intent.EXTRA_STREAM,
                            Uri::class.java
                        )

                else ->
                    null
            }

        if (
            uri !=
                null
        ) {
            externalOpenUri =
                uri
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DebugTelemetry.setup(
            applicationContext
        )

        captureExternalOpenIntent(
            intent
        )

        if (
            intent.getBooleanExtra(
                EXTRA_OPEN_ACCOUNT_FROM_AUTH,
                false
            )
        ) {
            accountOpenRequest++
        }

        setContent {
            var textScale by remember {
                mutableStateOf(
                    UiPreferencesStore
                        .textScale(
                            this@MainActivity
                        )
                )
            }

            var colorMode by remember {
                mutableStateOf(UiPreferencesStore.colorMode(this@MainActivity))
            }
            val darkTheme = when (colorMode) {
                AppColorMode.DARK -> true
                AppColorMode.LIGHT -> false
                AppColorMode.SYSTEM -> isSystemInDarkTheme()
            }
            SideEffect {
                val controller = WindowCompat.getInsetsController(
                    window, window.decorView
                )
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
            BrotherMatrizesTheme(
                textScaleMultiplier = textScale,
                darkTheme = darkTheme
            ) {
                BrotherMatrizesApp(
                    openAccountRequest =
                        accountOpenRequest,
                    externalOpenUri =
                        externalOpenUri,
                    onExternalOpenConsumed = {
                            consumedUri ->
                        if (
                            externalOpenUri ==
                                consumedUri
                        ) {
                            externalOpenUri =
                                null
                        }
                    },
                    textScale =
                        textScale,
                    colorMode = colorMode,
                    onColorModeChange = { requested ->
                        colorMode = UiPreferencesStore.setColorMode(
                            this@MainActivity, requested
                        )
                    },
                    onTextScaleChange = {
                            requested ->
                        textScale =
                            UiPreferencesStore
                                .setTextScale(
                                    this@MainActivity,
                                    requested
                                )
                    }
                )
            }
        }
    }

    override fun onNewIntent(
        intent: Intent
    ) {
        super.onNewIntent(
            intent
        )
        setIntent(
            intent
        )

        captureExternalOpenIntent(
            intent
        )

        if (
            intent.getBooleanExtra(
                EXTRA_OPEN_ACCOUNT_FROM_AUTH,
                false
            )
        ) {
            accountOpenRequest++
        }
    }
}

internal fun recommendedHoopFor(
    design: EmbroideryDesign
): HoopProfile {
    val designWidth =
        design.bounds.widthMm
    val designHeight =
        design.bounds.heightMm

    return HoopProfile.entries
        .firstOrNull {
                hoop ->
            (
                designWidth <=
                    hoop.widthMm &&
                designHeight <=
                    hoop.heightMm
                ) ||
                (
                    designWidth <=
                        hoop.heightMm &&
                    designHeight <=
                        hoop.widthMm
                    )
        }
        ?: HoopProfile.entries
            .last()
}

private sealed interface Screen {
    data object Home : Screen
    data object CreateName : Screen
    data object CreateMonogram : Screen
    data object CreateDrawing : Screen
    data object FontLibrary : Screen
    data object ProjectLibrary : Screen
    data object TelegramSearch : Screen
    data class ArchiveImport(val uri: Uri) : Screen
    data object Account : Screen

    data class Transfer(
        val design: EmbroideryDesign
    ) : Screen

    data class Viewer(
        val design: EmbroideryDesign,
        val displayMode:
            EmbroideryDisplayMode =
            EmbroideryDisplayMode.REALISTIC,
        val referenceHoop:
            HoopProfile =
            recommendedHoopFor(
                design
            ),
        val showConnections:
            Boolean =
            false
    ) : Screen

    data class Simulator(
        val design: EmbroideryDesign,
        val displayMode:
            EmbroideryDisplayMode =
            EmbroideryDisplayMode.REALISTIC,
        val referenceHoop:
            HoopProfile =
            recommendedHoopFor(
                design
            ),
        val showConnections:
            Boolean =
            false
    ) : Screen

    data class Converter(
        val design: EmbroideryDesign
    ) : Screen

    data class Editor(
        val design: EmbroideryDesign,
        val displayMode:
            EmbroideryDisplayMode =
            EmbroideryDisplayMode.REALISTIC,
        val referenceHoop:
            HoopProfile =
            recommendedHoopFor(
                design
            ),
        val showConnections:
            Boolean =
            false
    ) : Screen
}

@Composable
private fun BrotherMatrizesApp(
    openAccountRequest: Int = 0,
    externalOpenUri: Uri? = null,
    onExternalOpenConsumed: (Uri) -> Unit = {},
    textScale: Float,
    onTextScaleChange: (Float) -> Unit,
    colorMode: AppColorMode,
    onColorModeChange: (AppColorMode) -> Unit
) {
    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    val snackbar =
        remember {
            SnackbarHostState()
        }

    var screen by remember {
        mutableStateOf<Screen>(
            Screen.Home
        )
    }

    var recent by remember {
        mutableStateOf<
            EmbroideryDesign?
        >(null)
    }

    var pendingDocument by remember {
        mutableStateOf<
            DurablePendingDocument?
        >(null)
    }

    var savedProjects by remember {
        mutableStateOf<
            List<SavedProjectSummary>
        >(emptyList())
    }

    var favoriteProjectIds by remember {
        mutableStateOf(
            LibraryActivityStore
                .favoriteIds(
                    context
                )
        )
    }

    var recentProjectIds by remember {
        mutableStateOf(
            LibraryActivityStore
                .recentIds(
                    context
                )
        )
    }

    var sentMatrices by remember {
        mutableStateOf<
            List<SentMatrixRecord>
        >(
            LibraryActivityStore
                .sentMatrices(
                    context
                )
        )
    }

    var loading by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(
        openAccountRequest
    ) {
        if (
            openAccountRequest >
                0
        ) {
            screen =
                Screen.Account
        }
    }

    fun refreshLibraryActivity() {
        favoriteProjectIds =
            LibraryActivityStore
                .favoriteIds(
                    context
                )

        recentProjectIds =
            LibraryActivityStore
                .recentIds(
                    context
                )

        sentMatrices =
            LibraryActivityStore
                .sentMatrices(
                    context
                )
    }

    fun recordMachineDelivery(
        fileName: String,
        format: String,
        method: String
    ) {
        LibraryActivityStore
            .recordSent(
                context =
                    context,
                fileName =
                    fileName,
                format =
                    format,
                method =
                    method
            )

        refreshLibraryActivity()
    }

    fun removeSentRecord(
        record: SentMatrixRecord
    ) {
        LibraryActivityStore
            .removeSent(
                context =
                    context,
                recordId =
                    record.id
            )

        refreshLibraryActivity()

        scope.launch {
            snackbar.showSnackbar(
                "Registro removido dos envios recentes."
            )
        }
    }

    fun clearSentHistory() {
        LibraryActivityStore
            .clearSent(
                context
            )

        refreshLibraryActivity()

        scope.launch {
            snackbar.showSnackbar(
                "Histórico de envios recentes limpo."
            )
        }
    }

    fun clearRecentDesign() {
        scope.launch {
            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    ActiveDesignStore
                        .clear(
                            context
                        )
                }

            result.fold(
                onSuccess = {
                    recent =
                        null

                    snackbar.showSnackbar(
                        "Removido de Recente. Matrizes salvas na Biblioteca não foram apagadas."
                    )
                },
                onFailure = {
                    snackbar.showSnackbar(
                        "Não foi possível limpar o item recente."
                    )
                }
            )
        }
    }

    fun activateDesign(
        design: EmbroideryDesign
    ) {
        recent =
            design

        scope.launch {
            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    ActiveDesignStore
                        .save(
                            context,
                            design
                        )
                }

            result.onFailure {
                snackbar.showSnackbar(
                    "Não foi possível atualizar a cópia automática do trabalho. Verifique o espaço disponível no aparelho."
                )
            }
        }
    }

    LaunchedEffect(
        externalOpenUri
    ) {
        val uri =
            externalOpenUri
                ?: return@LaunchedEffect

        loading =
            true

        val isArchive = withContext(Dispatchers.IO) {
            SafeArchiveExtractor.isArchive(context, uri)
        }
        if (isArchive) {
            loading = false
            screen = Screen.ArchiveImport(uri)
            onExternalOpenConsumed(uri)
            return@LaunchedEffect
        }
        val result =
            withContext(
                Dispatchers.IO
            ) {
                EmbroideryLoader.load(
                    context.contentResolver,
                    uri
                )
            }

        loading =
            false

        when (
            result
        ) {
            is EmbroideryLoadResult.Success -> {
                activateDesign(
                    result.design
                )

                screen =
                    Screen.Viewer(
                        result.design
                    )
            }

            is EmbroideryLoadResult.Error -> {
                snackbar.showSnackbar(
                    result.userMessage
                )
            }
        }

        onExternalOpenConsumed(
            uri
        )
    }

    LaunchedEffect(Unit) {
        val result =
            withContext(
                Dispatchers.IO
            ) {
                ActiveDesignStore
                    .load(
                        context
                    )
            }

        result.fold(
            onSuccess = {
                    restored ->
                if (
                    recent ==
                        null &&
                    restored !=
                        null
                ) {
                    recent =
                        restored
                }
            },
            onFailure = {
                snackbar.showSnackbar(
                    "O trabalho automático anterior não pôde ser recuperado. A cópia corrompida foi isolada."
                )
            }
        )
    }

    fun goBack() {
        screen =
            when (
                val current =
                    screen
            ) {
                Screen.Home ->
                    Screen.Home

                Screen.Account,
                Screen.ProjectLibrary,
                Screen.TelegramSearch,
                Screen.FontLibrary,
                Screen.CreateName,
                Screen.CreateMonogram,
                Screen.CreateDrawing ->
                    Screen.Home

                is Screen.ArchiveImport -> Screen.ProjectLibrary

                is Screen.Transfer ->
                    Screen.Viewer(
                        current.design
                    )

                is Screen.Viewer ->
                    Screen.Home

                is Screen.Simulator ->
                    Screen.Viewer(
                        design =
                            current.design,
                        displayMode =
                            current.displayMode,
                        referenceHoop =
                            current.referenceHoop,
                        showConnections =
                            current.showConnections
                    )

                is Screen.Converter ->
                    Screen.Viewer(
                        current.design
                    )

                is Screen.Editor ->
                    Screen.Viewer(
                        design =
                            current.design,
                        displayMode =
                            current.displayMode,
                        referenceHoop =
                            current.referenceHoop,
                        showConnections =
                            current.showConnections
                    )
            }
    }

    BackHandler(
        enabled =
            screen !=
                Screen.Home
    ) {
        goBack()
    }

    var telegramImportPending by remember { mutableStateOf(false) }

    val picker =
        rememberLauncherForActivityResult(
            ActivityResultContracts
                .OpenDocument()
        ) { uri: Uri? ->
            if (uri == null) {
                telegramImportPending = false
                return@rememberLauncherForActivityResult
            }

            runCatching {
                context.contentResolver
                    .takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
            }

            if (SafeArchiveExtractor.isArchive(context, uri)) {
                telegramImportPending = false
                screen = Screen.ArchiveImport(uri)
                return@rememberLauncherForActivityResult
            }

            scope.launch {
                loading = true

                val result =
                    withContext(
                        Dispatchers.IO
                    ) {
                        EmbroideryLoader.load(
                            context.contentResolver,
                            uri
                        )
                    }

                loading = false

                when (result) {
                    is EmbroideryLoadResult.Success -> {
                        activateDesign(result.design)
                        screen = Screen.Viewer(result.design)
                        if (telegramImportPending) {
                            telegramImportPending = false
                            val saved = withContext(Dispatchers.IO) {
                                ProjectStore.save(context, result.design)
                            }
                            saved.fold(
                                onSuccess = { project ->
                                    savedProjects = listOf(project) +
                                        savedProjects.filter { it.id != project.id }
                                    snackbar.showSnackbar(
                                        "Matriz importada e salva na Biblioteca."
                                    )
                                },
                                onFailure = {
                                    snackbar.showSnackbar(
                                        "Matriz aberta, mas não foi possível salvar na Biblioteca."
                                    )
                                }
                            )
                        }
                    }

                    is EmbroideryLoadResult.Error -> {
                        telegramImportPending = false
                        snackbar.showSnackbar(result.userMessage)
                    }
                }
            }
        }

    val saveDocumentLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts
                .CreateDocument(
                    "application/octet-stream"
                )
        ) {
                destination: Uri? ->
            val memoryDocument =
                pendingDocument

            pendingDocument =
                null

            scope.launch {
                val document =
                    memoryDocument
                        ?: withContext(
                            Dispatchers.IO
                        ) {
                            PendingDocumentStore
                                .load(
                                    context
                                )
                                .getOrNull()
                        }

                if (
                    destination ==
                        null
                ) {
                    withContext(
                        Dispatchers.IO
                    ) {
                        PendingDocumentStore
                            .clear(
                                context
                            )
                    }

                    return@launch
                }

                if (
                    document ==
                        null
                ) {
                    withContext(
                        Dispatchers.IO
                    ) {
                        PendingDocumentStore
                            .clear(
                                context
                            )
                    }

                    snackbar.showSnackbar(
                        "Não foi possível recuperar o arquivo preparado para salvar."
                    )

                    return@launch
                }

                loading =
                    true

                val result =
                    withContext(
                        Dispatchers.IO
                    ) {
                        MatrixExporter.saveBytes(
                            contentResolver =
                                context.contentResolver,
                            destination =
                                destination,
                            bytes =
                                document.bytes
                        )
                    }

                loading =
                    false

                withContext(
                    Dispatchers.IO
                ) {
                    PendingDocumentStore
                        .clear(
                            context
                        )
                }

                result.fold(
                    onSuccess = {
                        if (
                            document.machineMethod !=
                                null &&
                            document.machineFormat !=
                                null
                        ) {
                            recordMachineDelivery(
                                fileName =
                                    document.fileName,
                                format =
                                    document.machineFormat,
                                method =
                                    document.machineMethod
                            )
                        }

                        snackbar.showSnackbar(
                            document.successMessage
                        )
                    },
                    onFailure = {
                            error ->
                        snackbar.showSnackbar(
                            if (
                                document.machineMethod !=
                                    null
                            ) {
                                "Não foi possível concluir a gravação. Verifique se o pendrive/OTG continua conectado e tente novamente."
                            } else {
                                error.message
                                    ?: "Não foi possível salvar o arquivo."
                            }
                        )
                    }
                )
            }
        }

    fun stageDocumentSave(
        document:
            DurablePendingDocument
    ) {
        scope.launch {
            val staged =
                withContext(
                    Dispatchers.IO
                ) {
                    PendingDocumentStore
                        .save(
                            context,
                            document
                        )
                }

            staged.fold(
                onSuccess = {
                    pendingDocument =
                        document

                    saveDocumentLauncher
                        .launch(
                            document.fileName
                        )
                },
                onFailure = {
                    snackbar.showSnackbar(
                        "Não foi possível preparar o arquivo para salvar. Verifique o espaço disponível no aparelho."
                    )
                }
            )
        }
    }

    val restoreBackupLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts
                .OpenDocument()
        ) {
                uri: Uri? ->
            if (
                uri ==
                    null
            ) {
                return@rememberLauncherForActivityResult
            }

            scope.launch {
                loading = true

                val result =
                    withContext(
                        Dispatchers.IO
                    ) {
                        runCatching {
                            val bytes =
                                context
                                    .contentResolver
                                    .openInputStream(
                                        uri
                                    )
                                    ?.use {
                                        SafeInputReader
                                            .readBytes(
                                                input =
                                                    it,
                                                maxBytes =
                                                    128 *
                                                        1024 *
                                                        1024
                                            )
                                    }
                                    ?: error(
                                        "Não foi possível ler o backup."
                                    )

                            val count =
                                ProjectBackupStore
                                    .restoreBackup(
                                        context,
                                        bytes
                                    )
                                    .getOrThrow()

                            val projects =
                                ProjectStore
                                    .list(
                                        context
                                    )
                                    .getOrThrow()

                            Pair(
                                count,
                                projects
                            )
                        }
                    }

                loading = false

                result.fold(
                    onSuccess = {
                            restored ->
                        savedProjects =
                            restored.second

                        snackbar.showSnackbar(
                            restored.first
                                .toString() +
                                " matriz(es) restaurada(s)."
                        )
                    },
                    onFailure = {
                            error ->
                        snackbar.showSnackbar(
                            error.message
                                ?: "Não foi possível restaurar este backup."
                        )
                    }
                )
            }
        }

    fun openShareIntent(
        matrix: ConvertedMatrix,
        chooserTitle: String =
            "Compartilhar matriz",
        machineMethod: String? = null
    ) {
        scope.launch {
            loading = true

            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    MatrixExporter
                        .createShareIntent(
                            context = context,
                            fileName =
                                matrix.fileName,
                            bytes =
                                matrix.bytes
                        )
                }

            loading = false

            result.fold(
                onSuccess = {
                        shareIntent ->
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(
                                shareIntent,
                                chooserTitle
                            )
                        )

                        if (
                            machineMethod !=
                                null
                        ) {
                            recordMachineDelivery(
                                fileName =
                                    matrix.fileName,
                                format =
                                    matrix.format,
                                method =
                                    machineMethod
                            )
                        }
                    }.onFailure {
                        snackbar.showSnackbar(
                            "Nenhum aplicativo disponível para compartilhar."
                        )
                    }
                },
                onFailure = {
                    snackbar.showSnackbar(
                        "Não foi possível preparar o compartilhamento."
                    )
                }
            )
        }
    }

    fun convertForSave(
        design: EmbroideryDesign,
        targetFormat: String,
        suffix: String =
            "convertido"
    ) {
        scope.launch {
            loading = true

            val result =
                withContext(
                    Dispatchers.Default
                ) {
                    MatrixConverter.convert(
                        design =
                            design,
                        targetFormat =
                            targetFormat,
                        outputSuffix =
                            suffix
                    )
                }

            loading = false

            result.fold(
                onSuccess = {
                        converted ->
                    val name =
                        MatrixExporter
                            .safeFileName(
                                converted
                                    .fileName
                            )

                    stageDocumentSave(
                        DurablePendingDocument(
                            fileName =
                                name,
                            bytes =
                                converted.bytes,
                            successMessage =
                                when (suffix) {
                                    "editado" ->
                                        "Matriz editada salva com sucesso."

                                    "criado" ->
                                        "Matriz criada salva com sucesso."

                                    "maquina" ->
                                        "Arquivo da máquina salvo no destino escolhido."

                                    else ->
                                        "Matriz convertida para " +
                                            converted.format +
                                            " e salva com sucesso."
                                },
                            machineMethod =
                                if (
                                    suffix ==
                                        "maquina"
                                ) {
                                    "USB / OTG"
                                } else {
                                    null
                                },
                            machineFormat =
                                if (
                                    suffix ==
                                        "maquina"
                                ) {
                                    converted.format
                                } else {
                                    null
                                }
                        )
                    )
                },
                onFailure = {
                    snackbar.showSnackbar(
                        "Não foi possível gerar o arquivo."
                    )
                }
            )
        }
    }

    fun convertForShare(
        design: EmbroideryDesign,
        targetFormat: String,
        suffix: String =
            "convertido",
        chooserTitle: String =
            "Compartilhar matriz"
    ) {
        scope.launch {
            loading = true

            val result =
                withContext(
                    Dispatchers.Default
                ) {
                    MatrixConverter.convert(
                        design =
                            design,
                        targetFormat =
                            targetFormat,
                        outputSuffix =
                            suffix
                    )
                }

            loading = false

            result.fold(
                onSuccess = {
                    openShareIntent(
                        matrix =
                            it,
                        chooserTitle =
                            chooserTitle,
                        machineMethod =
                            if (
                                suffix ==
                                    "maquina"
                            ) {
                                "Wi-Fi / app"
                            } else {
                                null
                            }
                    )
                },
                onFailure = {
                    snackbar.showSnackbar(
                        "Não foi possível gerar o arquivo para compartilhar."
                    )
                }
            )
        }
    }

    fun requestSave(
        design: EmbroideryDesign
    ) {
        if (design.isModified) {
            convertForSave(
                design =
                    design,
                targetFormat =
                    design.format,
                suffix =
                    if (
                        design.sourceBytes
                            .isEmpty()
                    ) {
                        "criado"
                    } else {
                        "editado"
                    }
            )

            return
        }

        val name =
            MatrixExporter.safeFileName(
                design.fileName
            )

        stageDocumentSave(
            DurablePendingDocument(
                fileName =
                    name,
                bytes =
                    design.sourceBytes,
                successMessage =
                    "Cópia salva com sucesso."
            )
        )
    }

    fun requestShare(
        design: EmbroideryDesign
    ) {
        if (design.isModified) {
            convertForShare(
                design =
                    design,
                targetFormat =
                    design.format,
                suffix =
                    if (
                        design.sourceBytes
                            .isEmpty()
                    ) {
                        "criado"
                    } else {
                        "editado"
                    }
            )

            return
        }

        openShareIntent(
            ConvertedMatrix(
                fileName =
                    design.fileName,
                format =
                    design.format,
                bytes =
                    design.sourceBytes
            )
        )
    }

    fun createLibraryBackup() {
        scope.launch {
            loading = true

            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    ProjectBackupStore
                        .exportBackup(
                            context
                        )
                }

            loading = false

            result.fold(
                onSuccess = {
                        bytes ->
                    val fileName =
                        "Brother-Matrizes-backup.brother_matrizes-backup"

                    stageDocumentSave(
                        DurablePendingDocument(
                            fileName =
                                fileName,
                            bytes =
                                bytes,
                            successMessage =
                                "Backup de Minhas Matrizes salvo com sucesso."
                        )
                    )
                },
                onFailure = {
                    snackbar.showSnackbar(
                        it.message
                            ?: "Não foi possível criar o backup."
                    )
                }
            )
        }
    }

    fun openProjectLibrary() {
        scope.launch {
            loading = true

            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    ProjectStore.list(
                        context
                    )
                }

            loading = false

            result.fold(
                onSuccess = {
                        projects ->
                    savedProjects =
                        projects

                    refreshLibraryActivity()

                    screen =
                        Screen.ProjectLibrary
                },
                onFailure = {
                    snackbar.showSnackbar(
                        "Não foi possível abrir Minhas Matrizes."
                    )
                }
            )
        }
    }

    fun saveProject(
        design: EmbroideryDesign
    ) {
        scope.launch {
            loading = true

            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    ProjectStore.save(
                        context,
                        design
                    )
                }

            loading = false

            result.fold(
                onSuccess = {
                        saved ->
                    savedProjects =
                        listOf(
                            saved
                        ) +
                            savedProjects
                                .filter {
                                    it.id !=
                                        saved.id
                                }

                    snackbar.showSnackbar(
                        "Matriz salva em Minhas Matrizes."
                    )
                },
                onFailure = {
                    snackbar.showSnackbar(
                        "Não foi possível salvar a matriz no app."
                    )
                }
            )
        }
    }

    fun loadProject(
        project: SavedProjectSummary,
        transferDirectly: Boolean
    ) {
        scope.launch {
            loading = true

            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    ProjectStore.load(
                        context,
                        project.id
                    )
                }

            loading = false

            result.fold(
                onSuccess = {
                        design ->
                    activateDesign(
                        design
                    )

                    LibraryActivityStore
                        .markRecent(
                            context,
                            project.id
                        )

                    refreshLibraryActivity()

                    screen =
                        if (
                            transferDirectly
                        ) {
                            Screen.Transfer(
                                design
                            )
                        } else {
                            Screen.Viewer(
                                design
                            )
                        }
                },
                onFailure = {
                    snackbar.showSnackbar(
                        "Não foi possível reabrir esta matriz."
                    )
                }
            )
        }
    }

    fun deleteProject(
        project: SavedProjectSummary
    ) {
        scope.launch {
            loading = true

            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    ProjectStore.delete(
                        context,
                        project.id
                    )
                }

            loading = false

            result.fold(
                onSuccess = {
                    savedProjects =
                        savedProjects
                            .filter {
                                it.id !=
                                    project.id
                            }

                    LibraryActivityStore
                        .removeProjectMetadata(
                            context,
                            project.id
                        )

                    refreshLibraryActivity()

                    snackbar.showSnackbar(
                        "Matriz removida de Minhas Matrizes."
                    )
                },
                onFailure = {
                    snackbar.showSnackbar(
                        "Não foi possível excluir esta matriz."
                    )
                }
            )
        }
    }

    fun toggleFavorite(
        project: SavedProjectSummary
    ) {
        val favorite =
            LibraryActivityStore
                .toggleFavorite(
                    context,
                    project.id
                )

        refreshLibraryActivity()

        scope.launch {
            snackbar.showSnackbar(
                if (
                    favorite
                ) {
                    "Matriz adicionada aos favoritos."
                } else {
                    "Matriz removida dos favoritos."
                }
            )
        }
    }

    Scaffold(
        containerColor =
            FioBackground,
        snackbarHost = {
            SnackbarHost(
                snackbar
            )
        },
        bottomBar = {
            val topLevelScreen =
                screen == Screen.Home ||
                    screen == Screen.CreateName ||
                    screen == Screen.ProjectLibrary ||
                    screen == Screen.FontLibrary ||
                    screen == Screen.Account

            if (topLevelScreen) {
                NavigationBar(
                    containerColor =
                        FioSurface,
                    tonalElevation =
                        4.dp
                ) {
                    val navigationColors =
                        NavigationBarItemDefaults
                            .colors(
                                selectedIconColor =
                                    FioBackground,
                                selectedTextColor =
                                    FioGold,
                                indicatorColor =
                                    FioGold,
                                unselectedIconColor =
                                    FioTextMuted,
                                unselectedTextColor =
                                    FioTextMuted
                            )

                    NavigationBarItem(
                        selected =
                            screen ==
                                Screen.Home,
                        onClick = {
                            screen =
                                Screen.Home
                        },
                        icon = {
                            Icon(
                                painter =
                                    painterResource(
                                        R.drawable
                                            .ic_ms_home_rounded
                                    ),
                                contentDescription =
                                    "Início"
                            )
                        },
                        label = {
                            Text("Início")
                        },
                        colors =
                            navigationColors
                    )

                    NavigationBarItem(
                        selected =
                            screen ==
                                Screen.CreateName,
                        onClick = {
                            screen =
                                Screen.CreateName
                        },
                        icon = {
                            Icon(
                                painter =
                                    painterResource(
                                        R.drawable
                                            .ic_ms_text_fields_rounded
                                    ),
                                contentDescription =
                                    "Criar"
                            )
                        },
                        label = {
                            Text("Criar")
                        },
                        colors =
                            navigationColors
                    )

                    NavigationBarItem(
                        selected =
                            screen ==
                                Screen.ProjectLibrary,
                        onClick = {
                            if (
                                screen !=
                                    Screen.ProjectLibrary
                            ) {
                                openProjectLibrary()
                            }
                        },
                        icon = {
                            Icon(
                                painter =
                                    painterResource(
                                        R.drawable
                                            .ic_ms_folder_open_rounded
                                    ),
                                contentDescription =
                                    "Biblioteca"
                            )
                        },
                        label = {
                            Text("Biblioteca")
                        },
                        colors =
                            navigationColors
                    )

                    NavigationBarItem(
                        selected =
                            screen ==
                                Screen.FontLibrary,
                        onClick = {
                            screen =
                                Screen.FontLibrary
                        },
                        icon = {
                            Icon(
                                painter =
                                    painterResource(
                                        R.drawable
                                            .ic_ms_font_download_rounded
                                    ),
                                contentDescription =
                                    "Fontes"
                            )
                        },
                        label = {
                            Text("Fontes")
                        },
                        colors =
                            navigationColors
                    )

                    NavigationBarItem(
                        selected =
                            screen ==
                                Screen.Account,
                        onClick = {
                            screen =
                                Screen.Account
                        },
                        icon = {
                            Icon(
                                painter =
                                    painterResource(
                                        R.drawable
                                            .ic_ms_account_circle_rounded
                                    ),
                                contentDescription =
                                    "Conta"
                            )
                        },
                        label = {
                            Text("Conta")
                        },
                        colors =
                            navigationColors
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (
                val current =
                    screen
            ) {
                Screen.Home -> {
                    HomeScreen(
                        recent = recent,
                        onCreateName = {
                            screen =
                                Screen.CreateName
                        },
                        onFonts = {
                            screen =
                                Screen.FontLibrary
                        },
                        onTransfer = {
                            recent?.let {
                                screen =
                                    Screen.Transfer(
                                        it
                                    )
                            }
                        },
                        onOpen = {
                            picker.launch(
                                arrayOf("*/*")
                            )
                        },
                        onRecent = {
                            recent?.let {
                                screen =
                                    Screen
                                        .Viewer(it)
                            }
                        },
                        onSimulate = {
                            recent?.let {
                                screen =
                                    Screen
                                        .Simulator(
                                            it
                                        )
                            }
                        },
                        onEdit = {
                            recent?.let {
                                screen =
                                    Screen
                                        .Editor(
                                            it
                                        )
                            }
                        },
                        onConvert = {
                            recent?.let {
                                screen =
                                    Screen
                                        .Converter(
                                            it
                                        )
                            }
                        },
                        onClearRecent = {
                            clearRecentDesign()
                        },
                        onUnavailable = {
                                message ->
                            scope.launch {
                                snackbar
                                    .showSnackbar(
                                        message
                                    )
                            }
                        }
                    )
                }

                Screen.Account -> {
                    AccountHostScreen(
                        onBack = {
                            goBack()
                        },
                        refreshRequest =
                            openAccountRequest,
                        textScale =
                            textScale,
                        onTextScaleChange =
                            onTextScaleChange,
                        colorMode = colorMode,
                        onColorModeChange = onColorModeChange
                    )
                }

                Screen.ProjectLibrary -> {
                    ProjectLibraryScreen(
                        projects =
                            savedProjects,
                        favoriteProjectIds =
                            favoriteProjectIds,
                        recentProjectIds =
                            recentProjectIds,
                        sentMatrices =
                            sentMatrices,
                        onBack = {
                            goBack()
                        },
                        onOpen = {
                                project ->
                            loadProject(
                                project,
                                transferDirectly =
                                    false
                            )
                        },
                        onTransfer = {
                                project ->
                            loadProject(
                                project,
                                transferDirectly =
                                    true
                            )
                        },
                        onToggleFavorite = {
                                project ->
                            toggleFavorite(
                                project
                            )
                        },
                        onDelete = {
                                project ->
                            deleteProject(
                                project
                            )
                        },
                        onDeleteSent = {
                                record ->
                            removeSentRecord(
                                record
                            )
                        },
                        onClearSent = {
                            clearSentHistory()
                        },
                        onBackup = {
                            createLibraryBackup()
                        },
                        onRestore = {
                            restoreBackupLauncher
                                .launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/octet-stream",
                                        "*/*"
                                    )
                                )
                        },
                        onFonts = {
                            screen =
                                Screen.FontLibrary
                        },
                        onSearchMatrices = {
                            screen = Screen.TelegramSearch
                        }
                    )
                }

                is Screen.ArchiveImport -> {
                    ArchiveExtractorScreen(
                        archiveUri = current.uri,
                        onBack = { goBack() },
                        onImported = { items, first ->
                            savedProjects = (items + savedProjects).distinctBy { it.id }
                            // Keep the extractor visible to allow multiple batches.
                            // Imported projects can be opened from Biblioteca.
                            scope.launch {
                                snackbar.showSnackbar(
                                    "${items.size} matriz(es) salvas na Biblioteca."
                                )
                            }
                        },
                        canImport = { category ->
                            BrotherMatrizesAccountService.authorizeImport(
                                category == ArchiveCategory.FONT
                            ).getOrThrow()
                        },
                        onImportFinished = { category, key, success ->
                            BrotherMatrizesAccountService.finalizeImport(
                                category == ArchiveCategory.FONT, key, success
                            ).getOrThrow()
                        }
                    )
                }

                Screen.TelegramSearch -> {
                    TelegramMatrixSearchScreen(
                        projects = savedProjects,
                        onBack = { goBack() },
                        onImport = {
                            telegramImportPending = true
                            picker.launch(arrayOf("*/*"))
                        },
                        onOpenSavedProject = { saved ->
                            loadProject(saved, transferDirectly = false)
                        }
                    )
                }

                Screen.FontLibrary -> {
                    FontLibraryScreen(
                        onBack = {
                            goBack()
                        }
                    )
                }

                Screen.CreateName -> {
                    CreateNameScreen(
                        onBack = {
                            goBack()
                        },
                        onCreate = {
                                created,
                                displayMode ->
                            scope.launch {
                                loading =
                                    true

                                val opened =
                                    withContext(
                                        Dispatchers.IO
                                    ) {
                                        GeneratedMatrixPipeline
                                            .canonicalize(
                                                design =
                                                    created,
                                                outputSuffix =
                                                    "criado"
                                            )
                                    }

                                loading =
                                    false

                                when (
                                    opened
                                ) {
                                    is EmbroideryLoadResult
                                        .Success -> {
                                        activateDesign(
                                            opened.design
                                        )

                                        screen =
                                            Screen.Viewer(
                                                design =
                                                    opened.design,
                                                displayMode =
                                                    displayMode
                                            )
                                    }

                                    is EmbroideryLoadResult
                                        .Error -> {
                                        snackbar.showSnackbar(
                                            opened.userMessage
                                        )
                                    }
                                }
                            }
                        },
                        onSimulate = {
                                created,
                                displayMode ->
                            /*
                             * Criar Nome -> Simular deve atravessar exatamente
                             * o mesmo caminho de uma matriz física aberta:
                             *
                             * criação interna -> PES/DST/JEF -> parser ->
                             * EmbroideryDesign canônico -> Simulador.
                             *
                             * Isso elimina a divergência histórica em que a
                             * prévia bruta era simulada com comandos diferentes
                             * daqueles efetivamente gravados e reabertos.
                             */
                            scope.launch {
                                loading =
                                    true

                                val opened =
                                    withContext(
                                        Dispatchers.IO
                                    ) {
                                        GeneratedMatrixPipeline
                                            .canonicalize(
                                                design =
                                                    created,
                                                outputSuffix =
                                                    "simulado",
                                                preserveGuidePoints =
                                                    true
                                            )
                                    }

                                loading =
                                    false

                                when (
                                    opened
                                ) {
                                    is EmbroideryLoadResult
                                        .Success -> {
                                        val canonical =
                                            opened.design

                                        activateDesign(
                                            canonical
                                        )

                                        DebugTelemetry
                                            .captureSimulationOpened(
                                                canonical
                                            )

                                        screen =
                                            Screen.Simulator(
                                                design =
                                                    canonical,
                                                displayMode =
                                                    displayMode,
                                                referenceHoop =
                                                    canonical.hoopProfile
                                                        ?: recommendedHoopFor(
                                                            canonical
                                                        ),
                                                showConnections =
                                                    false
                                            )
                                    }

                                    is EmbroideryLoadResult
                                        .Error -> {
                                        snackbar.showSnackbar(
                                            opened.userMessage
                                        )
                                    }
                                }
                            }
                        }
                    )
                }

                Screen.CreateDrawing -> {
                    CreateDrawingScreen(
                        onBack = {
                            goBack()
                        },
                        onCreate = {
                                created ->
                            activateDesign(
                                created
                            )

                            screen =
                                Screen.Viewer(
                                    created
                                )
                        },
                        onSimulate = {
                                created ->
                            activateDesign(
                                created
                            )

                            screen =
                                Screen.Simulator(
                                    created
                                )
                        }
                    )
                }

                Screen.CreateMonogram -> {
                    MonogramScreen(
                        onBack = {
                            goBack()
                        },
                        onCreate = {
                                created ->
                            activateDesign(
                                created
                            )

                            screen =
                                Screen.Viewer(
                                    created
                                )
                        },
                        onSimulate = {
                                created ->
                            activateDesign(
                                created
                            )

                            screen =
                                Screen.Simulator(
                                    created
                                )
                        }
                    )
                }

                is Screen.Transfer -> {
                    MachineTransferScreen(
                        design =
                            current.design,
                        onBack = {
                            goBack()
                        },
                        onUsbOtg = {
                                format ->
                            convertForSave(
                                design =
                                    current.design,
                                targetFormat =
                                    format,
                                suffix =
                                    "maquina"
                            )
                        },
                        onWifi = {
                                format ->
                            convertForShare(
                                design =
                                    current.design,
                                targetFormat =
                                    format,
                                suffix =
                                    "maquina",
                                chooserTitle =
                                    "Enviar matriz por Wi-Fi / app da máquina"
                            )
                        }
                    )
                }

                is Screen.Viewer -> {
                    ViewerScreen(
                        design =
                            current.design,
                        displayMode =
                            current.displayMode,
                        onDisplayModeChange = {
                                mode ->
                            screen =
                                current.copy(
                                    displayMode =
                                        mode
                                )
                        },
                        referenceHoop =
                            current.referenceHoop,
                        onReferenceHoopChange = {
                                hoop ->
                            screen =
                                current.copy(
                                    referenceHoop =
                                        hoop
                                )
                        },
                        showConnections =
                            current.showConnections,
                        onShowConnectionsChange = {
                                enabled ->
                            screen =
                                current.copy(
                                    showConnections =
                                        enabled
                                )
                        },
                        onBack = {
                            goBack()
                        },
                        onOpen = {
                            picker.launch(
                                arrayOf("*/*")
                            )
                        },
                        onSimulate = {
                            screen =
                                Screen
                                    .Simulator(
                                        design =
                                            current.design,
                                        displayMode =
                                            current.displayMode,
                                        referenceHoop =
                                            current.referenceHoop,
                                        showConnections =
                                            current.showConnections
                                    )
                        },
                        onEdit = {
                            screen =
                                Screen
                                    .Editor(
                                        design =
                                            current.design,
                                        displayMode =
                                            current.displayMode,
                                        referenceHoop =
                                            current.referenceHoop,
                                        showConnections =
                                            current.showConnections
                                    )
                        },
                        onConvert = {
                            screen =
                                Screen
                                    .Converter(
                                        current
                                            .design
                                    )
                        },
                        onTransfer = {
                            screen =
                                Screen.Transfer(
                                    current.design
                                )
                        },
                        onSaveProject = {
                            saveProject(
                                current
                                    .design
                            )
                        },
                        onSaveCopy = {
                            requestSave(
                                current
                                    .design
                            )
                        },
                        onShare = {
                            requestShare(
                                current
                                    .design
                            )
                        }
                    )
                }

                is Screen.Simulator -> {
                    SimulatorScreen(
                        design =
                            current.design,
                        displayMode =
                            current.displayMode,
                        referenceHoop =
                            current.referenceHoop,
                        showConnections =
                            current.showConnections,
                        onBack = {
                            goBack()
                        }
                    )
                }

                is Screen.Converter -> {
                    ConverterScreen(
                        design =
                            current.design,
                        onBack = {
                            goBack()
                        },
                        onSave = {
                                format ->
                            convertForSave(
                                design =
                                    current
                                        .design,
                                targetFormat =
                                    format
                            )
                        },
                        onShare = {
                                format ->
                            convertForShare(
                                design =
                                    current
                                        .design,
                                targetFormat =
                                    format
                            )
                        }
                    )
                }

                is Screen.Editor -> {
                    EditorScreen(
                        design =
                            current.design,
                        onBack = {
                            goBack()
                        },
                        onApply = {
                                edited ->
                            activateDesign(
                                edited
                            )

                            screen =
                                Screen
                                    .Viewer(
                                        design =
                                            edited,
                                        displayMode =
                                            current.displayMode,
                                        referenceHoop =
                                            current.referenceHoop,
                                        showConnections =
                                            current.showConnections
                                    )
                        }
                    )
                }
            }

            if (loading) {
                CircularProgressIndicator(
                    modifier =
                        Modifier.align(
                            Alignment.Center
                        ),
                    color =
                        FioGold
                )
            }
        }
    }
}
