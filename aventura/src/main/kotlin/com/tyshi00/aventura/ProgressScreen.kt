package com.tyshi00.aventura

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
import androidx.compose.runtime.remember
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
import java.time.Instant
import java.time.ZoneId

data class ProgressState(val history: List<CompletedQuestEntry> = emptyList())

class ProgressViewModel(private val repo: AventuraRepository) : LightViewModel<Unit>() {
    private val _state = MutableStateFlow(ProgressState())
    val state: StateFlow<ProgressState> = _state.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = ProgressState(history = repo.getHistory())
        }
    }
}

class ProgressScreen(
    sealedActivity: SealedLightActivity,
    private val repo: AventuraRepository,
) : LightScreen<Unit, ProgressViewModel>(sealedActivity) {

    override val viewModelClass: Class<ProgressViewModel>
        get() = ProgressViewModel::class.java

    override fun createViewModel() = ProgressViewModel(repo)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()

        val stats = remember(state.history) { state.history.stats() }
        val level = remember(stats) { Levels.forXp(stats.totalXp) }
        val next = remember(level) { Levels.next(level) }
        val unlocked = remember(state.history) { Trophies.unlocked(state.history) }
        val entries = remember(state.history) { state.history.sortedByDescending { it.completedAtMillis } }

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
                    center = LightTopBarCenter.Text("Progress"),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    LightText(text = "LV ${level.number}  ·  ${level.name}", variant = LightTextVariant.Heading)
                    val xpLine = if (next == null) {
                        "${stats.totalXp} XP · max level"
                    } else {
                        "${stats.totalXp} XP · ${next.minXp - stats.totalXp} to ${next.name}"
                    }
                    LightText(text = xpLine, variant = LightTextVariant.Fine, lighten = true)

                    Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))

                    val streakLine = if (stats.streak > 0) {
                        "${stats.streak} day streak" +
                            if (stats.bestStreak > stats.streak) " · best ${stats.bestStreak}" else ""
                    } else {
                        "No streak going. Finish one today to start."
                    }
                    LightText(text = streakLine, variant = LightTextVariant.Copy)

                    Spacer(modifier = Modifier.height(1.5f.gridUnitsAsDp()))

                    LightText(
                        text = "TROPHIES  ${unlocked.size}/${Trophies.all.size}",
                        variant = LightTextVariant.Detail,
                        lighten = true,
                    )
                    Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))
                    Trophies.all.forEach { trophy ->
                        val isUnlocked = trophy.id in unlocked
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 0.25f.gridUnitsAsDp())) {
                            Column {
                                LightText(
                                    text = if (isUnlocked) "[x] ${trophy.name}" else "[ ] ${trophy.name}",
                                    variant = LightTextVariant.Copy,
                                    lighten = !isUnlocked,
                                )
                                LightText(
                                    text = trophy.desc,
                                    variant = LightTextVariant.Detail,
                                    lighten = true,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(1.5f.gridUnitsAsDp()))

                    LightText(
                        text = "HISTORY  ${entries.size}",
                        variant = LightTextVariant.Detail,
                        lighten = true,
                    )
                    Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))
                    if (entries.isEmpty()) {
                        LightText(
                            text = "No quests done yet.",
                            variant = LightTextVariant.Fine,
                            lighten = true,
                        )
                    } else {
                        entries.forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 0.35f.gridUnitsAsDp()),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    LightText(text = formatDate(entry.completedAtMillis), variant = LightTextVariant.Superfine, lighten = true)
                                    LightText(text = entry.title, variant = LightTextVariant.Fine)
                                }
                                LightText(text = "+${entry.xp}", variant = LightTextVariant.Fine, lighten = true)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
                }

                LightBottomBar(items = listOf())
            }
        }
    }
}

private fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
