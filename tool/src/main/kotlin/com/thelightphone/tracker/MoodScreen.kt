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

private const val MOOD_NOTES_MAX_LENGTH = 250

data class MoodState(
    val selectedDate: String = todayStr(),
    val selectedMoods: List<Mood> = emptyList(),
    val notes: String = "",
    val showSaved: Boolean = false,
)

class MoodViewModel(private val repo: TrackerRepository) : LightViewModel<Unit>() {
    private val _state = MutableStateFlow(MoodState())
    val state: StateFlow<MoodState> = _state.asStateFlow()

    // Every save creates a brand new entry (multiple per day allowed) and
    // the form clears back to blank right after — so you can log a second,
    // different mood later the same day without the previous entry's
    // selections still sitting there.
    private val dbMutex = Mutex()

    // Guards against a slower, earlier save's delayed "hide" wiping out a
    // more recent save's still-visible confirmation.
    private var savedToken = 0L

    fun setInitialDate(date: String) {
        _state.value = _state.value.copy(selectedDate = date)
    }

    fun setDate(date: String) {
        _state.value = _state.value.copy(selectedDate = date)
    }

    fun setMoods(moods: List<Mood>) {
        _state.value = _state.value.copy(selectedMoods = moods)
    }

    fun setNotes(notes: String) {
        _state.value = _state.value.copy(notes = notes)
    }

    fun save() {
        val s = _state.value
        if (s.selectedMoods.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            dbMutex.withLock {
                repo.addMoodEntry(s.selectedDate, s.selectedMoods, s.notes)
                _state.value = MoodState(showSaved = true)

                val myToken = ++savedToken
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
                repo.resetMood()
                _state.value = MoodState()
            }
        }
    }
}

class MoodScreen(
    sealedActivity: SealedLightActivity,
    private val repo: TrackerRepository,
    private val initialDate: String? = null,
) : LightScreen<Unit, MoodViewModel>(sealedActivity) {

    override val viewModelClass: Class<MoodViewModel>
        get() = MoodViewModel::class.java

    override fun createViewModel() = MoodViewModel(repo).also {
        if (initialDate != null) it.setInitialDate(initialDate)
    }

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
                    center = LightTopBarCenter.Text("Mood"),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
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

                    LightTextField(
                        label = "Mood",
                        value = if (state.selectedMoods.isEmpty()) {
                            "How are you today?"
                        } else {
                            state.selectedMoods.joinToString(", ") { it.label }
                        },
                        placeholder = "",
                        onClick = {
                            navigateTo(
                                screenFactory = { MoodMultiPickerScreen(it, state.selectedMoods) },
                                resultCallback = { result ->
                                    if (result != null) viewModel.setMoods(result)
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                    )

                    Spacer(modifier = Modifier.height(1.5f.gridUnitsAsDp()))

                    LightTextField(
                        label = "Notes",
                        value = state.notes.ifBlank { "Not set" },
                        placeholder = "",
                        onClick = {
                            navigateTo(
                                screenFactory = {
                                    TextEditorScreen(
                                        it,
                                        title = "Notes",
                                        initialValue = state.notes,
                                        maxLength = MOOD_NOTES_MAX_LENGTH,
                                    )
                                },
                                resultCallback = { result ->
                                    if (result != null) viewModel.setNotes(result)
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 5,
                    )
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
                            onClick = { navigateTo(screenFactory = { MoodHistoryScreen(it, repo) }) },
                            contentDescription = "History",
                        ),
                        LightBarButton.Text(
                            text = "RESET",
                            onClick = {
                                navigateTo(
                                    screenFactory = {
                                        ConfirmResetScreen(
                                            it,
                                            "Reset all mood data? This will permanently clear every logged entry.",
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
