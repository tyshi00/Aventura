package com.thelightphone.tracker

import androidx.compose.foundation.background
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
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class WeightState(
    // Starting weight is a one-time reference point — persists and displays
    // (unlike Current weight below, it never clears back to blank on save).
    val startingWeightValue: String = "",
    val startingDate: String = todayStr(),
    // Current weight is a repeated, backdateable daily log, like Water/Steps/
    // Sleep's amount fields — clears back to blank after each save.
    val currentWeightValue: String = "",
    val currentDate: String = todayStr(),
    val selectedUnit: WeightUnit = WeightUnit.LBS,
    val averageChangeDisplay: String = "Not enough data yet",
    val showSaved: Boolean = false,
)

class WeightViewModel(private val repo: TrackerRepository) : LightViewModel<Unit>() {
    private val _state = MutableStateFlow(WeightState())
    val state: StateFlow<WeightState> = _state.asStateFlow()

    private val dbMutex = Mutex()

    // Only load the starting-weight fields from the database the FIRST time
    // this screen is shown. On every later onScreenShow (e.g. returning from
    // the date/number/unit pickers), we only refresh the read-only stat —
    // otherwise a stale DB read would stomp over a selection made before
    // SAVE was tapped (the same bug we fixed on the Cycle screen).
    private var loadedInitialState = false

    // Guards against a slower, earlier save's delayed "hide" wiping out a
    // more recent save's still-visible confirmation.
    private var savedToken = 0L

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        viewModelScope.launch(Dispatchers.IO) {
            dbMutex.withLock {
                if (!loadedInitialState) {
                    loadEditableFieldsFromDb()
                    loadedInitialState = true
                }
                refreshStatsOnly()
            }
        }
    }

    private suspend fun loadEditableFieldsFromDb() {
        val unit = repo.getWeightUnit()
        val starting = repo.getStartingWeight()
        _state.value = _state.value.copy(
            selectedUnit = unit,
            startingWeightValue = starting?.let { WeightConversion.format(it.weightKg, unit) } ?: "",
            startingDate = starting?.date ?: todayStr(),
        )
    }

    private suspend fun refreshStatsOnly() {
        val avgChangeKg = repo.getAverageWeeklyWeightChangeKg()
        val unit = _state.value.selectedUnit
        _state.value = _state.value.copy(
            averageChangeDisplay = avgChangeKg?.let { WeightConversion.formatWeeklyChange(it, unit) }
                ?: "Not enough data yet",
        )
    }

    fun setStartingWeightValue(value: String) {
        _state.value = _state.value.copy(startingWeightValue = value)
    }

    fun setStartingDate(date: String) {
        _state.value = _state.value.copy(startingDate = date)
    }

    fun setCurrentWeightValue(value: String) {
        _state.value = _state.value.copy(currentWeightValue = value)
    }

    fun setCurrentDate(date: String) {
        _state.value = _state.value.copy(currentDate = date)
    }

    fun setUnit(unit: WeightUnit) {
        viewModelScope.launch(Dispatchers.IO) {
            dbMutex.withLock {
                repo.setWeightUnit(unit)
                val starting = repo.getStartingWeight()
                _state.value = _state.value.copy(
                    selectedUnit = unit,
                    startingWeightValue = starting?.let { WeightConversion.format(it.weightKg, unit) }
                        ?: _state.value.startingWeightValue,
                )
                refreshStatsOnly()
            }
        }
    }

    fun save() {
        viewModelScope.launch(Dispatchers.IO) {
            dbMutex.withLock {
                val s = _state.value

                val startingValue = s.startingWeightValue.toDoubleOrNull()
                if (startingValue != null && startingValue > 0) {
                    repo.setStartingWeight(WeightConversion.toKg(startingValue, s.selectedUnit), s.startingDate)
                }

                val currentValue = s.currentWeightValue.toDoubleOrNull()
                if (currentValue != null && currentValue > 0) {
                    repo.addWeightEntry(WeightConversion.toKg(currentValue, s.selectedUnit), s.currentDate)
                }

                val refreshedStarting = repo.getStartingWeight()
                _state.value = _state.value.copy(
                    startingWeightValue = refreshedStarting?.let {
                        WeightConversion.format(it.weightKg, s.selectedUnit)
                    } ?: s.startingWeightValue,
                    startingDate = refreshedStarting?.date ?: s.startingDate,
                    currentWeightValue = "",
                    currentDate = todayStr(),
                )
                refreshStatsOnly()

                val myToken = ++savedToken
                _state.value = _state.value.copy(showSaved = true)
                viewModelScope.launch {
                    delay(1500)
                    if (savedToken == myToken) {
                        _state.value = _state.value.copy(showSaved = false)
                    }
                }
            }
        }
    }

    fun reset() {
        viewModelScope.launch(Dispatchers.IO) {
            dbMutex.withLock {
                repo.resetWeight()
                _state.value = WeightState(selectedUnit = _state.value.selectedUnit)
                loadedInitialState = false
            }
        }
    }
}

class WeightScreen(
    sealedActivity: SealedLightActivity,
    private val repo: TrackerRepository,
) : LightScreen<Unit, WeightViewModel>(sealedActivity) {

    override val viewModelClass: Class<WeightViewModel>
        get() = WeightViewModel::class.java

    override fun createViewModel() = WeightViewModel(repo)

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
                    center = LightTopBarCenter.Text("Weight"),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    LightTextField(
                        label = "Starting weight",
                        value = if (state.startingWeightValue.isBlank()) {
                            "Not set"
                        } else {
                            "${state.startingWeightValue} ${state.selectedUnit.label}"
                        },
                        placeholder = "0",
                        onClick = {
                            navigateTo(
                                screenFactory = {
                                    NumberEditorScreen(
                                        it,
                                        title = "Starting weight",
                                        initialValue = state.startingWeightValue,
                                        isDecimal = true,
                                    )
                                },
                                resultCallback = { result ->
                                    if (result != null) viewModel.setStartingWeightValue(result)
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(1.5f.gridUnitsAsDp()))

                    LightTextField(
                        label = "Starting date",
                        value = dateLabel(state.startingDate),
                        placeholder = "",
                        onClick = {
                            navigateTo(
                                screenFactory = { DatePickerScreen(it, state.startingDate) },
                                resultCallback = { result ->
                                    if (result != null) viewModel.setStartingDate(result)
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(2f.gridUnitsAsDp()))

                    LightTextField(
                        label = "Current weight",
                        value = state.currentWeightValue,
                        placeholder = "0",
                        onClick = {
                            navigateTo(
                                screenFactory = {
                                    NumberEditorScreen(
                                        it,
                                        title = "Current weight",
                                        initialValue = state.currentWeightValue,
                                        isDecimal = true,
                                    )
                                },
                                resultCallback = { result ->
                                    if (result != null) viewModel.setCurrentWeightValue(result)
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(1.5f.gridUnitsAsDp()))

                    LightTextField(
                        label = "Date",
                        value = dateLabel(state.currentDate),
                        placeholder = "",
                        onClick = {
                            navigateTo(
                                screenFactory = { DatePickerScreen(it, state.currentDate) },
                                resultCallback = { result ->
                                    if (result != null) viewModel.setCurrentDate(result)
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(1.5f.gridUnitsAsDp()))

                    LightTextField(
                        label = "Unit",
                        value = state.selectedUnit.label,
                        placeholder = "",
                        onClick = {
                            navigateTo(
                                screenFactory = { WeightUnitPickerScreen(it, state.selectedUnit) },
                                resultCallback = { result ->
                                    if (result != null) viewModel.setUnit(result)
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(2f.gridUnitsAsDp()))

                    StatRow(label = "Average change per week", value = state.averageChangeDisplay)
                }

                if (state.showSaved) {
                    LightText(
                        text = "Saved",
                        variant = LightTextVariant.Fine,
                        lighten = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp()),
                    )
                }

                LightBottomBar(
                    items = listOf(
                        LightBarButton.LightIcon(
                            icon = LightIcons.SAVE_TO_ALBUM,
                            onClick = { viewModel.save() },
                            contentDescription = "Save",
                        ),
                        LightBarButton.LightIcon(
                            icon = LightIcons.LIST,
                            onClick = { navigateTo(screenFactory = { WeightHistoryScreen(it, repo) }) },
                            contentDescription = "History",
                        ),
                        LightBarButton.Text(
                            text = "RESET",
                            onClick = {
                                navigateTo(
                                    screenFactory = {
                                        ConfirmResetScreen(
                                            it,
                                            "Reset all weight data? This will permanently clear your starting weight and every logged entry.",
                                        )
                                    },
                                    resultCallback = { confirmed ->
                                        if (confirmed == true) viewModel.reset()
                                    },
                                )
                            },
                        ),
                    ),
                )
            }
        }
    }
}
