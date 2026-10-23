package com.timachado.brothermatrizes.core.telegramvision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.timachado.brothermatrizes.BuildConfig
import io.github.tdlibandroid.ktx.TdClient
import io.github.tdlibandroid.ktx.awaitReady
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.io.File

enum class TelegramAuthorizationStage {
    NOT_CONFIGURED, INITIALIZING, PHONE, EMAIL, EMAIL_CODE, CODE, PASSWORD, READY, NEEDS_OTHER_METHOD, CLOSED
}

/**
 * Telegram account authorization is separate from Brother Google account.
 * TDLib session remains only in Android app-private files, never on our servers.
 */
class TelegramGroupSession private constructor(private val context: Context) :
    TelegramGroupMediaSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client: TdClient?
    private val _stage = MutableStateFlow(TelegramAuthorizationStage.INITIALIZING)
    val stage: StateFlow<TelegramAuthorizationStage> get() = _stage

    init {
        client = if (BuildConfig.BROTHER_TELEGRAM_API_ID > 0 &&
            BuildConfig.BROTHER_TELEGRAM_API_HASH.matches(Regex("[0-9a-fA-F]{32}"))
        ) {
            // Native library from the pinned verified TDLib Android dependency.
            System.loadLibrary("tdjni")
            // No TDLib session is imported from the separate Telegram Android app.
            TdClient(
                filesDir = File(context.filesDir, "telegram-vision-session").absolutePath,
                apiId = BuildConfig.BROTHER_TELEGRAM_API_ID,
                apiHash = BuildConfig.BROTHER_TELEGRAM_API_HASH,
                verbosityLevel = 1
            ).also { it.init() }
        } else {
            _stage.value = TelegramAuthorizationStage.NOT_CONFIGURED
            null
        }
        client?.let { td ->
            scope.launch {
                td.updates.collect { update ->
                    if (update is TdApi.UpdateAuthorizationState) {
                        _stage.value = stageFor(update.authorizationState)
                    }
                }
            }
            // Reconcile the current state after subscribing, in case the
            // initial authorization update arrived while the UI was attaching.
            scope.launch {
                runCatching { td.send(TdApi.GetAuthorizationState()) }
                    .onSuccess { _stage.value = stageFor(it) }
            }
        }
    }

    private fun stageFor(state: TdApi.AuthorizationState): TelegramAuthorizationStage =
        when (state) {
            is TdApi.AuthorizationStateReady -> TelegramAuthorizationStage.READY
            is TdApi.AuthorizationStateWaitPhoneNumber -> TelegramAuthorizationStage.PHONE
            is TdApi.AuthorizationStateWaitEmailAddress -> TelegramAuthorizationStage.EMAIL
            is TdApi.AuthorizationStateWaitEmailCode -> TelegramAuthorizationStage.EMAIL_CODE
            is TdApi.AuthorizationStateWaitCode -> TelegramAuthorizationStage.CODE
            is TdApi.AuthorizationStateWaitPassword -> TelegramAuthorizationStage.PASSWORD
            is TdApi.AuthorizationStateClosed -> TelegramAuthorizationStage.CLOSED
            is TdApi.AuthorizationStateWaitTdlibParameters ->
                TelegramAuthorizationStage.INITIALIZING
            else -> TelegramAuthorizationStage.NEEDS_OTHER_METHOD
        }

    companion object {
        @Volatile private var current: TelegramGroupSession? = null

        fun get(context: Context): TelegramGroupSession =
            current ?: synchronized(this) {
                current ?: TelegramGroupSession(context.applicationContext).also { current = it }
            }
    }

    suspend fun submitPhone(number: String) {
        require(_stage.value == TelegramAuthorizationStage.PHONE)
        require(number.matches(Regex("\\+[1-9][0-9]{6,14}"))) { "Informe o telefone com DDI." }
        session().send(TdApi.SetAuthenticationPhoneNumber(number, null))
    }

    suspend fun submitEmail(email: String) {
        require(_stage.value == TelegramAuthorizationStage.EMAIL)
        val trimmed = email.trim()
        require(trimmed.length in 6..254 && trimmed.contains("@") && !trimmed.contains(" ")) {
            "Informe um e-mail válido."
        }
        session().send(TdApi.SetAuthenticationEmailAddress(trimmed))
    }

    suspend fun submitEmailCode(code: String) {
        require(_stage.value == TelegramAuthorizationStage.EMAIL_CODE)
        require(code.length in 4..12 && code.all(Char::isDigit)) {
            "Código de e-mail inválido."
        }
        session().send(
            TdApi.CheckAuthenticationEmailCode(TdApi.EmailAddressAuthenticationCode(code))
        )
    }

    suspend fun submitCode(code: String) {
        require(_stage.value == TelegramAuthorizationStage.CODE)
        require(code.length in 4..10 && code.all(Char::isDigit)) { "Código inválido." }
        session().send(TdApi.CheckAuthenticationCode(code))
    }

    suspend fun submitPassword(password: String) {
        require(_stage.value == TelegramAuthorizationStage.PASSWORD)
        require(password.isNotBlank())
        session().send(TdApi.CheckAuthenticationPassword(password))
    }

    suspend fun logout() {
        val td = session()
        td.send(TdApi.LogOut())
    }

    private fun session(): TdClient =
        client ?: error("A integração Telegram ainda não tem as credenciais da aplicação.")

    override suspend fun authorizedGroups(): List<TelegramGroup> {
        val td = session()
        td.awaitReady()
        val chats = td.send(TdApi.GetChats(TdApi.ChatListMain(), 100))
        return chats.chatIds.toList().mapNotNull { id ->
            val chat = runCatching { td.send(TdApi.GetChat(id)) }.getOrNull()
                ?: return@mapNotNull null
            if (chat.hasProtectedContent) return@mapNotNull null
            val type = chat.type
            val joined = when (type) {
                is TdApi.ChatTypeBasicGroup -> {
                    val group = runCatching {
                        td.send(TdApi.GetBasicGroup(type.basicGroupId))
                    }.getOrNull() ?: return@mapNotNull null
                    isMemberStatus(group.status)
                }
                is TdApi.ChatTypeSupergroup -> {
                    if (type.isChannel) return@mapNotNull null
                    val group = runCatching {
                        td.send(TdApi.GetSupergroup(type.supergroupId))
                    }.getOrNull() ?: return@mapNotNull null
                    isMemberStatus(group.status)
                }
                else -> false
            }
            if (!joined) return@mapNotNull null
            TelegramGroup(id, chat.title.take(90), isMember = true)
        }
    }

    private fun isMemberStatus(status: TdApi.ChatMemberStatus): Boolean =
        status is TdApi.ChatMemberStatusMember ||
            status is TdApi.ChatMemberStatusAdministrator ||
            status is TdApi.ChatMemberStatusCreator ||
            (status is TdApi.ChatMemberStatusRestricted && status.isMember)

    override suspend fun recentPhotoThumbnails(
        groupId: Long,
        maxCount: Int
    ): List<TelegramPhotoCandidate> {
        require(maxCount in 1..TelegramGroupVisualSearch.MAX_PHOTOS_PER_GROUP)
        val td = session()
        td.awaitReady()
        val authorized = authorizedGroups().any { it.id == groupId && it.isMember }
        check(authorized) { "O grupo não pertence à conta Telegram autorizada." }
        val results = td.send(
            TdApi.SearchChatMessages(
                groupId, null, "", null, 0L, 0,
                maxCount, TdApi.SearchMessagesFilterPhoto()
            )
        )
        return results.messages.mapNotNull { message ->
            val photo = (message.content as? TdApi.MessagePhoto)?.photo ?: return@mapNotNull null
            val smallestUsefulSize = photo.sizes
                .filter { it.width >= 128 && it.height >= 128 }
                .minByOrNull { it.width.toLong() * it.height.toLong() }
                ?: photo.sizes.minByOrNull { it.width.toLong() * it.height.toLong() }
            val sourceFile = smallestUsefulSize?.photo ?: return@mapNotNull null
            if (sourceFile.expectedSize > 4 * 1024 * 1024) return@mapNotNull null
            val fetched = runCatching {
                td.send(TdApi.DownloadFile(sourceFile.id, 1, 0, 0, true))
            }.getOrNull() ?: return@mapNotNull null
            val filename = fetched.local.path
            val gray = decodeGray9x8(filename) ?: return@mapNotNull null
            val directLink = runCatching {
                td.send(TdApi.GetMessageLink(groupId, message.id, 0, 0, "", false, false)).link
            }.getOrNull()
            TelegramPhotoCandidate(
                chatId = groupId,
                messageId = message.id,
                title = "",
                publicPostUrl = directLink,
                grayscale9x8 = gray,
                localPreviewPath = filename
            )
        }
    }

    override suspend fun searchMessagesByText(
        groupId: Long, query: String, maxCount: Int
    ): List<TelegramTextCandidate> {
        require(query.length in 4..48 && maxCount in 1..12)
        val td = session()
        td.awaitReady()
        check(authorizedGroups().any { it.id == groupId && it.isMember }) {
            "Não é permitido pesquisar neste grupo."
        }
        // Searches within this authorized chat only — not all of Telegram.
        val messages = td.send(
            TdApi.SearchChatMessages(groupId, null, query, null, 0L, 0, maxCount, null)
        )
        return messages.messages.mapNotNull { message ->
            val content = message.content
            val matrixFormat = if (content is TdApi.MessageDocument) {
                content.document.fileName.substringAfterLast('.', "")
                    .uppercase(java.util.Locale.ROOT)
                    .takeIf { it in setOf("PES", "DST", "JEF", "EXP", "VP3", "XXX") }
            } else null
            // Only texts, photos with relevant captions, or real attached design
            // documents returned by the Telegram search.
            val relevant = content is TdApi.MessageText ||
                content is TdApi.MessagePhoto ||
                matrixFormat != null
            if (!relevant) return@mapNotNull null
            val link = runCatching {
                td.send(
                    TdApi.GetMessageLink(groupId, message.id, 0, 0, "", false, false)
                ).link
            }.getOrNull()
            TelegramTextCandidate(
                chatId = groupId, messageId = message.id,
                publicPostUrl = link, matrixFileFormat = matrixFormat
            )
        }
    }

    /** Small, temporary decoding; no separate Brother image database. */
    private fun decodeGray9x8(filePath: String): IntArray? {
        if (filePath.isBlank()) return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, opts)
        if (opts.outWidth !in 1..20000 || opts.outHeight !in 1..20000) return null
        var sample = 1
        while (opts.outWidth / sample > 1024 || opts.outHeight / sample > 1024) {
            sample *= 2
        }
        val loaded = BitmapFactory.decodeFile(
            filePath, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null
        return try {
            val small = Bitmap.createScaledBitmap(loaded, 9, 8, true)
            try {
                val pixels = IntArray(72)
                small.getPixels(pixels, 0, 9, 0, 0, 9, 8)
                IntArray(72) { pos ->
                    val c = pixels[pos]
                    (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                }
            } finally {
                if (small !== loaded) small.recycle()
            }
        } finally {
            loaded.recycle()
        }
    }
}
