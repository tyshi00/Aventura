package com.thelightphone.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Calendar-grid date picker for backdating entries (replaces the earlier
 * flat "Today / Yesterday / ..." list). Only today and past days are
 * selectable — you can't log an entry for a day that hasn't happened yet,
 * so future months/days are disabled rather than removed, to keep the
 * month-navigation affordance consistent.
 */
class DatePickerScreen(
    sealedActivity: SealedLightActivity,
    private val currentDate: String,
) : SimpleLightScreen<String>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val today = remember { LocalDate.now() }
        val selected = remember { LocalDate.parse(currentDate, DateTimeFormatter.ISO_LOCAL_DATE) }
        var displayedMonth by remember { mutableStateOf(YearMonth.from(selected)) }

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
                    center = LightTopBarCenter.Text("Date"),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    val canGoForward = displayedMonth < YearMonth.from(today)

                    // Month header with prev/next navigation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .clickable { displayedMonth = displayedMonth.minusMonths(1) }
                                .padding(0.5f.gridUnitsAsDp()),
                        ) {
                            LightIcon(icon = LightIcons.BACK, size = 1.3f)
                        }

                        LightText(
                            text = "${displayedMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${displayedMonth.year}",
                            variant = LightTextVariant.Copy,
                            align = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )

                        Box(
                            modifier = Modifier
                                .clickable(enabled = canGoForward) {
                                    displayedMonth = displayedMonth.plusMonths(1)
                                }
                                .padding(0.5f.gridUnitsAsDp()),
                        ) {
                            LightIcon(
                                icon = LightIcons.ARROW_RIGHT,
                                size = 1.3f,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))

                    // Weekday header, locale-aware
                    val weekFields = remember { WeekFields.of(Locale.getDefault()) }
                    val firstDayOfWeek = weekFields.firstDayOfWeek
                    val weekDayLabels = remember(firstDayOfWeek) {
                        (0 until 7).map { offset ->
                            firstDayOfWeek.plus(offset.toLong())
                                .getDisplayName(TextStyle.NARROW, Locale.getDefault())
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        weekDayLabels.forEach { label ->
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                LightText(
                                    text = label,
                                    variant = LightTextVariant.Fine,
                                    lighten = true,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))

                    // Calendar grid: leading blanks pad out to the correct weekday
                    val firstOfMonth = displayedMonth.atDay(1)
                    val daysInMonth = displayedMonth.lengthOfMonth()
                    val leadingBlanks = ((firstOfMonth.dayOfWeek.value - firstDayOfWeek.value) + 7) % 7
                    val totalCells = leadingBlanks + daysInMonth
                    val rowCount = (totalCells + 6) / 7

                    for (row in 0 until rowCount) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (col in 0 until 7) {
                                val cellIndex = row * 7 + col
                                val dayNumber = cellIndex - leadingBlanks + 1

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (dayNumber in 1..daysInMonth) {
                                        val date = displayedMonth.atDay(dayNumber)
                                        val isFuture = date.isAfter(today)
                                        val isSelected = date == selected
                                        val isToday = date == today

                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(0.2f.gridUnitsAsDp())
                                                .clip(CircleShape)
                                                .let {
                                                    if (isSelected) {
                                                        it.background(LightThemeTokens.colors.content)
                                                    } else {
                                                        it
                                                    }
                                                }
                                                .clickable(enabled = !isFuture) {
                                                    goBack(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            LightText(
                                                text = dayNumber.toString(),
                                                variant = LightTextVariant.Copy,
                                                lighten = isFuture,
                                                underline = isToday && !isSelected,
                                                color = if (isSelected) LightThemeTokens.colors.background else null,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
