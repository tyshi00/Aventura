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

data class CycleHistoryItem(
    val id: Long,
    val startDate: String,
    val dateRangeDisplay: String,
    val flowDisplay: String,
    val moodDisplay: String,
    val energyDisplay: String,
)

data class CycleHistoryState(
    val entries: List<CycleHistoryItem> = emptyList(),
    val loaded: Boolean = false,
)

class CycleHistoryViewModel(private val repo: TrackerRepository) : LightViewModel<Unit>() {
    private val _state = MutableStateFlow(CycleHistoryState())
    val state: StateFlow<CycleHistoryState> = _state.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        reload()
    }

    private fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            val history = repo.getCycleHistory()
            val items = history.map { entry ->
                val ownMoods = decodeMoods(entry.moods)
                val moodDisplay = if (ownMoods.isNotEmpty()) {
                    ownMoods.joinToString(", ") { it.label }
                } else if (repo.hasMoodEntriesInRange(entry.startDate, entry.endDate)) {
                    // Nothing stored directly on this cycle, but real Mood
                    // entries exist within its date range — point there
                    // instead of showing a misleading "Not set".
                    "See Mood history"
                } else {
                    "Not set"
                }

                CycleHistoryItem(
                    id = entry.id,
                    startDate = entry.startDate,
                    dateRangeDisplay = dateLabel(entry.startDate) +
                        (entry.endDate?.let { " – ${dateLabel(it)}" } ?: " (ongoing)"),
                    flowDisplay = entry.flow
                        ?.let { name -> FlowLevel.entries.firstOrNull { it.name == name }?.label }
                        ?: "Not set",
                    moodDisplay = moodDisplay,
                    energyDisplay = entry.energy
                        ?.let { name -> EnergyLevel.entries.firstOrNull { it.name == name }?.label }
                        ?: "Not set",
                )
            }
            _state.value = CycleHistoryState(
                entries = items.sortedByDescending { it.startDate },
                loaded = true,
            )
        }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteCycleEntry(id)
            reload()
        }
    }
}

class CycleHistoryScreen(
    sealedActivity: SealedLightActivity,
    private val repo: TrackerRepository,
) : LightScreen<Unit, CycleHistoryViewModel>(sealedActivity) {

    override val viewModelClass: Class<CycleHistoryViewModel>
        get() = CycleHistoryViewModel::class.java

    override fun createViewModel() = CycleHistoryViewModel(repo)

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
                    center = LightTopBarCenter.Text("Cycle history"),
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
                            text = "No cycles logged yet.",
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
                            CycleHistoryRow(
                                item = item,
                                onClick = {
                                    navigateTo(
                                        screenFactory = {
                                            ConfirmResetScreen(
                                                it,
                                                "Delete this cycle entry?",
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
private fun CycleHistoryRow(item: CycleHistoryItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        LightText(
            text = item.dateRangeDisplay,
            variant = LightTextVariant.Copy,
        )
        Spacer(modifier = Modifier.height(0.25f.gridUnitsAsDp()))
        LightText(
            text = "Flow: ${item.flowDisplay}",
            variant = LightTextVariant.Fine,
            lighten = true,
        )
        LightText(
            text = "Mood: ${item.moodDisplay}",
            variant = LightTextVariant.Fine,
            lighten = true,
        )
        LightText(
            text = "Energy: ${item.energyDisplay}",
            variant = LightTextVariant.Fine,
            lighten = true,
        )
    }
}

