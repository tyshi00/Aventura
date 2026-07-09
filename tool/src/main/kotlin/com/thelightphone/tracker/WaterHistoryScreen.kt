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

data class WaterHistoryState(
    val previousMonthDisplay: String = "0 ml",
    val yearlyAverageDisplay: String = "0 ml",
)

class WaterHistoryViewModel(private val repo: TrackerRepository) : LightViewModel<Unit>() {
    private val _state = MutableStateFlow(WaterHistoryState())
    val state: StateFlow<WaterHistoryState> = _state.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        viewModelScope.launch(Dispatchers.IO) {
            val unit = repo.getWaterUnit()
            val previousMonth = repo.getPreviousMonthWaterMl()
            val yearlyAverage = repo.getYearlyAverageWaterMl()
            _state.value = WaterHistoryState(
                previousMonthDisplay = "${WaterConversion.format(previousMonth, unit)} ${unit.label}",
                yearlyAverageDisplay = "${WaterConversion.format(yearlyAverage, unit)} ${unit.label}",
            )
        }
    }
}

class WaterHistoryScreen(
    sealedActivity: SealedLightActivity,
    private val repo: TrackerRepository,
) : LightScreen<Unit, WaterHistoryViewModel>(sealedActivity) {

    override val viewModelClass: Class<WaterHistoryViewModel>
        get() = WaterHistoryViewModel::class.java

    override fun createViewModel() = WaterHistoryViewModel(repo)

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
                    center = LightTopBarCenter.Text("Water history"),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    StatRow(label = "Previous month total", value = state.previousMonthDisplay)
                    Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
                    StatRow(label = "Yearly avg (per month)", value = state.yearlyAverageDisplay)
                }

                LightBottomBar(items = listOf())
            }
        }
    }
}
