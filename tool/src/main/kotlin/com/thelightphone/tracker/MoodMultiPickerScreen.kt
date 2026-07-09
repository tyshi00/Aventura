package com.thelightphone.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
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

private const val MAX_MOODS = 5

/**
 * Categorized, multi-select (up to [MAX_MOODS]) mood picker shared by both
 * the standalone Mood tracker and Cycle. Shows just the 5 category names up
 * front; tapping one expands it in place to reveal its moods (accordion
 * style, single screen) — selections persist across categories as you
 * expand/collapse different ones. Tap DONE to confirm.
 */
class MoodMultiPickerScreen(
    sealedActivity: SealedLightActivity,
    private val currentSelection: List<Mood>,
) : SimpleLightScreen<List<Mood>>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        var selected by remember { mutableStateOf(currentSelection.toSet()) }
        var expandedCategories by remember { mutableStateOf(setOf<MoodCategory>()) }

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack(null) },
                    ),
                    center = LightTopBarCenter.Text("Mood (${selected.size}/$MAX_MOODS)"),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    MoodCategory.entries.forEach { category ->
                        val isExpanded = category in expandedCategories
                        val selectedInCategory = Mood.entries.count { it.category == category && it in selected }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedCategories = if (isExpanded) {
                                        expandedCategories - category
                                    } else {
                                        expandedCategories + category
                                    }
                                }
                                .padding(vertical = 0.75f.gridUnitsAsDp()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LightText(
                                text = if (selectedInCategory > 0) {
                                    "${category.label} ($selectedInCategory)"
                                } else {
                                    category.label
                                },
                                variant = LightTextVariant.Subheading,
                                modifier = Modifier.weight(1f),
                            )
                            LightIcon(icon = if (isExpanded) LightIcons.UP else LightIcons.DOWN)
                        }

                        if (isExpanded) {
                            Mood.entries.filter { it.category == category }.forEach { option ->
                                val isSelected = option in selected
                                val canToggle = isSelected || selected.size < MAX_MOODS

                                LightText(
                                    text = option.label,
                                    variant = LightTextVariant.Copy,
                                    lighten = isSelected,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = canToggle) {
                                            selected = if (isSelected) selected - option else selected + option
                                        }
                                        .padding(
                                            start = 1f.gridUnitsAsDp(),
                                            top = 0.6f.gridUnitsAsDp(),
                                            bottom = 0.6f.gridUnitsAsDp(),
                                        ),
                                )
                            }
                        }
                    }
                }

                LightBottomBar(
                    items = listOf(
                        LightBarButton.Text(
                            text = "DONE",
                            onClick = { goBack(selected.toList()) },
                        ),
                    ),
                )
            }
        }
    }
}

