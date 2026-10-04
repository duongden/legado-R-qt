package io.legado.app.ui.browser

import android.webkit.WebView
import io.legado.app.utils.TranslateUtils
import io.legado.app.utils.UiTranslation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

/** Translate rendered text, without changing source HTML, form values, scripts or QR images. */
internal class WebViewUiTranslation(
    private val webView: WebView,
    private val scope: CoroutineScope
) {
    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        job = scope.launch {
            evaluate(RESTORE)
            if (!UiTranslation.isEnabled()) return@launch
            while (isActive) {
                val encoded = evaluate(COLLECT)
                val payload = (JSONTokener(encoded).nextValue() as? String)?.let(::JSONObject)
                if (payload != null) {
                    val items = payload.getJSONArray("items")
                    val translated = JSONArray()
                    for (index in 0 until items.length()) {
                        val item = items.getJSONObject(index)
                        translated.put(JSONObject().put("id", item.getInt("id"))
                            .put("raw", item.getString("raw"))
                            .put("display", UiTranslation.translateMessage(item.getString("raw"))))
                    }
                    if (UiTranslation.isEnabled() && translated.length() > 0) {
                        evaluate("""
                            (function() {
                                var state = window.__legadoViDisplay;
                                if (!state || state.token !== ${JSONObject.quote(payload.getString("token"))}) return;
                                var items = $translated;
                                items.forEach(function(item) {
                                    var entry = state.nodes[item.id];
                                    if (entry && entry.node.isConnected && entry.node.nodeValue === item.raw) {
                                        entry.display = item.display;
                                        entry.node.nodeValue = item.display;
                                    }
                                });
                            })();
                        """.trimIndent())
                    }
                }
                delay(1250)
            }
        }
    }

    fun cancel() { job?.cancel(); job = null }

    private suspend fun evaluate(script: String): String = suspendCancellableCoroutine { continuation ->
        webView.evaluateJavascript(script) { result ->
            if (continuation.isActive) continuation.resume(result ?: "null")
        }
    }

    companion object {
        private val RESTORE = """
            (function() {
                var state = window.__legadoViDisplay;
                if (!state) return;
                Object.keys(state.nodes).forEach(function(id) {
                    var entry = state.nodes[id];
                    if (entry.node.isConnected && entry.node.nodeValue === entry.display) {
                        entry.node.nodeValue = entry.raw;
                    }
                });
                delete window.__legadoViDisplay;
            })();
        """.trimIndent()

        internal val COLLECT = """
            (function() {
                if (!document.body) return JSON.stringify({token: '', items: []});
                var state = window.__legadoViDisplay;
                if (!state) state = window.__legadoViDisplay = {
                    token: String(Date.now()) + ':' + String(Math.random()),
                    nodes: {}, ids: new WeakMap(), next: 0
                };
                Object.keys(state.nodes).forEach(function(id) {
                    if (!state.nodes[id].node.isConnected) delete state.nodes[id];
                });
                var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
                var node, items = [];
                while ((node = walker.nextNode()) && items.length < 80) {
                    var parent = node.parentElement;
                    if (!parent || parent.closest('script,style,noscript,textarea,input,select,option,code,pre,[contenteditable],[translate="no"]') || !parent.getClientRects().length) continue;
                    var raw = node.nodeValue;
                    if (!/[\u3400-\u9fff]/.test(raw) || raw.length > 4000) continue;
                    var id = state.ids.get(node), entry = state.nodes[id];
                    if (entry && raw === entry.display) continue;
                    if (!entry || entry.raw !== raw) {
                        id = ++state.next;
                        state.ids.set(node, id);
                        entry = state.nodes[id] = {node: node, raw: raw, display: null};
                    }
                    items.push({id: id, raw: raw});
                }
                return JSON.stringify({token: state.token, items: items});
            })();
        """.trimIndent()
    }
}
