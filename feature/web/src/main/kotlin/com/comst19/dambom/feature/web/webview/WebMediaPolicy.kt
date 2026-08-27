package com.comst19.dambom.feature.web.webview

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

internal fun shouldBlockWebVideo(
    url: String,
    mediaGuardInstalled: Boolean,
): Boolean =
    !mediaGuardInstalled &&
        WEB_VIDEO_EXTENSIONS.any { extension -> url.substringBefore('?').endsWith(extension, ignoreCase = true) }

internal fun blockedVideoResponse() =
    WebResourceResponse(
        "video/mp4",
        null,
        204,
        "No Content",
        emptyMap(),
        ByteArrayInputStream(ByteArray(0)),
    )

private val WEB_VIDEO_EXTENSIONS = setOf(".mp4", ".webm", ".mov", ".m4v")

internal const val WEB_MEDIA_GUARD_SCRIPT =
    """
    (() => {
      if (window.__dambomMediaGuard) return;
      window.__dambomMediaGuard = true;

      const originalPlay = HTMLMediaElement.prototype.play;
      const userStartedVideos = new WeakSet();

      document.addEventListener('pointerdown', (event) => {
        const video = event.composedPath().find((node) => node instanceof HTMLVideoElement);
        if (video) userStartedVideos.add(video);
      }, true);

      HTMLMediaElement.prototype.play = function() {
        this.autoplay = false;
        this.removeAttribute('autoplay');
        if (!userStartedVideos.delete(this)) {
          this.pause();
          this.preload = 'none';
          return Promise.resolve();
        }
        document.querySelectorAll('video').forEach((other) => {
          if (other !== this) {
            other.pause();
            other.preload = 'none';
          }
        });
        this.preload = 'metadata';
        return originalPlay.call(this);
      };

      const visibility = new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
          const video = entry.target;
          if (!entry.isIntersecting) {
            video.pause();
            video.preload = 'none';
          }
        });
      }, { rootMargin: '320px 0px' });

      const prepare = (video) => {
        if (video.dataset.dambomGuarded) return;
        video.dataset.dambomGuarded = 'true';
        video.autoplay = false;
        video.removeAttribute('autoplay');
        video.preload = 'none';
        visibility.observe(video);
      };

      const scan = (root) => {
        if (root instanceof HTMLVideoElement) prepare(root);
        if (root.querySelectorAll) root.querySelectorAll('video').forEach(prepare);
      };

      document.addEventListener('DOMContentLoaded', () => scan(document), { once: true });
      new MutationObserver((mutations) => {
        mutations.forEach((mutation) => mutation.addedNodes.forEach(scan));
      }).observe(document, { childList: true, subtree: true });
    })();
    """
