package com.tyshi00.aventura

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.buildDatabase
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcon
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

data class HomeState(
    val history: List<CompletedQuestEntry> = emptyList(),
    val showStreaks: Boolean = true,
)

class HomeViewModel(private val repo: AventuraRepository) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        reload()
    }

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            val invertColors = repo.getInvertColors()
            if (invertColors) LightThemeController.setLightTheme() else LightThemeController.setDarkTheme()
            _state.value = HomeState(history = repo.getHistory(), showStreaks = repo.getShowStreaks())
        }
    }

    fun toggle(selected: SelectedQuest, done: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.setCompleted(selected, done)
            _state.value = _state.value.copy(history = repo.getHistory())
        }
    }
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, HomeViewModel>(sealedActivity) {

    private val repo by lazy {
        AventuraRepository.getInstance {
            lightContext.buildDatabase(AventuraDatabase::class.java, AventuraRepository.DB_NAME)
        }
    }

    override val viewModelClass: Class<HomeViewModel>
        get() = HomeViewModel::class.java

    override fun createViewModel() = HomeViewModel(repo)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()
        var tier by remember { mutableStateOf(QuestTier.DAILY) }

        val completed = remember(state.history) {
            state.history.mapTo(HashSet()) { it.completionKey }
        }
        val level = remember(state.history) { Levels.forXp(state.history.sumOf { it.xp }) }
        val streak = remember(state.history) { Streak.current(state.history) }
        val selection = remember(tier) { QuestSelector.selectionFor(tier) }
        val doneCount = selection.count { it.completionKey in completed }

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    center = LightTopBarCenter.Text("Aventura"),
                    rightButton = LightBarButton.Text(
                        text = "LV ${level.number}",
                        onClick = { navigateTo(screenFactory = { ProgressScreen(it, repo) }) },
                    ),
                    modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                )

                if (state.showStreaks && streak > 0) {
                    LightText(
                        text = "$streak day streak",
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.padding(
                            horizontal = 1f.gridUnitsAsDp(),
                            vertical = 0.25f.gridUnitsAsDp(),
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))

                TierTabs(
                    selected = tier,
                    onSelect = { tier = it },
                    modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
                )

                Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))

                LightText(
                    text = "$doneCount/${selection.size} done",
                    variant = LightTextVariant.Fine,
                    lighten = true,
                    modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
                )

                Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))

                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    selection.forEach { item ->
                        QuestCard(
                            selected = item,
                            done = item.completionKey in completed,
                            onToggle = { now -> viewModel.toggle(item, now) },
                        )
                        Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
                    }
                    Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
                }

                LightBottomBar(
                    items = listOf(
                        null,
                        null,
                        null,
                        null,
                        LightBarButton.LightIcon(
                            icon = LightIcons.SETTINGS,
                            onClick = { navigateTo(screenFactory = { SettingsScreen(it, repo) }) },
                        ),
                    ),
                )
            }
        }
    }
}

@Composable
private fun TierTabs(
    selected: QuestTier,
    onSelect: (QuestTier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        QuestTier.entries.forEach { t ->
            val isSelected = t == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(t) }
                    .padding(vertical = 0.5f.gridUnitsAsDp()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LightText(
                    text = t.label.uppercase(),
                    variant = LightTextVariant.Detail,
                    lighten = !isSelected,
                    underline = isSelected,
                )
            }
        }
    }
}

@Composable
private fun QuestCard(selected: SelectedQuest, done: Boolean, onToggle: (Boolean) -> Unit) {
    val quest = selected.quest
    val xp = Xp.forKind(selected.completionKey.substringBefore("|"))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!done) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            LightIcon(icon = LightIcons.CIRCLE)
            if (done) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_tick),
                    contentDescription = "done",
                    tint = LightThemeTokens.colors.content,
                    modifier = Modifier.size(1.1f.gridUnitsAsDp()),
                )
            }
        }
        Spacer(modifier = Modifier.width(1f.gridUnitsAsDp()))
        Column(modifier = Modifier.weight(1f)) {
            val meta = buildString {
                if (quest.category.isNotEmpty()) append(quest.category.uppercase())
                if (quest.minutes > 0) {
                    if (isNotEmpty()) append("  ·  ")
                    append("${quest.minutes} min")
                }
                if (isNotEmpty()) append("  ·  ")
                append("+$xp XP")
            }
            LightText(text = meta, variant = LightTextVariant.Superfine, lighten = true)
            LightText(
                text = quest.title,
                variant = LightTextVariant.Copy,
                underline = done,
            )
            LightText(
                text = quest.description,
                variant = LightTextVariant.Detail,
                lighten = true,
            )
        }
    }
}
