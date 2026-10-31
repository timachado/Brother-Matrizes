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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
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
import com.timachado.brothermatrizes.core.embroidery.NamePersonalization
import com.timachado.brothermatrizes.font.ImportedFontStore
import com.timachado.brothermatrizes.font.ImportedFontMatrixGenerator
import com.timachado.brothermatrizes.core.embroidery.MatrixExporter
import com.timachado.brothermatrizes.core.archive.ArchiveCategory
import com.timachado.brothermatrizes.core.archive.SafeArchiveExtractor
import com.timachado.brothermatrizes.ui.updates.BrotherVersionGate
import com.timachado.brothermatrizes.ui.updates.BrotherUpdateReminder
import com.timachado.brothermatrizes.ui.updates.BrotherPostUpdateNews
import com.timachado.brothermatrizes.ui.updates.BrotherFirebasePushOptIn
import com.timachado.brothermatrizes.core.updates.BrotherUpdateNotificationWorker
import com.timachado.brothermatrizes.core.account.BrotherMatrizesAccountService
import com.timachado.brothermatrizes.core.library.LibraryActivityStore
import com.timachado.brothermatrizes.core.library.SentMatrixRecord
import com.timachado.brothermatrizes.core.project.ActiveDesignStore
import com.timachado.brothermatrizes.core.project.CompleteLibraryBackupStore
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
import com.timachado.brothermatrizes.ui.theme.FioNavigationSurface
import com.timachado.brothermatrizes.ui.theme.FioNavigationMuted
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    // A tela Início verifica novidades ao retornar do background ou instalação.
    private var updateResumeRequest by mutableStateOf(0)
    private var openHomeUpdatesRequest by mutableStateOf(0)
    private var openCommunityRequest by mutableStateOf(0)

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

        // A verificação periódica continua após o aplicativo ser fechado.
        // Não usa FCM/Supabase nem consulta assinaturas comerciais.
        runCatching { BrotherUpdateNotificationWorker.schedule(applicationContext) }

        DebugTelemetry.setup(
            applicationContext
        )

        captureExternalOpenIntent(
            intent
        )

        if (intent.getBooleanExtra(EXTRA_OPEN_ACCOUNT_FROM_AUTH, false)) {
            accountOpenRequest++
        }
        if (intent.action == "com.timachado.brothermatrizes.OPEN_UPDATES") {
            getSharedPreferences("brother_update_home_cards", MODE_PRIVATE).edit()
                .remove("dismissed_version_code").apply()
            openHomeUpdatesRequest++
        }

        if (intent.action == "com.timachado.brothermatrizes.OPEN_COMMUNITY") {
            openCommunityRequest++
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
                BrotherVersionGate(resumeRequest = updateResumeRequest) {
                BrotherFirebasePushOptIn()
                BrotherUpdateReminder(
                    endpoint = BuildConfig.WORDPRESS_URL.trimEnd('/') +
                        "/wp-json/tim-brother-updates/v1/latest?channel=stable"
                )
                BrotherPostUpdateNews()
                BrotherMatrizesApp(
                    openAccountRequest =
                        accountOpenRequest,
                    openHomeUpdatesRequest = openHomeUpdatesRequest,
                    openCommunityRequest = openCommunityRequest,
                    updateResumeRequest = updateResumeRequest,
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

        if (intent.getBooleanExtra(EXTRA_OPEN_ACCOUNT_FROM_AUTH, false)) {
            accountOpenRequest++
        }
        if (intent.action == "com.timachado.brothermatrizes.OPEN_UPDATES") {
            getSharedPreferences("brother_update_home_cards", MODE_PRIVATE).edit()
                .remove("dismissed_version_code").apply()
            openHomeUpdatesRequest++
        }
        if (intent.action == "com.timachado.brothermatrizes.OPEN_COMMUNITY") {
            openCommunityRequest++
        }
    }

    override fun onResume() {
        super.onResume()
        updateResumeRequest++
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
                    hoop.usableWidthMm &&
                designHeight <=
                    hoop.usableHeightMm
                ) ||
                (
                    designWidth <=
                        hoop.usableHeightMm &&
                    designHeight <=
                        hoop.usableWidthMm
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
    data class ArchiveImport(
        val uri: Uri,
        val returnTo: Screen = ProjectLibrary
    ) : Screen
    data object Account : Screen
    data object Community : Screen

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
            false,
        val rebuildStatus: String? = null,
        val rebuildInProgress: Boolean = false
    ) : Screen

    data class Converter(
        val design: EmbroideryDesign
    ) : Screen

    data class NameEditor(
        val design: EmbroideryDesign,
        val referenceHoop: HoopProfile = recommendedHoopFor(design)
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
    openHomeUpdatesRequest: Int = 0,
    openCommunityRequest: Int = 0,
    updateResumeRequest: Int = 0,
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

    LaunchedEffect(openHomeUpdatesRequest) {
        if (openHomeUpdatesRequest > 0) {
            // Tocar na notificação abre o cartão de novidades na Home.
            screen = Screen.Home
        }
    }

    LaunchedEffect(openCommunityRequest) {
        if (openCommunityRequest > 0) {
            screen = Screen.Community
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

    LaunchedEffect(externalOpenUri, openAccountRequest) {
        val uri =
            externalOpenUri
                ?: return@LaunchedEffect

        val google = BrotherMatrizesAccountService.ensureGoogleSession()
        if (google.isFailure) {
            screen = Screen.Account
            snackbar.showSnackbar(google.exceptionOrNull()?.message
                ?: "Entre com Google para abrir a matriz.")
            return@LaunchedEffect
        }
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
        val permit = BrotherMatrizesAccountService.authorizeImport(context, isFont = false)
        if (permit.isFailure) {
            loading = false
            snackbar.showSnackbar(permit.exceptionOrNull()?.message
                ?: "Sua cota de matrizes não está disponível.")
            onExternalOpenConsumed(uri)
            return@LaunchedEffect
        }
        val quotaKey = permit.getOrThrow()
        val result =
            withContext(
                Dispatchers.IO
            ) {
                EmbroideryLoader.load(
                    context.contentResolver,
                    uri
                )
            }

        val registration = BrotherMatrizesAccountService.finalizeImport(
            context, isFont = false, requestKey = quotaKey,
            success = result is EmbroideryLoadResult.Success
        )
        if (registration.isFailure) snackbar.showSnackbar(
            "Falha ao confirmar a cota. Verifique sua conexão."
        )
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
                Screen.Community,
                Screen.ProjectLibrary,
                Screen.TelegramSearch,
                Screen.FontLibrary,
                Screen.CreateName,
                Screen.CreateMonogram,
                Screen.CreateDrawing ->
                    Screen.Home

                is Screen.ArchiveImport -> current.returnTo

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

                is Screen.NameEditor ->
                    Screen.Viewer(
                        design = current.design,
                        referenceHoop = current.referenceHoop
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
                val origin = if (telegramImportPending) Screen.TelegramSearch
                    else Screen.ProjectLibrary
                telegramImportPending = false
                screen = Screen.ArchiveImport(uri, origin)
                return@rememberLauncherForActivityResult
            }

            scope.launch {
                // QA de homologação isolada: testar um PES local não depende
                // da autenticação Google do pacote comercial nem debita cotas.
                // A exceção exige simultaneamente build DEBUG e applicationId
                // exclusivo; NUNCA se aplica ao aplicativo de produção.
                val localNameQaImport = NameQaImportPolicy.allowLocalImportWithoutAccount(
                    debugBuild = BuildConfig.DEBUG,
                    applicationId = context.packageName
                )
                val quotaKey = if (localNameQaImport) {
                    null
                } else {
                    val permit = BrotherMatrizesAccountService.authorizeImport(
                        context, isFont = false
                    )
                    if (permit.isFailure) {
                        telegramImportPending = false
                        snackbar.showSnackbar(permit.exceptionOrNull()?.message
                            ?: "Login Google ou cota de matrizes indisponível.")
                        if (BrotherMatrizesAccountService.ensureGoogleSession().isFailure) {
                            screen = Screen.Account
                        }
                        return@launch
                    }
                    permit.getOrThrow()
                }
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

                // A confirmação de consumo só existe na importação comercial.
                if (quotaKey != null) {
                    val registered = BrotherMatrizesAccountService.finalizeImport(
                        context, isFont = false, requestKey = quotaKey,
                        success = result is EmbroideryLoadResult.Success
                    )
                    if (registered.isFailure) {
                        snackbar.showSnackbar("Não foi possível confirmar a cota da importação.")
                    }
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

    fun launchMatrixPicker(fromTelegram: Boolean = false) {
        scope.launch {
            val localNameQaImport = NameQaImportPolicy.allowLocalImportWithoutAccount(
                debugBuild = BuildConfig.DEBUG,
                applicationId = context.packageName
            )
            if (!localNameQaImport) {
                val google = BrotherMatrizesAccountService.ensureGoogleSession()
                if (google.isFailure) {
                    screen = Screen.Account
                    snackbar.showSnackbar("Entre com Google antes de abrir ou importar matrizes.")
                    return@launch
                }
            }
            telegramImportPending = fromTelegram
            picker.launch(arrayOf("*/*"))
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
                                                    192 *
                                                        1024 *
                                                        1024
                                            )
                                    }
                                    ?: error(
                                        "Não foi possível ler o backup."
                                    )

                            val count =
                                CompleteLibraryBackupStore
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
                            restored.first.projects.toString() +
                                " matriz(es) e " +
                                restored.first.fonts.toString() +
                                " fonte(s) restaurada(s)."
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
                    CompleteLibraryBackupStore
                        .exportBackup(
                            context
                        )
                }

            loading = false

            result.fold(
                onSuccess = {
                        bytes ->
                    val fileName =
                        "Brother-Matrizes-backup-completo.brother_matrizes-fullbackup"

                    stageDocumentSave(
                        DurablePendingDocument(
                            fileName =
                                fileName,
                            bytes =
                                bytes,
                            successMessage =
                                "Backup completo de matrizes e fontes salvo com sucesso."
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
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .testTag("brother_premium_bottom_bar"),
                    containerColor =
                        FioNavigationSurface,
                    // Sem tonalizacao Material 3 para preservar o azul exato.
                    tonalElevation =
                        0.dp
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
                                    FioNavigationMuted,
                                unselectedTextColor =
                                    FioNavigationMuted
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
                        updateRefreshRequest = updateResumeRequest + openHomeUpdatesRequest,
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
                        onOpen = { launchMatrixPicker() },
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
                        onCommunity = { screen = Screen.Community },
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

                Screen.Community -> {
                    BrotherCommunityScreen(onBack = { goBack() })
                }

                Screen.Account -> {
                    AccountHostScreen(
                        onBack = {
                            goBack()
                        },
                        onOpenSupport = {
                            screen = Screen.Community
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
                        onRequireGoogle = { screen = Screen.Account },
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
                                context, category == ArchiveCategory.FONT
                            ).getOrThrow()
                        },
                        onImportFinished = { category, key, success ->
                            BrotherMatrizesAccountService.finalizeImport(
                                context, category == ArchiveCategory.FONT, key, success
                            ).getOrThrow()
                        }
                    )
                }

                Screen.TelegramSearch -> {
                    TelegramMatrixSearchScreen(
                        projects = savedProjects,
                        onBack = { goBack() },
                        onImport = {
                            launchMatrixPicker(fromTelegram = true)
                        },
                        onRequireGoogle = { screen = Screen.Account },
                        onOpenSavedProject = { saved ->
                            loadProject(saved, transferDirectly = false)
                        }
                    )
                }

                Screen.FontLibrary -> {
                    FontLibraryScreen(
                        onBack = { goBack() },
                        onRequireGoogle = { screen = Screen.Account }
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
                        onOpen = { launchMatrixPicker() },
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
                        onEditName = {
                            screen = Screen.NameEditor(
                                design = current.design,
                                referenceHoop = current.referenceHoop
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
                        },
                        onPositionChange = { placed ->
                            activateDesign(placed)
                            screen = current.copy(
                                design = placed,
                                referenceHoop = placed.hoopProfile ?: current.referenceHoop
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
                        rebuildStatus = current.rebuildStatus,
                        rebuildInProgress = current.rebuildInProgress,
                        onBack = {
                            goBack()
                        },
                        onEditName = if (
                            current.design.editableName?.options?.importedFontId != null
                        ) {
                            {
                                screen = Screen.NameEditor(
                                    design = current.design,
                                    referenceHoop = current.referenceHoop
                                )
                            }
                        } else null,
                        onRebuildName = if (
                            current.design.editableName?.options?.importedFontId != null
                        ) {
                            {
                                if (!loading && !current.rebuildInProgress) {
                                    loading = true
                                    val previous = current.design
                                    val previousMode = current.displayMode
                                    val previousHoop = current.referenceHoop
                                    val previousConnections = current.showConnections
                                    val originalCode = stitchSequenceFingerprint(previous)
                                    screen = current.copy(
                                        rebuildStatus = null,
                                        rebuildInProgress = true
                                    )
                                    scope.launch {
                                        try {
                                            val recalculated = withContext(Dispatchers.Default) {
                                                val layer = requireNotNull(previous.editableName)
                                                val fontId = requireNotNull(layer.options.importedFontId)
                                                val font = ImportedFontStore.list(context)
                                                    .firstOrNull { it.id == fontId }
                                                    ?: throw IllegalStateException(
                                                        "A fonte desta matriz não está instalada neste APK. " +
                                                            "Reimporte a mesma Bertilda TTF/OTF no editor."
                                                    )
                                                NamePersonalization.invalidatePreparedPreview()
                                                NamePersonalization.add(
                                                    original = previous,
                                                    options = NamePersonalization.optionsForSatinRegeneration(
                                                        layer.options
                                                    ).copy(
                                                        outputFormat = previous.format,
                                                        hoopProfile = previousHoop,
                                                        enforceHoop = true
                                                    ),
                                                    centerXUnits = layer.centerXUnits,
                                                    centerYUnits = layer.centerYUnits,
                                                    importedGenerator = { options ->
                                                        ImportedFontMatrixGenerator.generateText(
                                                            font, options.text, options
                                                        )
                                                    }
                                                ).getOrThrow()
                                            }
                                            if (recalculated.points == previous.points) {
                                                screen = current.copy(
                                                    rebuildStatus =
                                                        "Sem alteração: sequência " + originalCode +
                                                        ". Estilo original: " + 
                                                        previous.editableName?.options?.style?.displayName +
                                                        ". Testado com motor Satin vetorial; " +
                                                        "as pontadas geradas permaneceram iguais. " +
                                                        "A matriz original foi preservada.",
                                                    rebuildInProgress = false
                                                )
                                            } else {
                                                val newCode = stitchSequenceFingerprint(recalculated)
                                                activateDesign(recalculated)
                                                screen = Screen.Simulator(
                                                    design = recalculated,
                                                    displayMode = previousMode,
                                                    referenceHoop = previousHoop,
                                                    showConnections = previousConnections,
                                                    rebuildStatus =
                                                        "Pontadas NOVAS: " + originalCode + " -> " +
                                                        newCode + " (" +
                                                        previous.stitchCount + " -> " +
                                                        recalculated.stitchCount + " pontos). " +
                                                        "Ainda não liberado para a máquina.",
                                                    rebuildInProgress = false
                                                )
                                            }
                                        } catch (error: Exception) {
                                            screen = current.copy(
                                                rebuildStatus =
                                                    "REPROCESSAMENTO FALHOU: " +
                                                    (error.message ?: error.javaClass.simpleName) +
                                                    ". A sequência " + originalCode + " NÃO mudou.",
                                                rebuildInProgress = false
                                            )
                                        } finally {
                                            loading = false
                                        }
                                    }
                                }
                            }
                        } else null
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

                is Screen.NameEditor -> {
                    NameEditorScreen(
                        original = current.design,
                        referenceHoop = current.referenceHoop,
                        onBack = { goBack() },
                        onApply = { personalized ->
                            activateDesign(personalized)
                            screen = Screen.Viewer(
                                design = personalized,
                                referenceHoop = personalized.hoopProfile ?: current.referenceHoop
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
