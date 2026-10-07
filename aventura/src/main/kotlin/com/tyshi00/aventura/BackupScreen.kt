package com.tyshi00.aventura

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val fullDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
private val shortDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy")

private fun entries(n: Int) = "$n ${if (n == 1) "entry" else "entries"}"

private fun at(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())

/** Entry count and exact save time, for the confirm screen. */
private fun describe(data: BackupData): String {
    val saved = if (data.savedAtMillis > 0L) ", saved " + fullDateFormat.format(at(data.savedAtMillis)) else ""
    return entries(data.entries.size) + saved
}

/** Date only, short enough to fit on a row's subtitle line. */
private fun shortDate(data: BackupData): String =
    if (data.savedAtMillis > 0L) shortDateFormat.format(at(data.savedAtMillis)) else "date unknown"

private fun joinWithAnd(parts: List<String>): String = when (parts.size) {
    0 -> ""
    1 -> parts[0]
    2 -> "${parts[0]} and ${parts[1]}"
    else -> parts.dropLast(1).joinToString(", ") + ", and " + parts.last()
}

private fun Throwable.brief(): String = (message ?: toString()).take(100)

data class BackupUiState(
    val saved: BackupData? = null,
    val auto: BackupData? = null,
    /** Backups found in the drop-off folder, which is where a copy goes after a reinstall. */
    val drop: DropScan = DropScan(emptyList(), 0),
    val publicCount: Int = 0,
    val publicNewest: LocalDateTime? = null,
    val status: String? = null,
)

/**
 * All backup and restore work runs here, not in the screen's composition. The confirm screen
 * discards this screen's composition while it is showing, which cancels any scope remembered
 * inside it. A ViewModel scope survives that, so a restore confirmed on the next screen still runs.
 */
class BackupViewModel(
    private val repo: AventuraRepository,
    private val store: BackupStore,
    private val folders: BackupFolders,
) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { folders.ensureDropDir() }
            val saved = store.readManual()
            val auto = store.readAuto()
            val drop = runCatching { folders.findDropBackups(Backup::decode) }.getOrDefault(DropScan(emptyList(), 0))
            val publicCopies = runCatching { folders.ownPublicCopies() }.getOrDefault(emptyList())
            val newest = runCatching { folders.newestPublicCopyTime() }.getOrNull()
            _state.update {
                it.copy(saved = saved, auto = auto, drop = drop, publicCount = publicCopies.size, publicNewest = newest)
            }
        }
    }

    fun setStatus(message: String?) {
        _state.update { it.copy(status = message) }
    }

    /** Saves to the app and to Documents/Aventura, and verifies both. */
    fun backUp() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val data = repo.exportBackup()
                val text = Backup.encode(data)
                val fileOk = store.writeManual(text, data.entries.size)
                val publicWrite = runCatching { folders.writePublicCopy(text, BackupKind.MANUAL) }
                    .getOrElse { PublicWrite(false, null, it.brief()) }

                val places = buildList {
                    if (fileOk) add("the app")
                    if (publicWrite.ok) add("Documents/Aventura")
                }
                var message = if (places.isEmpty()) {
                    "Backup failed."
                } else {
                    "Backed up ${entries(data.entries.size)} to ${joinWithAnd(places)}."
                }
                if (!fileOk) message += " Saving inside the app failed."
                if (!publicWrite.ok) message += " Could not save to Documents/Aventura: ${publicWrite.error}."

                val saved = if (fileOk) store.readManual() else _state.value.saved
                val publicCopies = runCatching { folders.ownPublicCopies() }.getOrDefault(emptyList())
                val newest = runCatching { folders.newestPublicCopyTime() }.getOrNull()
                _state.update {
                    it.copy(saved = saved, publicCount = publicCopies.size, publicNewest = newest, status = message)
                }
            } catch (e: Exception) {
                setStatus("Backup failed: ${e.brief()}")
            }
        }
    }

    fun restore(data: BackupData) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { repo.restoreBackup(data) }
            if (result.isSuccess) {
                if (data.invertColors) LightThemeController.setLightTheme() else LightThemeController.setDarkTheme()
                setStatus("Restored ${entries(data.entries.size)}.")
            } else {
                setStatus("Restore failed, your existing data was not changed. ${result.exceptionOrNull()?.brief().orEmpty()}")
            }
        }
    }
}

class BackupScreen(
    sealedActivity: SealedLightActivity,
    private val repo: AventuraRepository,
) : LightScreen<Unit, BackupViewModel>(sealedActivity) {

    override val viewModelClass: Class<BackupViewModel>
        get() = BackupViewModel::class.java

    override fun createViewModel() =
        BackupViewModel(repo, BackupStore(lightContext.fileShare), BackupFolders())

    private fun confirmAndRestore(data: BackupData, source: String) {
        viewModel.setStatus(null)
        navigateTo(
            screenFactory = {
                ConfirmResetScreen(
                    it,
                    "Restore ${describe(data)} from $source? " +
                        "This replaces your current history, level, and streak.",
                    title = "Confirm restore",
                    confirmLabel = "RESTORE",
                )
            },
            // Runs after this screen's composition is gone, so it must only touch the ViewModel.
            resultCallback = { confirmed -> if (confirmed) viewModel.restore(data) },
        )
    }

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()

        val dropSubtitle = when {
            state.drop.valid.isNotEmpty() ->
                "${state.drop.valid.size} found, newest ${shortDate(state.drop.valid.first().data)}"
            state.drop.unusable > 0 -> "files found, none readable"
            else -> "none found"
        }
        val publicLine = if (state.publicCount == 0) {
            "In Documents/Aventura: none yet."
        } else {
            "In Documents/Aventura: ${state.publicCount} ${if (state.publicCount == 1) "copy" else "copies"}" +
                (state.publicNewest?.let { ", newest ${shortDateFormat.format(it)}" } ?: "") + "."
        }

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                // Settings-style screen: the list starts right under the bar, each row carries its own padding.
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack() },
                    ),
                    center = LightTopBarCenter.Text("Backup & restore"),
                )

                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = OPTION_START_UNITS.gridUnitsAsDp()),
                ) {
                    OptionRow(
                        title = "Back up now",
                        subtitle = "in this app and in Documents",
                        onClick = { viewModel.backUp() },
                    )

                    OptionRow(
                        title = "Restore from saved backup",
                        subtitle = state.saved?.let { "saved ${shortDate(it)}" } ?: "none yet",
                        dimmed = state.saved == null,
                        onClick = {
                            val data = state.saved
                            if (data == null) {
                                viewModel.setStatus("There's no saved backup yet. Use Back up now first.")
                            } else {
                                confirmAndRestore(data, "the saved backup")
                            }
                        },
                    )

                    OptionRow(
                        title = "Restore from auto-backup",
                        subtitle = state.auto?.let { "auto-saved ${shortDate(it)}" } ?: "none yet",
                        dimmed = state.auto == null,
                        onClick = {
                            val data = state.auto
                            if (data == null) {
                                viewModel.setStatus(
                                    "There's no auto-backup yet. One is saved about once a day when you open Aventura.",
                                )
                            } else {
                                confirmAndRestore(data, "the auto-backup")
                            }
                        },
                    )

                    OptionRow(
                        title = "Restore from folder",
                        subtitle = dropSubtitle,
                        dimmed = state.drop.valid.isEmpty(),
                        onClick = {
                            val newest = state.drop.valid.firstOrNull()
                            if (newest == null) {
                                viewModel.setStatus(
                                    if (state.drop.unusable > 0) {
                                        "The files in the Aventura folder aren't valid Aventura backups."
                                    } else {
                                        "Nothing in the Aventura folder yet. Copy a backup file into " +
                                            "${BackupFolders.DROP_PATH_FOR_ADB}/ first, then try again."
                                    },
                                )
                            } else {
                                confirmAndRestore(newest.data, "the Aventura folder")
                            }
                        },
                    )

                    state.status?.let {
                        LightText(
                            text = it,
                            variant = LightTextVariant.Detail,
                            modifier = Modifier.padding(top = 1f.gridUnitsAsDp(), end = 1f.gridUnitsAsDp()),
                        )
                    }

                    LightText(
                        text = publicLine,
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp(), end = 1f.gridUnitsAsDp()),
                    )

                    LightText(
                        text = "Back up saves inside the app and as a dated file in Documents/Aventura. " +
                            "It also auto-saves about once a day when you open the tool. Use the file " +
                            "saved in Documents, since it survives uninstalls. Restoring it has to be " +
                            "done manually via ADB:",
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp(), end = 1f.gridUnitsAsDp()),
                    )

                    LightText(
                        text = "Keep a copy on your PC:\n${BackupFolders.ADB_PULL_COMMAND}",
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp(), end = 1f.gridUnitsAsDp()),
                    )

                    LightText(
                        text = "Restore (use the actual file name):\n${BackupFolders.ADB_PUSH_COMMAND}\n" +
                            "Then tap \"Restore from folder\".",
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp(), end = 1f.gridUnitsAsDp()),
                    )
                }

                LightBottomBar(items = listOf())
            }
        }
    }
}
