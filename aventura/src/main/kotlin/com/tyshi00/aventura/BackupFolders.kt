package com.tyshi00.aventura

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class BackupKind(val label: String, val keep: Int) {
    MANUAL("manual", keep = 5),
    AUTO("auto", keep = 7),
}

data class PublicWrite(val ok: Boolean, val fileName: String?, val error: String?)

/** A backup found in the drop-off folder, already checked to be a real Aventura backup. */
data class DropBackup(val fileName: String, val data: BackupData)

data class DropScan(val valid: List<DropBackup>, val unusable: Int)

/**
 * The two shared-storage folders that make a backup survive an uninstall. Both were verified on a
 * real Light Phone III with the storage test:
 *
 *  - [publicDir] (Documents/Aventura): files written here stay after an uninstall and can be
 *    copied to a computer. A reinstalled app is not allowed to open them, and Android hides them
 *    from its folder listings, but it can still add new files next to them.
 *  - [dropDir] (Android/media/<app id>): the app's own folder. Android wipes it on an uninstall,
 *    but the app can read any file placed there, including ones copied with adb.
 *
 * After a reinstall the person copies a backup from the first folder into the second, and the app
 * restores from it. Only plain java.io calls, so this runs on a laptop for testing.
 */
class BackupFolders(root: String = DEFAULT_ROOT) {

    companion object {
        const val DEFAULT_ROOT = "/storage/emulated/0"

        /** Must match `id` in aventura/lighttool.toml. */
        const val APP_ID = "com.tyshi00.aventura"

        /** The same two folders as adb sees them, for showing to people. */
        const val PUBLIC_PATH_FOR_ADB = "/sdcard/Documents/Aventura"
        const val DROP_PATH_FOR_ADB = "/sdcard/Android/media/$APP_ID"

        /** A real-looking backup name, shown in the instructions. Must match how writePublicCopy names files. */
        const val EXAMPLE_BACKUP_NAME = "aventura-auto-2026-10-06-182601.json"

        /** The two commands shown on the Backup screen. */
        const val ADB_PULL_COMMAND = "adb pull $PUBLIC_PATH_FOR_ADB"
        const val ADB_PUSH_COMMAND = "adb push $EXAMPLE_BACKUP_NAME $DROP_PATH_FOR_ADB/"

        private val NAME = Regex("""^aventura-(manual|auto)-(\d{4}-\d{2}-\d{2}-\d{6})(-\d+)?\.json$""")
        private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
        private const val MAX_DROP_FILES = 5
    }

    val publicDir = File("$root/Documents/Aventura")
    val dropDir = File("$root/Android/media/$APP_ID")

    private fun why(t: Throwable?): String = (t?.message ?: t?.toString() ?: "unknown").take(80)

    private fun readable(file: File): Boolean =
        runCatching { file.inputStream().use { it.read() }; true }.getOrDefault(false)

    private fun stampOf(name: String): String = NAME.matchEntire(name)?.groupValues?.get(2).orEmpty()

    /** Makes the drop-off folder so there is always somewhere to copy a backup to. */
    fun ensureDropDir(): Boolean =
        runCatching { dropDir.isDirectory || dropDir.mkdirs() || dropDir.isDirectory }.getOrDefault(false)

    /**
     * Saves a dated copy in Documents/Aventura and checks it by reading it back. The name carries the
     * time, so it never collides with a file left behind by an earlier install. Older copies this
     * install made are removed, keeping the newest few of each kind.
     */
    fun writePublicCopy(text: String, kind: BackupKind, now: LocalDateTime = LocalDateTime.now()): PublicWrite {
        val folder = runCatching { publicDir.isDirectory || publicDir.mkdirs() || publicDir.isDirectory }
        if (folder.isFailure || folder.getOrNull() != true) {
            return PublicWrite(false, null, "could not create Documents/Aventura")
        }
        val base = "aventura-${kind.label}-${now.format(STAMP)}"
        var name = "$base.json"
        var counter = 2
        while (runCatching { File(publicDir, name).exists() }.getOrDefault(false) && counter < 50) {
            name = "$base-$counter.json"
            counter++
        }
        val file = File(publicDir, name)
        val verified = runCatching {
            file.writeText(text)
            file.readText() == text
        }
        if (verified.isFailure || verified.getOrNull() != true) {
            runCatching { file.delete() }
            return PublicWrite(false, null, if (verified.isFailure) why(verified.exceptionOrNull()) else "the saved file did not match")
        }
        ownPublicCopies(kind).drop(kind.keep).forEach { runCatching { it.delete() } }
        return PublicWrite(true, name, null)
    }

    /**
     * Copies in Documents/Aventura that this install can read, newest first. Files from an earlier
     * install are hidden or locked, so they never appear here.
     */
    fun ownPublicCopies(kind: BackupKind? = null): List<File> {
        val files = runCatching { publicDir.listFiles() }.getOrNull() ?: return emptyList()
        return files
            .filter { f ->
                !f.isDirectory && NAME.matches(f.name) &&
                    (kind == null || f.name.startsWith("aventura-${kind.label}-")) && readable(f)
            }
            .sortedWith(compareByDescending<File> { stampOf(it.name) }.thenByDescending { it.name })
    }

    fun newestPublicCopyTime(): LocalDateTime? =
        ownPublicCopies().firstOrNull()?.let { runCatching { LocalDateTime.parse(stampOf(it.name), STAMP) }.getOrNull() }

    /**
     * True when Documents/Aventura exists but this install has no readable backup in it. That is
     * what a reinstall looks like: the folder is left over from the earlier install, whose files
     * this one cannot open. A brand new install has no such folder yet.
     */
    fun looksLikeReinstall(): Boolean =
        runCatching { publicDir.isDirectory }.getOrDefault(false) && ownPublicCopies().isEmpty()

    /** Backups placed in the drop-off folder, checked and sorted newest first. */
    fun findDropBackups(decode: (String) -> BackupData?): DropScan {
        val files = runCatching { dropDir.listFiles() }.getOrNull().orEmpty()
            .filter { !it.isDirectory && it.name.endsWith(".json", ignoreCase = true) }
            .sortedByDescending { runCatching { it.lastModified() }.getOrDefault(0L) }
            .take(MAX_DROP_FILES)

        val valid = mutableListOf<DropBackup>()
        var unusable = 0
        files.forEach { file ->
            val tooBig = runCatching { file.length() > BackupValidation.MAX_FILE_CHARS }.getOrDefault(true)
            val data = if (tooBig) null else runCatching { file.readText() }.getOrNull()?.let(decode)
            if (data != null) valid += DropBackup(file.name, data) else unusable++
        }
        return DropScan(valid.sortedByDescending { it.data.savedAtMillis }, unusable)
    }
}
