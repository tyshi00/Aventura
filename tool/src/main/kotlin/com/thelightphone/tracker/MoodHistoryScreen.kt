package com.thelightphone.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

data class MoodHistoryItem(
    val id: Long,
    val date: String,
    val dateDisplay: String,
    val moodsDisplay: String,
    val notesDisplay: String?,
    val duringCycle: Boolean,
)

data class MoodHistoryState(
    val entries: List<MoodHistoryItem> = emptyList(),
    val loaded: Boolean = false,
)

class MoodHistoryViewModel(private val repo: TrackerRepository) : LightViewModel<Unit>() {
    private val _state = MutableStateFlow(MoodHistoryState())
    val state: StateFlow<MoodHistoryState> = _state.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        reload()
    }

    private fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            val history = repo.getMoodHistory()
            // Only bother cross-referencing cycles if Cycle tracking is
            // actually in use — no point checking otherwise.
            val cycleTrackingOn = repo.getCycleFeatureEnabled()

            val items = history.map { entry ->
                MoodHistoryItem(
                    id = entry.id,
                    date = entry.date,
                    dateDisplay = dateLabel(entry.date),
                    moodsDisplay = decodeMoods(entry.moods).joinToString(", ") { it.label },
                    notesDisplay = entry.notes?.takeIf { it.isNotBlank() },
                    duringCycle = cycleTrackingOn && repo.isDateWithinAnyCycle(entry.date),
                )
            }
            _state.value = MoodHistoryState(
                entries = items.sortedWith(compareByDescending<MoodHistoryItem> { it.date }.thenByDescending { it.id }),
                loaded = true,
            )
        }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteMoodEntry(id)
            reload()
        }
    }
}

class MoodHistoryScreen(
    sealedActivity: SealedLightActivity,
    private val repo: TrackerRepository,
) : LightScreen<Unit, MoodHistoryViewModel>(sealedActivity) {

    override val viewModelClass: Class<MoodHistoryViewModel>
        get() = MoodHistoryViewModel::class.java

    override fun createViewModel() = MoodHistoryViewModel(repo)

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
                    center = LightTopBarCenter.Text("Mood history"),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                if (state.loaded && state.entries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 1f.gridUnitsAsDp()),
                        contentAlignment = Alignment.Center,
                    ) {
                        LightText(
                            text = "No moods logged yet.",
                            variant = LightTextVariant.Copy,
                            lighten = true,
                        )
                    }
                } else {
                    LightScrollView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 1f.gridUnitsAsDp()),
                    ) {
                        state.entries.forEachIndexed { index, item ->
                            MoodHistoryRow(
                                item = item,
                                onClick = {
                                    navigateTo(
                                        screenFactory = {
                                            ConfirmResetScreen(
                                                it,
                                                "Delete this mood entry?",
                                                title = "Confirm deletion",
                                                confirmLabel = "DELETE",
                                            )
                                        },
                                        resultCallback = { confirmed ->
                                            if (confirmed == true) viewModel.deleteEntry(item.id)
                                        },
                                    )
                                },
                            )
                            if (index != state.entries.lastIndex) {
                                Spacer(modifier = Modifier.height(1.25f.gridUnitsAsDp()))
                            }
                        }
                    }
                }

                LightBottomBar(items = listOf())
            }
        }
    }
}

@Composable
private fun MoodHistoryRow(item: MoodHistoryItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        LightText(
            text = if (item.duringCycle) "${item.dateDisplay} (during cycle)" else item.dateDisplay,
            variant = LightTextVariant.Copy,
        )
        Spacer(modifier = Modifier.height(0.25f.gridUnitsAsDp()))
        LightText(
            text = item.moodsDisplay,
            variant = LightTextVariant.Fine,
            lighten = true,
        )
        item.notesDisplay?.let { notes ->
            LightText(
                text = notes,
                variant = LightTextVariant.Fine,
                lighten = true,
            )
        }
    }
}

