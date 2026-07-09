package com.thelightphone.tracker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp

/**
 * Stat display: label (Detail, 20sp) above value (Copy, 30sp).
 * Matches SDK conventions — Detail for secondary labels, Copy for body values.
 */
@Composable
fun StatRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LightText(
            text = label.uppercase(),
            variant = LightTextVariant.Detail,   // 20sp — secondary label
            lighten = true,
        )
        Spacer(modifier = Modifier.height(0.25f.gridUnitsAsDp()))
        LightText(
            text = value,
            variant = LightTextVariant.Copy,     // 30sp — standard body text per SDK examples
        )
    }
}
