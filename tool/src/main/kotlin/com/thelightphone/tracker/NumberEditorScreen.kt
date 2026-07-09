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
import com.thelightphone.lp3Keyboard.ui.NumberLayout
import java.util.UUID

class NumberEditorScreen(
    sealedActivity: SealedLightActivity,
    private val title: String,
    private val initialValue: String,
    @Suppress("UNUSED_PARAMETER") isDecimal: Boolean = false,
) : SimpleLightScreen<String>(sealedActivity) {

    // Each screen instance needs its own keyboard ViewModel. The Activity's
    // ViewModelStore is shared across screen instances (NumberEditorScreen is
    // not a ViewModelStoreOwner), so keying off `title` alone would reuse a
    // stale keyboard instance still wired to a previous, discarded text state
    // — inputs would appear to only work the first time.
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
                onSubmit = { result -> goBack(result.toString().trim()) },
                onBack = { goBack(null) },
                submitLabel = "DONE",
                editorKey = editorInstanceKey,
                initialLayout = NumberLayout,
                modifier = Modifier.background(LightThemeTokens.colors.background),
            )
        }
    }
}
