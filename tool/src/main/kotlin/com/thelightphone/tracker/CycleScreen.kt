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

data class CycleState(
    val editingId: Long? = null,
    val startDate: String = todayStr(),
    val endDate: String? = null,
    val flow: FlowLevel? = null,
    val moods: List<Mood> = emptyList(),
    val energy: EnergyLevel? = null,
    val nextExpectedDisplay: String = "Not enough data yet",
    val lastCycleDisplay: String = "None logged",
    // Whether the standalone Mood tracker is on — checked every screen show
    // (not just once) since it's an external setting, not something this
    // screen edits itself. When true, Cycle's own Mood field is replaced by
    // a shortcut into the Mood tracker instead, to avoid asking someone who
    // tracks both to log mood twice during the same days.
    val moodFeatureEnabled: Boolean = false,
    val showSaved: Boolean = false,
)

class CycleViewModel(private val repo: TrackerRepository) : LightViewModel<Unit>() {
    private val _state = MutableStateFlow(CycleState())
    val state: StateFlow<CycleState> = _state.asStateFlow()

    // Guards save/reset/reload so they can't race each other.
    private val dbMutex = Mutex()

    // Only load the editable fields (start/end date, flow, mood, energy)
    // from the database the FIRST time this screen is shown. On every later
    // onScreenShow (e.g. returning from the date/flow/mood/energy pickers),
    // we only refresh the read-only stat displays — otherwise a stale DB
    // read would stomp over the selection the person just made before they
    // had a chance to tap SAVE.
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
        val recent = repo.getMostRecentCycle()
        // If the most recent cycle has no end date yet, treat it as still in
        // progress and keep editing that same record rather than logging a
        // brand new one every time details are added.
        val editing = recent?.takeIf { it.endDate == null }

        _state.value = _state.value.copy(
            editingId = editing?.id,
            startDate = editing?.startDate ?: todayStr(),
            endDate = editing?.endDate,
            flow = editing?.flow?.let { name -> FlowLevel.entries.firstOrNull { it.name == name } },
            moods = decodeMoods(editing?.moods),
            energy = editing?.energy?.let { name -> EnergyLevel.entries.firstOrNull { it.name == name } },
        )
    }

    private suspend fun refreshStatsOnly() {
        val recent = repo.getMostRecentCycle()
        val next = repo.predictNextCycleStart()
        _state.value = _state.value.copy(
            nextExpectedDisplay = next?.let { dateLabel(it) } ?: "Not enough data yet",
            lastCycleDisplay = recent?.let {
                dateLabel(it.startDate) + (it.endDate?.let { end -> " – ${dateLabel(end)}" } ?: " (ongoing)")
            } ?: "None logged",
            moodFeatureEnabled = repo.getMoodFeatureEnabled(),
        )
    }

    fun setStartDate(date: String) {
        _state.value = _state.value.copy(startDate = date)
    }

    fun setEndDate(date: String) {
        _state.value = _state.value.copy(endDate = date)
    }

    fun setFlow(flow: FlowLevel) {
        _state.value = _state.value.copy(flow = flow)
    }

    fun setMoods(moods: List<Mood>) {
        _state.value = _state.value.copy(moods = moods)
    }

    fun setEnergy(energy: EnergyLevel) {
        _state.value = _state.value.copy(energy = energy)
    }

    fun save() {
        viewModelScope.launch(Dispatchers.IO) {
            dbMutex.withLock {
                val s = _state.value
                repo.saveCycle(
                    id = s.editingId,
                    startDate = s.startDate,
                    endDate = s.endDate,
                    flow = s.flow,
                    moods = s.moods,
                    energy = s.energy,
                )
                // Pick up the id if this save just inserted a new row (so
                // further edits before leaving the screen update it rather
                // than inserting duplicates), and refresh the stat displays.
                // The editable fields themselves are left alone — they
                // already reflect exactly what was just saved.
                val recent = repo.getMostRecentCycle()
                val editing = recent?.takeIf { it.endDate == null }
                _state.value = _state.value.copy(editingId = editing?.id)
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
                repo.resetCycles()
                _state.value = CycleState()
                // Force the next onScreenShow to re-derive from the (now
                // empty) database instead of treating this as still loaded.
                loadedInitialState = false
            }
        }
    }
}

class CycleScreen(
    sealedActivity: SealedLightActivity,
    private val repo: TrackerRepository,
) : LightScreen<Unit, CycleViewModel>(sealedActivity) {

    override val viewModelClass: Class<CycleViewModel>
        get() = CycleViewModel::class.java

    override fun createViewModel() = CycleViewModel(repo)

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
                    center = LightTopBarCenter.Text("Cycle"),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    LightTextField(
                        label = "Start date",
                        value = dateLabel(state.startDate),
                        placeholder = "",
                        onClick = {
                            navigateTo(
                                screenFactory = { DatePickerScreen(it, state.startDate) },
                                resultCallback = { result ->
                                    if (result != null) viewModel.setStartDate(result)
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(1.5f.gridUnitsAsDp()))

                    LightTextField(
                        label = "End date",
                        value = state.endDate?.let { dateLabel(it) } ?: "Not set",
                        placeholder = "",
                        onClick = {
                            navigateTo(
                                screenFactory = {
                                    DatePickerScreen(it, state.endDate ?: state.startDate)
                                },
                                resultCallback = { result ->
                                    if (result != null) viewModel.setEndDate(result)
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(1.5f.gridUnitsAsDp()))

                    LightTextField(
                        label = "Flow",
                        value = state.flow?.label ?: "Not set",
                        placeholder = "",
                        onClick = {
                            navigateTo(
                                screenFactory = { FlowPickerScreen(it, state.flow) },
                                resultCallback = { result ->
                                    if (result != null) viewModel.setFlow(result)
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(1.5f.gridUnitsAsDp()))

                    LightTextField(
                        label = "Mood",
                        value = if (state.moodFeatureEnabled) {
                            "Log in Mood tracker"
                        } else if (state.moods.isEmpty()) {
                            "Not set"
                        } else {
                            state.moods.joinToString(", ") { it.label }
                        },
                        placeholder = "",
                        onClick = {
                            if (state.moodFeatureEnabled) {
                                // Mood tracking is on — log there instead of
                                // keeping a second, separate mood field here,
                                // so tracking both isn't a double entry.
                                // Defaults to today (not the cycle's start
                                // date) since this is meant for logging
                                // however you feel right now — backdating is
                                // still available from inside Mood itself.
                                navigateTo(
                                    screenFactory = {
                                        MoodScreen(it, repo, initialDate = todayStr())
                                    },
                                )
                            } else {
                                navigateTo(
                                    screenFactory = { MoodMultiPickerScreen(it, state.moods) },
                                    resultCallback = { result ->
                                        if (result != null) viewModel.setMoods(result)
                                    },
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                    )

                    if (state.moodFeatureEnabled) {
                        LightText(
                            text = "Logs one day — add more days in Mood",
                            variant = LightTextVariant.Fine,
                            lighten = true,
                            modifier = Modifier.padding(top = 0.4f.gridUnitsAsDp()),
                        )
                    }

                    Spacer(modifier = Modifier.height(1.5f.gridUnitsAsDp()))

                    LightTextField(
                        label = "Energy",
                        value = state.energy?.label ?: "Not set",
                        placeholder = "",
                        onClick = {
                            navigateTo(
                                screenFactory = { EnergyPickerScreen(it, state.energy) },
                                resultCallback = { result ->
                                    if (result != null) viewModel.setEnergy(result)
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(2f.gridUnitsAsDp()))

                    StatRow(label = "Next expected", value = state.nextExpectedDisplay)
                    Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
                    StatRow(label = "Last cycle", value = state.lastCycleDisplay)
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
                            onClick = { navigateTo(screenFactory = { CycleHistoryScreen(it, repo) }) },
                            contentDescription = "History",
                        ),
                        LightBarButton.Text(
                            text = "RESET",
                            onClick = {
                                navigateTo(
                                    screenFactory = {
                                        ConfirmResetScreen(
                                            it,
                                            "Reset all cycle data? This will permanently clear every logged cycle.",
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
