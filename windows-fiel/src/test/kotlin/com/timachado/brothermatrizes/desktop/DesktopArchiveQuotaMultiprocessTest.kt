package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveQuotaJournal
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Real, independently launched Java 17 JVM processes on a Windows runner.
 * Unlike two instances in one JVM, this verifies the OS FileLock and marker
 * survival after destroyForcibly / Runtime.halt, with no quota server.
 */
class DesktopArchiveQuotaMultiprocessTest {
    private val javaLauncher = Path.of(
        System.getProperty("java.home"), "bin",
        if (System.getProperty("os.name").contains("Windows", true))
            "java.exe" else "java"
    )

    private val testClasspath: String =
        System.getProperty("brother.qa.childClasspath").orEmpty()

    private fun launch(
        root: Path,
        action: String,
        output: Path
    ): Process {
        require(testClasspath.isNotBlank()) {
            "Gradle test runtime classpath missing. Never skip multiprocess QA."
        }
        return ProcessBuilder(
            javaLauncher.toString(), "-cp", testClasspath,
            DesktopArchiveQuotaProcessProbe::class.java.name,
            action, root.toString()
        ).redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start()
    }

    private fun completed(
        root: Path,
        action: String,
        expectCode: Int = 0
    ) {
        val log = root.resolve("child-$action.log")
        val process = launch(root, action, log)
        try {
            check(process.waitFor(20, TimeUnit.SECONDS)) {
                "Child JVM timed out in $action."
            }
            val result = process.exitValue()
            assertEquals(
                expectCode, result,
                "Child JVM failed in $action. Log: " +
                    Files.readString(log).takeLast(4000)
            )
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
                process.waitFor(10, TimeUnit.SECONDS)
            }
        }
    }

    private fun untilReady(process: Process, signal: Path, output: Path) {
        // No sleeps in production: bounded polling only in the QA process.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!Files.exists(signal) && process.isAlive &&
            System.nanoTime() < deadline) {
            Thread.sleep(40)
        }
        check(Files.readString(signal) == "READY") {
            "Reservation holder never acquired the OS lock. Log: " +
                Files.readString(output).takeLast(4000)
        }
    }

    /**
     * After destroyForcibly(), Windows may hold the redirected holder.log
     * briefly even though the child JVM is gone. Retry ONLY cleanup of
     * ephemeral QA files; never retry quota reservation or weaken assertions.
     */
    private fun deleteQaFile(path: Path) {
        repeat(40) { attempt ->
            try {
                Files.deleteIfExists(path)
                return
            } catch (busy: java.nio.file.FileSystemException) {
                if (attempt == 39) throw busy
                Thread.sleep(100)
            }
        }
    }

    private inline fun sandbox(action: (Path) -> Unit) {
        val root = Files.createTempDirectory("brother-quota-multiprocess-")
        try {
            action(root)
        } finally {
            Files.list(root).use { stream ->
                stream.forEach { deleteQaFile(it) }
            }
            deleteQaFile(root)
        }
    }

    @Test
    fun secondJvmCannotReserveWhileFirstHoldsOsLockAndCrashLeavesMarker() {
        sandbox { root ->
            val output = root.resolve("holder.log")
            val holder = launch(root, "hold-reservation", output)
            try {
                untilReady(holder, root.resolve(".child-ready"), output)
                assertTrue(holder.isAlive)
                completed(root, "probe-lock-held")
                // Hard termination: finally/AutoCloseable is NOT run in child.
                holder.destroyForcibly()
                assertTrue(holder.waitFor(15, TimeUnit.SECONDS))
                assertFalse(holder.isAlive)
                completed(root, "probe-surviving-reserve")
                val stillPending = DesktopArchiveQuotaJournal(root)
                    .listPending().single()
                assertEquals(
                    DesktopArchiveQuotaJournal.Stage.RESERVE_UNKNOWN,
                    stillPending.stage
                )
                assertEquals(null, stillPending.reservationKey)
            } finally {
                if (holder.isAlive) {
                    holder.destroyForcibly()
                    holder.waitFor(10, TimeUnit.SECONDS)
                }
            }
        }
    }

    @Test
    fun hardJvmHaltAfterFinalizeIntentCannotSilentlyClearAmbiguousQuota() {
        sandbox { root ->
            completed(root, "force-exit-after-finalize-intent", expectCode = 61)
            completed(root, "probe-surviving-finalize")
            val pending = DesktopArchiveQuotaJournal(root)
                .listPending().single()
            assertEquals(
                DesktopArchiveQuotaJournal.Stage.FINALIZE_UNKNOWN,
                pending.stage
            )
            assertEquals(
                "usage_12345678-1234-1234-1234-123456789abc",
                pending.reservationKey
            )
        }
    }
}
