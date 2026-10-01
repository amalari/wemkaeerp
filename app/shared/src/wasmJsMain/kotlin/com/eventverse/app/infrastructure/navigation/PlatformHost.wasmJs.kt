package com.eventverse.app.infrastructure.navigation

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("() => { try { return window.location.host || ''; } catch (e) { return ''; } }")
private external fun jsCurrentHost(): String

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(name) => { try { return new URLSearchParams(window.location.search).get(name) || ''; } catch (e) { return ''; } }")
private external fun jsQueryParameter(name: String): String

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(url) => { try { window.location.assign(url); } catch (e) {} }")
private external fun jsOpenUrl(url: String)

actual object PlatformHost {
    actual fun currentHost(): String? = jsCurrentHost().takeIf { it.isNotBlank() }
    actual fun queryParameter(name: String): String? = jsQueryParameter(name).takeIf { it.isNotBlank() }
    actual fun openUrl(url: String) = jsOpenUrl(url)
}
