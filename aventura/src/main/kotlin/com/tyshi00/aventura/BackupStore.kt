package com.tyshi00.aventura

import com.thelightphone.sdk.LightFileShare

/** Reads and writes the two backup files kept inside the app: the manual one and the daily automatic one. */
class BackupStore(private val files: LightFileShare) {

    fun readManual(): BackupData? = read(Backup.FILE_NAME)

    fun readAuto(): BackupData? = read(Backup.AUTO_FILE_NAME)

    /** Returns true only if the file was written and read back with the expected number of entries. */
    fun writeManual(text: String, entryCount: Int): Boolean = write(Backup.FILE_NAME, text, entryCount)

    fun writeAuto(text: String, entryCount: Int): Boolean = write(Backup.AUTO_FILE_NAME, text, entryCount)

    private fun read(name: String): BackupData? =
        runCatching { files.read(name) { it.readText() } }
            .getOrNull()
            ?.let { Backup.decode(it) }

    private fun write(name: String, text: String, entryCount: Int): Boolean =
        runCatching {
            files.write(name) { it.write(text) }
            read(name)?.entries?.size == entryCount
        }.getOrDefault(false)
}
