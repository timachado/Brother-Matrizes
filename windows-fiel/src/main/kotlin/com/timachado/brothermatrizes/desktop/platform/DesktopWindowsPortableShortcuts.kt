package com.timachado.brothermatrizes.desktop.platform

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Explicit click only. Never executes at startup or installs an MSI. */
internal object DesktopWindowsPortableShortcuts {
    const val SCRIPT = "Criar-Atalhos-Brother-Matrizes.cmd"

    fun scriptForRoot(portableRoot: Path): Path {
        val root = portableRoot.toAbsolutePath().normalize()
        require(!Files.isSymbolicLink(root) &&
            Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            "Pasta do pacote portátil indisponível."
        }
        val files = listOf("BrotherMatrizes.exe", SCRIPT, "LEIA-ME-QA.txt")
        require(files.all { name ->
            val file = root.resolve(name)
            Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) &&
                !Files.isSymbolicLink(file)
        } && listOf("app", "runtime").all { name ->
            val folder = root.resolve(name)
            Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS) &&
                !Files.isSymbolicLink(folder)
        }) {
            "Extraia toda a pasta do ZIP antes de criar os atalhos."
        }
        return root.resolve(SCRIPT)
    }

    fun createForCurrentUser() {
        check(System.getProperty("os.name").startsWith("Windows", true)) {
            "Atalhos disponíveis apenas no Windows."
        }
        // jpackage stores the entry JAR under <portableRoot>/app/.
        val code = Path.of(
            DesktopWindowsPortableShortcuts::class.java
                .protectionDomain.codeSource.location.toURI()
        ).toAbsolutePath().normalize()
        val root = checkNotNull(code.parent?.parent) {
            "Não foi possível localizar o aplicativo extraído."
        }
        val script = scriptForRoot(root)
        // Environment variable avoids interpolating an untrusted path into
        // PowerShell code. No ExecutionPolicy bypass, admin, Defender edits.
        val processBuilder = ProcessBuilder(
            "powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
            "-Command", "& \$env:BM_PORTABLE_SCRIPT; exit \$LASTEXITCODE"
        ).directory(root.toFile()).redirectErrorStream(true)
        processBuilder.environment()["BM_PORTABLE_SCRIPT"] = script.toString()
        val process = processBuilder.start()
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("A criação de atalhos excedeu o tempo permitido.")
        }
        check(process.exitValue() == 0) {
            "Atalhos não criados. Execute o arquivo $SCRIPT na pasta portátil."
        }
    }
}
