package com.eventverse.app.infrastructure.storage

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(key, value) => { try { window.localStorage.setItem(key, value); } catch (e) {} }")
private external fun jsSetItem(key: String, value: String)

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(key) => { try { return window.localStorage.getItem(key); } catch (e) { return null; } }")
private external fun jsGetItem(key: String): String?

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(key) => { try { window.localStorage.removeItem(key); } catch (e) {} }")
private external fun jsRemoveItem(key: String)

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("() => { try { window.localStorage.clear(); } catch (e) {} }")
private external fun jsClear()

actual object PlatformLocalStorage {
    actual fun setItem(key: String, value: String) {
        jsSetItem(key, value)
    }

    actual fun getItem(key: String): String? {
        return jsGetItem(key)
    }

    actual fun removeItem(key: String) {
        jsRemoveItem(key)
    }

    actual fun clear() {
        jsClear()
    }
}
