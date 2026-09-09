package com.eventverse.app.infrastructure.navigation

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("() => { try { var h = window.location.hash || ''; if (h.startsWith('#/')) return h.substring(1); if (h.startsWith('#') && h.length > 1) return '/' + h.substring(1).replace(/^\\//, ''); var p = window.location.pathname || '/'; return p.length > 0 ? p : '/'; } catch(e) { return '/'; } }")
private external fun jsGetCurrentPath(): String

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(path) => { try { if (window.location.pathname !== path) { window.history.pushState(null, '', path); } } catch (e) {} }")
private external fun jsPushPath(path: String)

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(path) => { try { window.history.replaceState(null, '', path); } catch (e) {} }")
private external fun jsReplacePath(path: String)

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(callback) => { try { var notify = () => { var h = window.location.hash || ''; var p = window.location.pathname || '/'; if (h.startsWith('#/')) p = h.substring(1); else if (h.startsWith('#') && h.length > 1) p = '/' + h.substring(1).replace(/^\\//, ''); callback(p); }; window.addEventListener('popstate', notify); window.addEventListener('hashchange', notify); } catch (e) {} }")
private external fun jsListenToPathChanges(callback: (String) -> Unit)

actual object PlatformNavigation {
    actual fun getCurrentPath(): String {
        return jsGetCurrentPath()
    }

    actual fun pushPath(path: String) {
        jsPushPath(path)
    }

    actual fun replacePath(path: String) {
        jsReplacePath(path)
    }

    actual fun listenToPathChanges(onPathChanged: (String) -> Unit) {
        jsListenToPathChanges(onPathChanged)
    }
}
