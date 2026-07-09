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

data class WeightHistoryItem(
    val date: String,
    val dateDisplay: String,
    val weightDisplay: String,
)

data class WeightHistoryState(
    val entries: List<WeightHistoryItem> = emptyList(),
    val loaded: Boolean = false,
)

class WeightHistoryViewModel(private val repo: TrackerRepository) : LightViewModel<Unit>() {
    private val _state = MutableStateFlow(WeightHistoryState())
    val state: StateFlow<WeightHistoryState> = _state.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        reload()
    }

    private fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            val unit = repo.getWeightUnit()
            val history = repo.getWeightHistory()
            _state.value = WeightHistoryState(
                entries = history.map { entry ->
                    WeightHistoryItem(
                        date = entry.date,
                        dateDisplay = dateLabel(entry.date),
                        weightDisplay = "${WeightConversion.format(entry.weightKg, unit)} ${unit.label}",
                    )
                }.sortedByDescending { it.date },
                loaded = true,
            )
        }
    }

    fun deleteEntry(date: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteWeightEntry(date)
            reload()
        }
    }
}

class WeightHistoryScreen(
    sealedActivity: SealedLightActivity,
    private val repo: TrackerRepository,
) : LightScreen<Unit, WeightHistoryViewModel>(sealedActivity) {

    override val viewModelClass: Class<WeightHistoryViewModel>
        get() = WeightHistoryViewModel::class.java

    override fun createViewModel() = WeightHistoryViewModel(repo)

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
                    center = LightTopBarCenter.Text("Weight history"),
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
                            text = "No weight logged yet.",
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
                            LightText(
                                text = "${item.dateDisplay}: ${item.weightDisplay}",
                                variant = LightTextVariant.Copy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        navigateTo(
                                            screenFactory = {
                                                ConfirmResetScreen(
                                                    it,
                                                    "Delete this weight entry?",
                                                    title = "Confirm deletion",
                                                    confirmLabel = "DELETE",
                                                )
                                            },
                                            resultCallback = { confirmed ->
                                                if (confirmed == true) viewModel.deleteEntry(item.date)
                                            },
                                        )
                                    },
                            )
                            if (index != state.entries.lastIndex) {
                                Spacer(modifier = Modifier.height(0.75f.gridUnitsAsDp()))
                            }
                        }
                    }
                }

                LightBottomBar(items = listOf())
            }
        }
    }
}

