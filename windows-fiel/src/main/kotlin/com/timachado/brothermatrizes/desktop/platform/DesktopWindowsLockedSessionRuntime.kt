package com.timachado.brothermatrizes.desktop.platform

import java.io.Closeable
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Clock

/**
 * Reserves ONE authenticated session owner across Windows processes.
 *
 * The OS FileLock, not a mutable process flag, owns the lease. Do not remove
 * the persistent lock file when closing: deleting/recreating a lock inode
 * could allow a second instance to bypass an existing lease.
 *
 * No passwords, tokens or private information are written to the lock file.
 * Only the associated DPAPI vault may hold the protected session.
 */
internal class DesktopWindowsLockedSessionRuntime private constructor(
    private val channel: FileChannel,
    private val lease: FileLock,
    private val storage: DesktopWindowsDpapiSessionStore
) : Closeable {

    @Volatile
    private var closed = false

    private fun assertActive() {
        check(!closed && lease.isValid && channel.isOpen) {
            "A sessão Windows não pertence mais a esta instância."
        }
    }

    private val guardedVault: DesktopProtectedSessionVault =
        object : DesktopProtectedSessionVault {
            override fun save(session: DesktopTransientAuthSession) {
                assertActive()
                storage.save(session)
            }

            override fun load(): DesktopProtectedSessionSnapshot? {
                assertActive()
                return storage.load()
            }

            override fun clear() {
                assertActive()
                storage.clear()
            }
        }

    /**
     * Exposes a keeper that checks the lease even when an access token is
     * cached and no DPAPI operation would otherwise be necessary.
     */
    fun createKeeper(
        refresher: DesktopSessionRefresher,
        clock: Clock = Clock.systemUTC()
    ): DesktopTransientSessionKeeper {
        assertActive()
        return DesktopTransientSessionKeeper(
            refresh = refresher,
            clock = clock,
            vault = guardedVault,
            sessionGuard = ::assertActive
        )
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        try {
            lease.release()
        } finally {
            channel.close()
        }
    }

    override fun toString(): String = "DesktopWindowsLockedSessionRuntime(redacted)"

    companion object {
        fun open(
            folder: Path = DesktopWindowsDpapiSessionStore.defaultUserDataFolder(),
            clock: Clock = Clock.systemUTC()
        ): DesktopWindowsLockedSessionRuntime {
            check(System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
                "Sessão Windows indisponível neste sistema."
            }
            require(folder.isAbsolute && folder.normalize() == folder) {
                "Diretório de credenciais inválido."
            }
            Files.createDirectories(folder)
            check(Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
                "Diretório de credenciais inesperado."
            }
            val lockFile = folder.resolve("session.v1.dpapi.lock")
            check(!Files.exists(lockFile, LinkOption.NOFOLLOW_LINKS) ||
                Files.isRegularFile(lockFile, LinkOption.NOFOLLOW_LINKS)) {
                "Arquivo de exclusão da sessão inválido."
            }
            val channel = FileChannel.open(
                lockFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS
            )
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            } catch (failure: Exception) {
                channel.close()
                throw failure
            }
            if (lock == null) {
                channel.close()
                error("Outra instância do Brother Matrizes já utiliza a sessão Windows.")
            }
            try {
                return DesktopWindowsLockedSessionRuntime(
                    channel, lock, DesktopWindowsDpapiSessionStore(folder, clock)
                )
            } catch (failure: Exception) {
                lock.release()
                channel.close()
                throw failure
            }
        }
    }
}
