package com.tyshi00.aventura

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIconConfiguration
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.designVerticalPxToDp
import com.thelightphone.sdk.ui.designVerticalPxToSp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

/**
 * Left edge of settings-style lists, in grid units. LightOS's own settings screens start their
 * rows 2.1 units in, with the text of icon rows starting at about 5.05 units.
 */
const val OPTION_START_UNITS = 2.1f

/**
 * The SDK's older icon names are backwards: TOGGLE_OFF draws the filled "on" knob and TOGGLE_ON
 * draws the hollow "off" ring (newer SDKs renamed them TOGGLE_STATE_ON / TOGGLE_STATE_OFF with the
 * same artwork). Keep this mapping in one place so nobody "fixes" it.
 */
fun toggleIcon(on: Boolean): LightIconConfiguration =
    if (on) LightIcons.TOGGLE_OFF else LightIcons.TOGGLE_ON

/**
 * A tappable settings row laid out like LightOS's own: 1.3 grid units above and below, a 38 point
 * title on a 1.2 line height, an optional smaller subtitle, and an optional icon on the left.
 */
@Composable
fun OptionRow(
    title: String,
    subtitle: String? = null,
    icon: LightIconConfiguration? = null,
    dimmed: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClickLabel = title, role = Role.Button, onClick = onClick)
            .padding(top = 1.3f.gridUnitsAsDp(), bottom = 1.3f.gridUnitsAsDp(), end = 1f.gridUnitsAsDp()),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .padding(start = 0.17f.gridUnitsAsDp())
                    .width(2.78f.gridUnitsAsDp())
                    .height((38f * 1.2f).designVerticalPxToDp()),
                contentAlignment = Alignment.CenterStart,
            ) {
                LightIcon(icon = icon, size = 2f, contentDescription = null)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            OptionText(text = title, size = 38f, lineHeight = 1.2f, dimmed = dimmed, maxLines = 2)
            if (!subtitle.isNullOrEmpty()) {
                OptionText(text = subtitle, size = 20f, lineHeight = 1.3f, dimmed = dimmed, maxLines = 1)
            }
        }
    }
}

@Composable
private fun OptionText(
    text: String,
    size: Float,
    lineHeight: Float,
    dimmed: Boolean,
    maxLines: Int,
) {
    val colors = LightThemeTokens.colors
    val style = LightThemeTokens.typography.copy.copy(
        fontSize = size.designVerticalPxToSp(),
        lineHeight = (size * lineHeight).designVerticalPxToSp(),
        letterSpacing = 0f.em,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )
    Text(
        text = text,
        color = if (dimmed) colors.contentSecondary else colors.content,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
