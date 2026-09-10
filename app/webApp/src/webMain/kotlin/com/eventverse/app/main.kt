package com.eventverse.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.eventverse.app.presentation.auth.GoogleAuthBridge

@OptIn(ExperimentalComposeUiApi::class, kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("() => { if (window.openGoogleLoginPopup) { window.openGoogleLoginPopup(); } }")
external fun openGooglePopupJs(): Unit

/**
 * Receives the Google **ID token** (a signed JWT) from the browser.
 *
 * The callback used to hand over an email and a display name, which the app then trusted as
 * the signed-in identity. It now passes the token so the server can verify it against Google
 * and decide the identity itself.
 */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(callback) => { window.onGoogleAuthCallback = (idToken) => callback(idToken); }")
external fun registerGoogleCallbackJs(callback: (String) -> Unit): Unit

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("() => { if (window.hideAppLoader) { window.hideAppLoader(); } }")
external fun hideAppLoaderJs(): Unit

@OptIn(ExperimentalComposeUiApi::class, kotlin.js.ExperimentalWasmJsInterop::class)
fun main() {
    GoogleAuthBridge.onSignInTrigger = {
        openGooglePopupJs()
    }

    registerGoogleCallbackJs { idToken ->
        GoogleAuthBridge.onAuthenticated?.invoke(idToken)
    }

    ComposeViewport {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            hideAppLoaderJs()
        }
        App()
    }
}
