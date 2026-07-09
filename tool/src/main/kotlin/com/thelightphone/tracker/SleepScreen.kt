package com.thelightphone.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SleepState(
    // Sleep and wake each get their own explicit date, rather than one
    // shared date with an inferred "next day" — clearer to read back, and
    // it also just works for same-day naps without any special-casing.
    val sleepDate: String = previousDayStr(),
    val sleepHour: String = "",
    val sleepMinute: String = "",
    val sleepAmPm: AmPm? = null, // only used when timeFormat is AM_PM
    val wakeDate: String = todayStr(),
    val wakeHour: String = "",
    val wakeMinute: String = "",
    val wakeAmPm: AmPm? = null,
    // Refreshed every time the screen shows (an external setting, not
    // something edited here), same reasoning as Cycle's moodFeatureEnabled.
    val timeFormat: TimeFormat = TimeFormat.AM_PM,
    val weeklyAvgDisplay: String = "0h 0m",
    val monthlyAvgDisplay: String = "0h 0m",
    val showSaved: Boolean = false,
)

class SleepViewModel(private val repo: TrackerRepository) : LightViewModel<Unit>() {
    private val _state = MutableStateFlow(SleepState())
    val state: StateFlow<SleepState> = _state.asStateFlow()

    private val dbMutex = Mutex()
    private var savedToken = 0L

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        reload()
    }

    private fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            dbMutex.withLock {
                _state.value = _state.value.copy(
                    timeFormat = repo.getTimeFormat(),
                    weeklyAvgDisplay = formatSleep(repo.getWeeklyAvgSleepMinutes()),
                    monthlyAvgDisplay = formatSleep(repo.getMonthlyAvgSleepMinutes()),
                )
            }
        }
    }

    fun setSleepDate(date: String) {
        _state.value = _state.value.copy(sleepDate = date)
    }

    fun setSleepHour(value: String) {
        _state.value = _state.value.copy(sleepHour = value)
    }

    fun setSleepMinute(value: String) {
        _state.value = _state.value.copy(sleepMinute = value)
    }

    fun setSleepAmPm(value: AmPm) {
        _state.value = _state.value.copy(sleepAmPm = value)
    }

    fun setWakeDate(date: String) {
        _state.value = _state.value.copy(wakeDate = date)
    }

    fun setWakeHour(value: String) {
        _state.value = _state.value.copy(wakeHour = value)
    }

    fun setWakeMinute(value: String) {
        _state.value = _state.value.copy(wakeMinute = value)
    }

    fun setWakeAmPm(value: AmPm) {
        _state.value = _state.value.copy(wakeAmPm = value)
    }

    fun save() {
        val s = _state.value
        val sleepHour = s.sleepHour.toIntOrNull()
        val sleepMinute = s.sleepMinute.toIntOrNull()
        val wakeHour = s.wakeHour.toIntOrNull()
        val wakeMinute = s.wakeMinute.toIntOrNull()
        if (sleepHour == null || sleepMinute == null || wakeHour == null || wakeMinute == null) return

        val sleepMinutesOfDay = if (s.timeFormat == TimeFormat.AM_PM) {
            val ampm = s.sleepAmPm ?: return
            to24HourMinutes(sleepHour, sleepMinute, ampm)
        } else {
            to24HourFormatMinutes(sleepHour, sleepMinute)
        }
        val wakeMinutesOfDay = if (s.timeFormat == TimeFormat.AM_PM) {
            val ampm = s.wakeAmPm ?: return
            to24HourMinutes(wakeHour, wakeMinute, ampm)
        } else {
            to24HourFormatMinutes(wakeHour, wakeMinute)
        }

        val duration = computeSleepDurationMinutes(s.sleepDate, sleepMinutesOfDay, s.wakeDate, wakeMinutesOfDay)
        if (duration <= 0) return // wake wasn't actually after sleep — nothing sensible to save

        viewModelScope.launch(Dispatchers.IO) {
            dbMutex.withLock {
                repo.addSleepSession(s.sleepDate, sleepMinutesOfDay, s.wakeDate, wakeMinutesOfDay)

                val nextSleepDate = previousDayStr()
                _state.value = _state.value.copy(
                    sleepDate = nextSleepDate,
                    sleepHour = "",
                    sleepMinute = "",
                    sleepAmPm = null,
                    wakeDate = todayStr(),
                    wakeHour = "",
                    wakeMinute = "",
                    wakeAmPm = null,
                    weeklyAvgDisplay = formatSleep(repo.getWeeklyAvgSleepMinutes()),
                    monthlyAvgDisplay = formatSleep(repo.getMonthlyAvgSleepMinutes()),
                )

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
                repo.resetSleep()
                _state.value = SleepState(
                    timeFormat = _state.value.timeFormat,
                    weeklyAvgDisplay = "0h 0m",
                    monthlyAvgDisplay = "0h 0m",
                )
            }
        }
    }
}

class SleepScreen(
    sealedActivity: SealedLightActivity,
    private val repo: TrackerRepository,
) : LightScreen<Unit, SleepViewModel>(sealedActivity) {

    override val viewModelClass: Class<SleepViewModel>
        get() = SleepViewModel::class.java

    override fun createViewModel() = SleepViewModel(repo)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()
        val is24Hour = state.timeFormat == TimeFormat.HOUR_24

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
                    center = LightTopBarCenter.Text("Sleep"),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    LightText(text = "Sleep", variant = LightTextVariant.Detail)
                    Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))

                    LightTextField(
                        label = "Date",
                        value = dateLabel(state.sleepDate),
                        placeholder = "",
                        onClick = {
                            navigateTo(
                                screenFactory = { DatePickerScreen(it, state.sleepDate) },
                                resultCallback = { result -> if (result != null) viewModel.setSleepDate(result) },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        LightTextField(
                            label = if (is24Hour) "Hour (0-23)" else "Hour",
                            value = state.sleepHour,
                            placeholder = "0",
                            onClick = {
                                navigateTo(
                                    screenFactory = {
                                        NumberEditorScreen(it, title = "Sleep hour", initialValue = state.sleepHour)
                                    },
                                    resultCallback = { result -> if (result != null) viewModel.setSleepHour(result) },
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.weight(0.2f))
                        LightTextField(
                            label = "Minute",
                            value = state.sleepMinute,
                            placeholder = "0",
                            onClick = {
                                navigateTo(
                                    screenFactory = {
                                        NumberEditorScreen(it, title = "Sleep minute", initialValue = state.sleepMinute)
                                    },
                                    resultCallback = { result -> if (result != null) viewModel.setSleepMinute(result) },
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (!is24Hour) {
                            Spacer(modifier = Modifier.weight(0.2f))
                            LightTextField(
                                label = "AM/PM",
                                value = state.sleepAmPm?.label ?: "",
                                placeholder = "-",
                                onClick = {
                                    navigateTo(
                                        screenFactory = { AmPmPickerScreen(it, state.sleepAmPm) },
                                        resultCallback = { result -> if (result != null) viewModel.setSleepAmPm(result) },
                                    )
                                },
                                modifier = Modifier.weight(0.8f),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2f.gridUnitsAsDp()))

                    LightText(text = "Wake", variant = LightTextVariant.Detail)
                    Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))

                    LightTextField(
                        label = "Date",
                        value = dateLabel(state.wakeDate),
                        placeholder = "",
                        onClick = {
                            navigateTo(
                                screenFactory = { DatePickerScreen(it, state.wakeDate) },
                                resultCallback = { result -> if (result != null) viewModel.setWakeDate(result) },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        LightTextField(
                            label = if (is24Hour) "Hour (0-23)" else "Hour",
                            value = state.wakeHour,
                            placeholder = "0",
                            onClick = {
                                navigateTo(
                                    screenFactory = {
                                        NumberEditorScreen(it, title = "Wake hour", initialValue = state.wakeHour)
                                    },
                                    resultCallback = { result -> if (result != null) viewModel.setWakeHour(result) },
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.weight(0.2f))
                        LightTextField(
                            label = "Minute",
                            value = state.wakeMinute,
                            placeholder = "0",
                            onClick = {
                                navigateTo(
                                    screenFactory = {
                                        NumberEditorScreen(it, title = "Wake minute", initialValue = state.wakeMinute)
                                    },
                                    resultCallback = { result -> if (result != null) viewModel.setWakeMinute(result) },
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (!is24Hour) {
                            Spacer(modifier = Modifier.weight(0.2f))
                            LightTextField(
                                label = "AM/PM",
                                value = state.wakeAmPm?.label ?: "",
                                placeholder = "-",
                                onClick = {
                                    navigateTo(
                                        screenFactory = { AmPmPickerScreen(it, state.wakeAmPm) },
                                        resultCallback = { result -> if (result != null) viewModel.setWakeAmPm(result) },
                                    )
                                },
                                modifier = Modifier.weight(0.8f),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2f.gridUnitsAsDp()))

                    val sleepHourInt = state.sleepHour.toIntOrNull()
                    val sleepMinuteInt = state.sleepMinute.toIntOrNull()
                    val sleepAmPmValue = state.sleepAmPm
                    val wakeHourInt = state.wakeHour.toIntOrNull()
                    val wakeMinuteInt = state.wakeMinute.toIntOrNull()
                    val wakeAmPmValue = state.wakeAmPm

                    val sleepTimeReady = sleepHourInt != null && sleepMinuteInt != null &&
                        (is24Hour || sleepAmPmValue != null)
                    val wakeTimeReady = wakeHourInt != null && wakeMinuteInt != null &&
                        (is24Hour || wakeAmPmValue != null)

                    val sessionPreview = if (sleepTimeReady && wakeTimeReady) {
                        val sleepMinutesOfDay = if (is24Hour) {
                            to24HourFormatMinutes(sleepHourInt!!, sleepMinuteInt!!)
                        } else {
                            to24HourMinutes(sleepHourInt!!, sleepMinuteInt!!, sleepAmPmValue!!)
                        }
                        val wakeMinutesOfDay = if (is24Hour) {
                            to24HourFormatMinutes(wakeHourInt!!, wakeMinuteInt!!)
                        } else {
                            to24HourMinutes(wakeHourInt!!, wakeMinuteInt!!, wakeAmPmValue!!)
                        }
                        val duration = computeSleepDurationMinutes(
                            state.sleepDate, sleepMinutesOfDay, state.wakeDate, wakeMinutesOfDay,
                        )
                        if (duration > 0) formatSleep(duration) else "Check dates and times"
                    } else {
                        "Incomplete"
                    }
                    StatRow(label = "This session", value = sessionPreview)
                    Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
                    StatRow(label = "Weekly avg", value = state.weeklyAvgDisplay)
                    Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
                    StatRow(label = "Monthly avg", value = state.monthlyAvgDisplay)
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
                            onClick = { navigateTo(screenFactory = { SleepHistoryScreen(it, repo) }) },
                            contentDescription = "History",
                        ),
                        LightBarButton.Text(
                            text = "RESET",
                            onClick = {
                                navigateTo(
                                    screenFactory = {
                                        ConfirmResetScreen(
                                            it,
                                            "Reset all sleep data? This will clear weekly and monthly averages.",
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
