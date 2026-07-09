package com.thelightphone.sdk

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.File

private class BackStackEntry<T>(
    val screen: SimpleLightScreen<T>,
    val callback: ((T) -> Unit)? = null,
) {
    // SaveableStateProvider requires a Bundle-compatible key (String, Int,
    // Parcelable, etc.) — the BackStackEntry object itself isn't one, so it
    // can't be used directly as the key without crashing on first render.
    val id: String = "screen-${nextId++}"

    fun deliverResult() {
        val result = screen.result ?: return
        callback?.invoke(result)
    }

    companion object {
        private var nextId = 0L
    }
}

class LightActivity internal constructor() : ComponentActivity() {

    private val backStack = mutableListOf<BackStackEntry<*>>()
    private val currentScreen = mutableStateOf<BackStackEntry<*>?>(null)

    // Set once from within setContent (rememberSaveableStateHolder needs a
    // composition), then read from goBack() to clean up a permanently-popped
    // screen's cached state. See setContent below for why this exists.
    private var saveableStateHolderRef: SaveableStateHolder? = null
    private var contentReady = false
    private val createdAt = android.os.SystemClock.elapsedRealtime()

    internal fun <T> navigateTo(screen: SimpleLightScreen<T>, resultCallback: ((T) -> Unit)? = null) {
        currentScreen.value?.screen?.notifyWillHide()
        val entry = BackStackEntry(screen, resultCallback)
        backStack.add(entry)
        screen.notifyWillShow()
        currentScreen.value = entry
    }

    internal fun goBack() {
        val current = currentScreen.value ?: return
        val popped = current.screen
        popped.notifyWillHide()
        popped.destroy()
        backStack.removeAt(backStack.lastIndex)
        // This entry is gone for good — drop its cached UI state rather than
        // holding onto it forever (SaveableStateProvider otherwise keeps it
        // around indefinitely to support the "navigate away and back" case).
        saveableStateHolderRef?.removeState(current.id)
        if (backStack.isEmpty()) {
            finish()
            return
        }
        val previous = backStack.last()
        // Deliver the result (which updates the returning screen's ViewModel
        // state) BEFORE switching the visible screen. Otherwise the screen
        // switch triggers an immediate recomposition using the OLD state —
        // the result callback's update flows through a StateFlow with its
        // own async collection step, so it can arrive a beat too late for
        // that redraw, making a just-entered value appear to vanish until
        // the field is tapped and re-entered a second time.
        current.deliverResult()
        previous.screen.notifyWillShow()
        currentScreen.value = previous
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition {
            !contentReady || android.os.SystemClock.elapsedRealtime() - createdAt < 1000
        }
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        val factory = LightSdkRegistry.initialScreenFactory
            ?: throw IllegalStateException("No class annotated with @InitialScreen found")

        val initial = BackStackEntry(factory(SealedLightActivity(this)))

        backStack.add(initial)
        currentScreen.value = initial

        setContent {
            androidx.compose.runtime.LaunchedEffect(Unit) { contentReady = true }
            val saveableStateHolder = rememberSaveableStateHolder()
            saveableStateHolderRef = saveableStateHolder
            val entry = currentScreen.value
            if (entry != null) {
                val screen = entry.screen
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        // SaveableStateProvider (not bare key()) so a screen's UI
                        // state — e.g. scroll position — survives navigating away
                        // and back. key() only caches a single keyed subtree at a
                        // time: navigating to a sub-screen discards the parent's
                        // composition outright, so returning to it (even though
                        // it's the same BackStackEntry) starts fresh, resetting
                        // scroll position to the top. SaveableStateProvider keeps
                        // each entry's state cached across such round trips, the
                        // same mechanism Navigation-Compose's NavHost uses.
                        saveableStateHolder.SaveableStateProvider(entry.id) {
                            val content: @Composable () -> Unit = { screen.Content() }
                            if (screen is ViewModelStoreOwner) {
                                CompositionLocalProvider(
                                    LocalViewModelStoreOwner provides screen,
                                    content = content,
                                )
                            } else {
                                content()
                            }
                        }
                    }
                }
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    goBack()
                }
            }
        )
    }

    override fun onPause() {
        super.onPause()
        currentScreen.value?.screen?.notifyAppPause()
    }

    override fun onResume() {
        super.onResume()
        currentScreen.value?.screen?.notifyWillShow()
    }
}

class SealedLightContext(internal val androidContext: Context) {
    val dataStore: DataStore<Preferences> by lazy{ androidContext.dataStore }
    val filesDir: File by lazy{ androidContext.filesDir }
    val fileShare: LightFileShare by lazy { LightFileShare(androidContext) }
}
/**
 * Wrapper class to pass around an instance of LightActivity without exposing it to
 * user code. Sorry! :)
 */
class SealedLightActivity(internal val activity: LightActivity)

internal val Context.dataStore by preferencesDataStore(
    name = "DEFAULT_DATASTORE"
)