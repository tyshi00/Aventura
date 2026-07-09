package com.thelightphone.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import java.util.UUID

/**
 * Free-text entry screen (letters, not numbers) — used for Mood's notes
 * field. Enforces [maxLength] by truncating on submit.
 */
class TextEditorScreen(
    sealedActivity: SealedLightActivity,
    private val title: String,
    private val initialValue: String,
    private val maxLength: Int = 250,
) : SimpleLightScreen<String>(sealedActivity) {

    // See NumberEditorScreen for why this needs to be unique per instance —
    // same reasoning applies here.
    private val editorInstanceKey = UUID.randomUUID().toString()

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val textState = rememberTextFieldState(initialValue)
        val keyboardOptionsFlow = rememberKeyboardOptions()

        LightTheme(colors = themeColors) {
            LightTextInputEditor(
                title = title,
                state = textState,
                keyboardOptionsFlow = keyboardOptionsFlow,
                onSubmit = { result -> goBack(result.toString().trim().take(maxLength)) },
                onBack = { goBack(null) },
                submitLabel = "DONE",
                editorKey = editorInstanceKey,
                showUnderline = false,
                // Default (LowerCaseLayout) is exactly what we want here —
                // no need to override, unlike NumberEditorScreen.
                modifier = Modifier.background(LightThemeTokens.colors.background),
            )
        }
    }
}
