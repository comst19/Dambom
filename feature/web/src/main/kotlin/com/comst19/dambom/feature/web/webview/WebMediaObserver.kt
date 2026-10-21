package com.comst19.dambom.feature.web.webview

import android.webkit.WebView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.comst19.dambom.core.common.net.isHttpUrl
import com.comst19.dambom.core.domain.model.MediaResourceRequest
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

internal fun installWebMediaObserver(
    view: WebView,
    tabId: Long,
    generation: AtomicLong,
    onMediaRequest: (Long, Long, MediaResourceRequest) -> Unit,
) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
        !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    ) {
        return
    }
    WebViewCompat.addWebMessageListener(
        view,
        "dambomMedia",
        setOf("*"),
    ) { webView, message, sourceOrigin, isMainFrame, _ ->
        if (sourceOrigin.scheme !in setOf("http", "https")) return@addWebMessageListener
        if (message.type != WebMessageCompat.TYPE_STRING) return@addWebMessageListener
        val data = message.data?.takeIf { it.length <= MAX_MEDIA_MESSAGE_LENGTH } ?: return@addWebMessageListener
        val parsed = runCatching { JSONObject(data) }.getOrNull() ?: return@addWebMessageListener
        if (isMainFrame && parsed.optString("pageUrl").substringBefore('#') != webView.url?.substringBefore('#')) {
            return@addWebMessageListener
        }
        val url =
            parsed.optString("url").takeIf { it.length <= MAX_MEDIA_URL_LENGTH && it.isHttpUrl() }
                ?: return@addWebMessageListener
        onMediaRequest(tabId, generation.get(), MediaResourceRequest(url))
    }
    WebViewCompat.addDocumentStartJavaScript(view, WEB_MEDIA_OBSERVER_SCRIPT, setOf("*"))
}

private const val MAX_MEDIA_URL_LENGTH = 8_192
private const val MAX_MEDIA_MESSAGE_LENGTH = 16_384

internal const val WEB_MEDIA_OBSERVER_SCRIPT = """
(() => {
  if (window.__dambomMediaObserver || !window.dambomMedia) return;
  window.__dambomMediaObserver = true;
  const sent = new Set();
  const report = (value) => {
    try {
      const url = new URL(value, document.baseURI);
      if (!/^https?:$/.test(url.protocol) || sent.has(url.href) || sent.size >= 128) return;
      sent.add(url.href);
      window.dambomMedia.postMessage(JSON.stringify({url: url.href, pageUrl: location.href}));
    } catch (_) {}
  };
  const inspect = (element) => {
    if (element instanceof HTMLMediaElement) {
      if (element.currentSrc) report(element.currentSrc);
      if (element.getAttribute('src')) report(element.src);
      element.querySelectorAll('source[src]').forEach((source) => report(source.src));
    }
    if (element.querySelectorAll) element.querySelectorAll('video,audio').forEach(inspect);
  };
  document.addEventListener('loadedmetadata', (event) => inspect(event.target), true);
  document.addEventListener('play', (event) => inspect(event.target), true);
  document.addEventListener('DOMContentLoaded', () => inspect(document), {once: true});
  new MutationObserver((mutations) => mutations.forEach((mutation) => {
    if (mutation.type === 'attributes') {
      inspect(mutation.target.closest('video,audio') || mutation.target);
    } else {
      mutation.addedNodes.forEach((node) => { if (node.nodeType === 1) inspect(node); });
    }
  })).observe(document, {childList: true, subtree: true, attributes: true, attributeFilter: ['src']});
  const mediaType = (value) => /^(video\/|audio\/(x-)?mpegurl|application\/(vnd\.apple\.mpegurl|x-mpegurl|dash\+xml))/i.test(value || '');
  if (window.fetch) {
    const originalFetch = window.fetch;
    window.fetch = function(input, init) {
      const method = ((init && init.method) || (input && input.method) || 'GET').toUpperCase();
      return originalFetch.apply(this, arguments).then((response) => {
        if (method === 'GET' && mediaType(response.headers.get('Content-Type'))) report(response.url);
        return response;
      });
    };
  }
  const originalOpen = XMLHttpRequest.prototype.open;
  const xhrMethods = new WeakMap();
  XMLHttpRequest.prototype.open = function(method) {
    if (!xhrMethods.has(this)) {
      this.addEventListener('load', () => {
        if (xhrMethods.get(this) === 'GET' && mediaType(this.getResponseHeader('Content-Type'))) report(this.responseURL);
      });
    }
    xhrMethods.set(this, String(method).toUpperCase());
    return originalOpen.apply(this, arguments);
  };
})();
"""
