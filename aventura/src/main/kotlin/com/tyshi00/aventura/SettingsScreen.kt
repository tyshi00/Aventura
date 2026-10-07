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
import kotlinx.coroutines.launch

data class SettingsState(
    val invertColors: Boolean = false,
    val showStreaks: Boolean = true,
    val showTrophies: Boolean = true,
)

class SettingsViewModel(private val repo: AventuraRepository) : LightViewModel<Unit>() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = SettingsState(
                invertColors = repo.getInvertColors(),
                showStreaks = repo.getShowStreaks(),
                showTrophies = repo.getShowTrophies(),
            )
        }
    }

    fun toggleInvertColors() {
        viewModelScope.launch(Dispatchers.IO) {
            val newValue = !_state.value.invertColors
            repo.setInvertColors(newValue)
            _state.value = _state.value.copy(invertColors = newValue)
            if (newValue) LightThemeController.setLightTheme() else LightThemeController.setDarkTheme()
        }
    }

    fun toggleShowStreaks() {
        viewModelScope.launch(Dispatchers.IO) {
            val newValue = !_state.value.showStreaks
            repo.setShowStreaks(newValue)
            _state.value = _state.value.copy(showStreaks = newValue)
        }
    }

    fun toggleShowTrophies() {
        viewModelScope.launch(Dispatchers.IO) {
            val newValue = !_state.value.showTrophies
            repo.setShowTrophies(newValue)
            _state.value = _state.value.copy(showTrophies = newValue)
        }
    }

    fun resetAll() {
        viewModelScope.launch(Dispatchers.IO) {
            repo.resetAll()
        }
    }
}

class SettingsScreen(
    sealedActivity: SealedLightActivity,
    private val repo: AventuraRepository,
) : LightScreen<Unit, SettingsViewModel>(sealedActivity) {

    override val viewModelClass: Class<SettingsViewModel>
        get() = SettingsViewModel::class.java

    override fun createViewModel() = SettingsViewModel(repo)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                // Settings-style screens start their list right under the bar, with no extra gap.
                // Each row carries its own 1.3 unit padding, like LightOS's settings screens.
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack() },
                    ),
                    center = LightTopBarCenter.Text("Settings"),
                )

                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = OPTION_START_UNITS.gridUnitsAsDp()),
                ) {
                    OptionRow(
                        title = "Invert colors",
                        icon = toggleIcon(state.invertColors),
                        onClick = { viewModel.toggleInvertColors() },
                    )
                    OptionRow(
                        title = "Show streaks",
                        icon = toggleIcon(state.showStreaks),
                        onClick = { viewModel.toggleShowStreaks() },
                    )
                    OptionRow(
                        title = "Show trophies",
                        icon = toggleIcon(state.showTrophies),
                        onClick = { viewModel.toggleShowTrophies() },
                    )
                    OptionRow(
                        title = "Backup & restore",
                        onClick = { navigateTo(screenFactory = { BackupScreen(it, repo) }) },
                    )
                    OptionRow(
                        title = "Reset all data",
                        dimmed = true,
                        onClick = {
                            navigateTo(
                                screenFactory = {
                                    ConfirmResetScreen(
                                        it,
                                        "Reset all data? This will permanently clear your quest history, level, and streak. Saved backups are not deleted.",
                                    )
                                },
                                resultCallback = { confirmed ->
                                    if (confirmed) viewModel.resetAll()
                                },
                            )
                        },
                    )
                }

                LightBottomBar(items = listOf())
            }
        }
    }
}
