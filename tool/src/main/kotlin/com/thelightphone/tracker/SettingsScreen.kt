package com.thelightphone.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcon
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
import kotlinx.coroutines.launch

data class SettingsState(
    val invertColors: Boolean = false,
    val defaultWaterUnit: WaterUnit = WaterUnit.ML,
    val cycleTrackingEnabled: Boolean = false,
    val weightTrackingEnabled: Boolean = false,
    val moodTrackingEnabled: Boolean = false,
    val timeFormat: TimeFormat = TimeFormat.AM_PM,
)

class SettingsViewModel(private val repo: TrackerRepository) : LightViewModel<Unit>() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = SettingsState(
                invertColors = repo.getInvertColors(),
                defaultWaterUnit = repo.getWaterUnit(),
                cycleTrackingEnabled = repo.getCycleFeatureEnabled(),
                weightTrackingEnabled = repo.getWeightFeatureEnabled(),
                moodTrackingEnabled = repo.getMoodFeatureEnabled(),
                timeFormat = repo.getTimeFormat(),
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

    fun toggleCycleTracking() {
        viewModelScope.launch(Dispatchers.IO) {
            val newValue = !_state.value.cycleTrackingEnabled
            repo.setCycleFeatureEnabled(newValue)
            _state.value = _state.value.copy(cycleTrackingEnabled = newValue)
        }
    }

    fun toggleWeightTracking() {
        viewModelScope.launch(Dispatchers.IO) {
            val newValue = !_state.value.weightTrackingEnabled
            repo.setWeightFeatureEnabled(newValue)
            _state.value = _state.value.copy(weightTrackingEnabled = newValue)
        }
    }

    fun toggleMoodTracking() {
        viewModelScope.launch(Dispatchers.IO) {
            val newValue = !_state.value.moodTrackingEnabled
            repo.setMoodFeatureEnabled(newValue)
            _state.value = _state.value.copy(moodTrackingEnabled = newValue)
        }
    }

    fun setTimeFormat(format: TimeFormat) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.setTimeFormat(format)
            _state.value = _state.value.copy(timeFormat = format)
        }
    }

    fun setWaterUnit(unit: WaterUnit) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.setWaterUnit(unit)
            _state.value = _state.value.copy(defaultWaterUnit = unit)
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
    private val repo: TrackerRepository,
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
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack() },
                    ),
                    center = LightTopBarCenter.Text("Settings"),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    // Invert colors
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleInvertColors() }
                            .padding(vertical = 0.75f.gridUnitsAsDp()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LightText(
                            text = "Invert colors",
                            variant = LightTextVariant.Copy,
                            modifier = Modifier.weight(1f),
                        )
                        // TOGGLE_ON's artwork has its knob on the left; TOGGLE_OFF's
                        // knob is on the right. The screen is black (dark theme) when
                        // invertColors is false, so the knob should sit on the left
                        // in that state — i.e. show TOGGLE_ON when NOT inverted.
                        LightIcon(
                            icon = if (state.invertColors) LightIcons.TOGGLE_OFF else LightIcons.TOGGLE_ON,
                        )
                    }

                    // Track mood — opt-in, hidden by default. Controls whether
                    // the Mood tile shows up on the Home screen.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleMoodTracking() }
                            .padding(vertical = 0.75f.gridUnitsAsDp()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LightText(
                            text = "Track mood",
                            variant = LightTextVariant.Copy,
                            modifier = Modifier.weight(1f),
                        )
                        LightIcon(
                            icon = if (state.moodTrackingEnabled) LightIcons.TOGGLE_OFF else LightIcons.TOGGLE_ON,
                        )
                    }

                    // Track weight — opt-in, hidden by default, same reasoning
                    // as Cycle. Controls whether the Weight tile shows up on
                    // the Home screen.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleWeightTracking() }
                            .padding(vertical = 0.75f.gridUnitsAsDp()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LightText(
                            text = "Track weight",
                            variant = LightTextVariant.Copy,
                            modifier = Modifier.weight(1f),
                        )
                        LightIcon(
                            icon = if (state.weightTrackingEnabled) LightIcons.TOGGLE_OFF else LightIcons.TOGGLE_ON,
                        )
                    }

                    // Track menstrual cycle — opt-in, hidden by default since not
                    // everyone tracks a cycle. Controls whether the Cycle tile
                    // shows up on the Home screen.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleCycleTracking() }
                            .padding(vertical = 0.75f.gridUnitsAsDp()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LightText(
                            text = "Track menstrual cycle",
                            variant = LightTextVariant.Copy,
                            modifier = Modifier.weight(1f),
                        )
                        LightIcon(
                            icon = if (state.cycleTrackingEnabled) LightIcons.TOGGLE_OFF else LightIcons.TOGGLE_ON,
                        )
                    }

                    // Default water unit
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navigateTo(
                                    screenFactory = {
                                        UnitPickerScreen(it, state.defaultWaterUnit)
                                    },
                                    resultCallback = { result ->
                                        if (result != null) viewModel.setWaterUnit(result)
                                    },
                                )
                            }
                            .padding(vertical = 0.75f.gridUnitsAsDp()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            LightText(
                                text = "Default water unit",
                                variant = LightTextVariant.Copy,
                            )
                            LightText(
                                text = state.defaultWaterUnit.label,
                                variant = LightTextVariant.Fine,
                                lighten = true,
                            )
                        }
                        LightIcon(icon = LightIcons.ARROW_RIGHT)
                    }

                    // Time format — affects how Sleep's time fields are entered
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navigateTo(
                                    screenFactory = {
                                        TimeFormatPickerScreen(it, state.timeFormat)
                                    },
                                    resultCallback = { result ->
                                        if (result != null) viewModel.setTimeFormat(result)
                                    },
                                )
                            }
                            .padding(vertical = 0.75f.gridUnitsAsDp()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            LightText(
                                text = "Time format",
                                variant = LightTextVariant.Copy,
                            )
                            LightText(
                                text = state.timeFormat.label,
                                variant = LightTextVariant.Fine,
                                lighten = true,
                            )
                        }
                        LightIcon(icon = LightIcons.ARROW_RIGHT)
                    }

                    // Reset all data
                    LightText(
                        text = "Reset all data",
                        variant = LightTextVariant.Copy,
                        lighten = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navigateTo(
                                    screenFactory = {
                                        ConfirmResetScreen(
                                            it,
                                            "Reset all data? This will permanently clear all water, step, and sleep data.",
                                        )
                                    },
                                    resultCallback = { confirmed ->
                                        if (confirmed == true) viewModel.resetAll()
                                    },
                                )
                            }
                            .padding(vertical = 0.75f.gridUnitsAsDp()),
                    )
                }

                LightBottomBar(items = listOf())
            }
        }
    }
}
