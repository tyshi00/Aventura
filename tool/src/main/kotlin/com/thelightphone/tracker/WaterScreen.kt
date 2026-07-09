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
import com.thelightphone.sdk.ui.LightTextField
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class WaterState(
    val amountValue: String = "",
    val selectedUnit: WaterUnit = WaterUnit.ML,
    val defaultUnit: WaterUnit = WaterUnit.ML,
    val selectedDate: String = todayStr(),
    val weeklyDisplay: String = "0 ml",
    val monthlyDisplay: String = "0 ml",
)

class WaterViewModel(private val repo: TrackerRepository) : LightViewModel<Unit>() {
    private val _state = MutableStateFlow(WaterState())
    val state: StateFlow<WaterState> = _state.asStateFlow()

    // Guards reload/save/reset so a screen-refresh read (triggered when the
    // screen becomes visible again) can't race a concurrent reset/save and
    // overwrite it with stale data.
    private val dbMutex = Mutex()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        viewModelScope.launch(Dispatchers.IO) {
            dbMutex.withLock {
                val unit = repo.getWaterUnit()
                val weekMl = repo.getWeeklyWaterMl()
                val monthMl = repo.getMonthlyWaterMl()
                _state.value = _state.value.copy(
                    selectedUnit = unit,
                    defaultUnit = unit,
                    weeklyDisplay = "${WaterConversion.format(weekMl, unit)} ${unit.label}",
                    monthlyDisplay = "${WaterConversion.format(monthMl, unit)} ${unit.label}",
                )
            }
        }
    }

    fun setAmount(value: String) {
        _state.value = _state.value.copy(amountValue = value)
    }

    fun setUnit(unit: WaterUnit) {
        viewModelScope.launch(Dispatchers.IO) {
            dbMutex.withLock {
                val weekMl = repo.getWeeklyWaterMl()
                val monthMl = repo.getMonthlyWaterMl()
                _state.value = _state.value.copy(
                    selectedUnit = unit,
                    weeklyDisplay = "${WaterConversion.format(weekMl, unit)} ${unit.label}",
                    monthlyDisplay = "${WaterConversion.format(monthMl, unit)} ${unit.label}",
                )
            }
        }
    }

    fun setDate(date: String) {
        _state.value = _state.value.copy(selectedDate = date)
    }

    fun save() {
        val amount = _state.value.amountValue.toDoubleOrNull() ?: return
        if (amount <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            dbMutex.withLock {
                repo.addWater(amount, _state.value.selectedUnit, _state.value.selectedDate)
                val unit = _state.value.selectedUnit
                val weekMl = repo.getWeeklyWaterMl()
                val monthMl = repo.getMonthlyWaterMl()
                _state.value = _state.value.copy(
                    amountValue = "",
                    selectedDate = todayStr(),
                    weeklyDisplay = "${WaterConversion.format(weekMl, unit)} ${unit.label}",
                    monthlyDisplay = "${WaterConversion.format(monthMl, unit)} ${unit.label}",
                )
            }
        }
    }

    fun reset() {
        viewModelScope.launch(Dispatchers.IO) {
            dbMutex.withLock {
                repo.resetWater()
                val unit = _state.value.selectedUnit
                _state.value = _state.value.copy(
                    amountValue = "",
                    weeklyDisplay = "0 ${unit.label}",
                    monthlyDisplay = "0 ${unit.label}",
                )
            }
        }
    }
}

class WaterScreen(
    sealedActivity: SealedLightActivity,
    private val repo: TrackerRepository,
) : LightScreen<Unit, WaterViewModel>(sealedActivity) {

    override val viewModelClass: Class<WaterViewModel>
        get() = WaterViewModel::class.java

    override fun createViewModel() = WaterViewModel(repo)

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
                    center = LightTopBarCenter.Text("Water"),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    // Date field — defaults to today, lets you log a forgotten day
                    LightTextField(
                        label = "Date",
                        value = dateLabel(state.selectedDate),
                        placeholder = "",
                        onClick = {
                            navigateTo(
                                screenFactory = { DatePickerScreen(it, state.selectedDate) },
                                resultCallback = { result ->
                                    if (result != null) viewModel.setDate(result)
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(1.5f.gridUnitsAsDp()))

                    // Amount field
                    LightTextField(
                        label = "Amount",
                        value = state.amountValue,
                        placeholder = "0",
                        onClick = {
                            navigateTo(
                                screenFactory = {
                                    NumberEditorScreen(
                                        it,
                                        title = "Amount",
                                        initialValue = state.amountValue,
                                        isDecimal = true,
                                    )
                                },
                                resultCallback = { result ->
                                    if (result != null) viewModel.setAmount(result)
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(1.5f.gridUnitsAsDp()))

                    // Unit picker
                    LightTextField(
                        label = "Unit",
                        value = state.selectedUnit.label,
                        placeholder = "",
                        onClick = {
                            navigateTo(
                                screenFactory = { UnitPickerScreen(it, state.selectedUnit) },
                                resultCallback = { result ->
                                    if (result != null) viewModel.setUnit(result)
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(2f.gridUnitsAsDp()))

                    StatRow(label = "Weekly total", value = state.weeklyDisplay)
                    Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
                    StatRow(label = "Monthly total", value = state.monthlyDisplay)
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
                            onClick = { navigateTo(screenFactory = { WaterHistoryScreen(it, repo) }) },
                            contentDescription = "History",
                        ),
                        LightBarButton.Text(
                            text = "RESET",
                            onClick = {
                                navigateTo(
                                    screenFactory = {
                                        ConfirmResetScreen(
                                            it,
                                            "Reset all water data? This will clear weekly and monthly totals.",
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
