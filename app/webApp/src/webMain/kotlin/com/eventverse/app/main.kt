package com.eventverse.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.eventverse.app.presentation.auth.GoogleAuthBridge

@OptIn(ExperimentalComposeUiApi::class, kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("() => { if (window.openGoogleLoginPopup) { window.openGoogleLoginPopup(); } }")
external fun openGooglePopupJs(): Unit

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(callback) => { window.onGoogleAuthCallback = (email, name) => callback(email, name); }")
external fun registerGoogleCallbackJs(callback: (String, String) -> Unit): Unit

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("() => { if (window.hideAppLoader) { window.hideAppLoader(); } }")
external fun hideAppLoaderJs(): Unit

@OptIn(ExperimentalComposeUiApi::class, kotlin.js.ExperimentalWasmJsInterop::class)
fun main() {
    GoogleAuthBridge.onSignInTrigger = {
        openGooglePopupJs()
    }

    registerGoogleCallbackJs { email, name ->
        GoogleAuthBridge.onAuthenticated?.invoke(email, name)
    }

    ComposeViewport {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            hideAppLoaderJs()
        }
        App()
    }
}