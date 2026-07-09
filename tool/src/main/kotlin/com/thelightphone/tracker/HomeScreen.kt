package com.thelightphone.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.buildDatabase
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
import kotlinx.coroutines.launch

data class HomeState(
    val waterDisplay: String = "0",
    val waterUnit: String = "ml",
    val stepsDisplay: String = "0",
    val sleepDisplay: String = "0h 0m",
    val moodEnabled: Boolean = false,
    val moodDisplay: String = "",
    val weightEnabled: Boolean = false,
    val weightDisplay: String = "",
    val cycleEnabled: Boolean = false,
    val cycleNextDisplay: String = "",
)

class HomeViewModel(private val repo: TrackerRepository) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        reload()
    }

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            val unit = repo.getWaterUnit()
            val invertColors = repo.getInvertColors()
            if (invertColors) LightThemeController.setLightTheme()
            else LightThemeController.setDarkTheme()

            val waterMl = repo.getTodayWaterMl()
            val steps = repo.getTodaySteps()
            val sleepMin = repo.getMostRecentSleepDurationMinutes() ?: 0

            val moodEnabled = repo.getMoodFeatureEnabled()
            val moodDisplay = if (moodEnabled) {
                repo.getMostRecentMoodEntry()?.let { entry ->
                    decodeMoods(entry.moods).joinToString(", ") { it.label }
                } ?: "How are you today?"
            } else {
                ""
            }

            val weightEnabled = repo.getWeightFeatureEnabled()
            val weightDisplay = if (weightEnabled) {
                val weightUnit = repo.getWeightUnit()
                repo.getMostRecentWeightEntry()?.let {
                    "${WeightConversion.format(it.weightKg, weightUnit)} ${weightUnit.label}"
                } ?: "Not set"
            } else {
                ""
            }

            val cycleEnabled = repo.getCycleFeatureEnabled()
            val cycleNextDisplay = if (cycleEnabled) {
                repo.predictNextCycleStart()?.let { dateLabel(it) } ?: "Not enough data yet"
            } else {
                ""
            }

            _state.value = HomeState(
                waterDisplay = WaterConversion.format(waterMl, unit),
                waterUnit = unit.label,
                stepsDisplay = "%,d".format(steps),
                sleepDisplay = formatSleep(sleepMin),
                moodEnabled = moodEnabled,
                moodDisplay = moodDisplay,
                weightEnabled = weightEnabled,
                weightDisplay = weightDisplay,
                cycleEnabled = cycleEnabled,
                cycleNextDisplay = cycleNextDisplay,
            )
        }
    }
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, HomeViewModel>(sealedActivity) {

    private val repo by lazy {
        TrackerRepository.getInstance {
            lightContext.buildDatabase(TrackerDatabase::class.java, "tracker.db")
        }
    }

    override val viewModelClass: Class<HomeViewModel>
        get() = HomeViewModel::class.java

    override fun createViewModel() = HomeViewModel(repo)

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
                // LightTopBar renders its center text at LightTextVariant.Fine (25sp) internally
                LightTopBar(
                    center = LightTopBarCenter.Text("Tracker"),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    TrackerHomeItem(
                        label = "Water",
                        value = state.waterDisplay,
                        unit = state.waterUnit,
                        onClick = { navigateTo(screenFactory = { WaterScreen(it, repo) }) },
                    )

                    Spacer(modifier = Modifier.height(2f.gridUnitsAsDp()))

                    TrackerHomeItem(
                        label = "Steps",
                        value = state.stepsDisplay,
                        onClick = { navigateTo(screenFactory = { StepsScreen(it, repo) }) },
                    )

                    Spacer(modifier = Modifier.height(2f.gridUnitsAsDp()))

                    TrackerHomeItem(
                        label = "Sleep",
                        value = state.sleepDisplay,
                        onClick = { navigateTo(screenFactory = { SleepScreen(it, repo) }) },
                    )

                    if (state.moodEnabled) {
                        Spacer(modifier = Modifier.height(2f.gridUnitsAsDp()))

                        TrackerHomeItem(
                            label = "Mood",
                            value = state.moodDisplay,
                            onClick = { navigateTo(screenFactory = { MoodScreen(it, repo) }) },
                        )
                    }

                    if (state.weightEnabled) {
                        Spacer(modifier = Modifier.height(2f.gridUnitsAsDp()))

                        TrackerHomeItem(
                            label = "Weight",
                            value = state.weightDisplay,
                            onClick = { navigateTo(screenFactory = { WeightScreen(it, repo) }) },
                        )
                    }

                    if (state.cycleEnabled) {
                        Spacer(modifier = Modifier.height(2f.gridUnitsAsDp()))

                        TrackerHomeItem(
                            label = "Cycle",
                            value = state.cycleNextDisplay,
                            onClick = { navigateTo(screenFactory = { CycleScreen(it, repo) }) },
                        )
                    }
                }

                LightBottomBar(
                    items = listOf(
                        null,
                        null,
                        null,
                        null,
                        LightBarButton.LightIcon(
                            icon = LightIcons.SETTINGS,
                            onClick = { navigateTo(screenFactory = { SettingsScreen(it, repo) }) },
                        ),
                    ),
                )
            }
        }
    }
}

@Composable
private fun TrackerHomeItem(
    label: String,
    value: String,
    unit: String? = null,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 0.5f.gridUnitsAsDp()),
    ) {
        // SDK Detail variant (20sp) — matches ic icon label usage in UiDemoIconsScreen
        LightText(
            text = label.uppercase(),
            variant = LightTextVariant.Detail,
            lighten = true,
        )
        // SDK Heading variant (38sp) — primary display value, matches WeatherHomeScreen usage
        LightText(
            text = value,
            variant = LightTextVariant.Heading,
        )
        if (unit != null) {
            // SDK Fine variant (25sp) — secondary label, matches AuthenticatorAccountScreen
            LightText(
                text = unit,
                variant = LightTextVariant.Fine,
                lighten = true,
            )
        }
    }
}
