package com.comst19.dambom.feature.web.webview

import android.webkit.WebView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.comst19.dambom.core.domain.model.MediaResourceRequest
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
        val request = parseWebMediaMessage(message.data, webView.url, isMainFrame) ?: return@addWebMessageListener
        onMediaRequest(tabId, generation.get(), request)
    }
    WebViewCompat.addDocumentStartJavaScript(view, WEB_MEDIA_OBSERVER_SCRIPT, setOf("*"))
}

internal const val WEB_MEDIA_OBSERVER_SCRIPT = """
(() => {
  if (window.__dambomMediaObserver || !window.dambomMedia) return;
  window.__dambomMediaObserver = true;
  const sent = new Map();
  const httpUrl = (value) => {
    try {
      const url = new URL(value, document.baseURI);
      return /^https?:$/.test(url.protocol) && !url.username && !url.password ? url.href : '';
    } catch (_) { return ''; }
  };
  const report = (value, poster = '', title = '') => {
    try {
      const url = httpUrl(value);
      const thumbnail = poster ? httpUrl(poster) : '';
      const label = title.trim().slice(0, 512);
      const metadata = JSON.stringify([thumbnail, label]);
      if (!url || (sent.has(url) && ((!thumbnail && !label) || sent.get(url) === metadata))) return;
      if (!sent.has(url) && sent.size >= 128) return;
      sent.set(url, metadata);
      window.dambomMedia.postMessage(JSON.stringify({url: url, poster: thumbnail, title: label, pageUrl: location.href}));
    } catch (_) {}
  };
  const inspect = (element) => {
    if (element instanceof HTMLMediaElement) {
      const poster = element instanceof HTMLVideoElement ? element.poster : '';
      const title = [element.getAttribute('title'), element.getAttribute('aria-label')].find(value => value && value.trim()) || '';
      if (element.currentSrc) report(element.currentSrc, poster, title);
      if (element.getAttribute('src')) report(element.src, poster, title);
      element.querySelectorAll('source[src]').forEach((source) => report(source.src, poster, title));
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
      mutation.addedNodes.forEach((node) => { if (node.nodeType === 1) inspect(node.closest('video,audio') || node); });
    }
  })).observe(document, {childList: true, subtree: true, attributes: true, attributeFilter: ['src', 'poster', 'title', 'aria-label']});
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
