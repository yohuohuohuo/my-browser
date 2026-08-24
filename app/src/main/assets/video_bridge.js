(function() {
    if (window.__videoBridge) return;

    var activeVideo = null;
    var callbacks = { onActive: null, onInactive: null, onBounds: null };
    var pollInterval = null;
    var observer = null;
    var presenceCheckTimer = null;
    var boundVideos = typeof WeakSet === 'function' ? new WeakSet() : null;
    var lastBounds = null;

    /** 判断元素是否在视口内可见 */
    function isInViewport(el) {
        var rect = el.getBoundingClientRect();
        var viewportHeight = window.innerHeight || document.documentElement.clientHeight;
        var viewportWidth = window.innerWidth || document.documentElement.clientWidth;
        return rect.width > 0 && rect.height > 0 &&
               rect.bottom > 0 && rect.top < viewportHeight &&
               rect.right > 0 && rect.left < viewportWidth;
    }

    function findActiveVideo() {
        var videos = document.querySelectorAll('video');
        var largestVisible = null;
        var largestVisibleArea = 0;
        var largestPlaying = null;
        var largestPlayingArea = 0;
        videos.forEach(function(v) {
            if (!isInViewport(v)) return;
            var rect = v.getBoundingClientRect();
            var area = rect.width * rect.height;
            if (area > largestVisibleArea) {
                largestVisibleArea = area;
                largestVisible = v;
            }
            if (!v.paused && v.readyState >= 2 && area > largestPlayingArea) {
                largestPlayingArea = area;
                largestPlaying = v;
            }
        });
        // 手势应优先作用于正在播放的视频；没有正在播放的视频时，
        // 再回退到视口内面积最大的 video（用于 play/toggle 等操作）。
        return largestPlaying || largestVisible;
    }

    function getVideoBounds(video) {
        var rect = video.getBoundingClientRect();
        if (!(rect.width > 0) || !(rect.height > 0)) return null;
        return {
            left: rect.left,
            top: rect.top,
            width: rect.width,
            height: rect.height
        };
    }

    function notifyVideoBounds(bounds) {
        if (!callbacks.onBounds || !bounds) return;
        if (lastBounds &&
            Math.abs(lastBounds.left - bounds.left) < 0.5 &&
            Math.abs(lastBounds.top - bounds.top) < 0.5 &&
            Math.abs(lastBounds.width - bounds.width) < 0.5 &&
            Math.abs(lastBounds.height - bounds.height) < 0.5) {
            return;
        }
        lastBounds = bounds;
        callbacks.onBounds(bounds.left, bounds.top, bounds.width, bounds.height);
    }

    function checkActive() {
        var v = findActiveVideo();
        if (v && !v.paused && v.readyState >= 2) {
            if (activeVideo !== v) {
                activeVideo = v;
                lastBounds = null;
                if (callbacks.onActive) callbacks.onActive();
            }
            notifyVideoBounds(getVideoBounds(v));
        } else if (activeVideo && (v !== activeVideo || (v && v.paused))) {
            activeVideo = null;
            lastBounds = null;
            if (callbacks.onInactive) callbacks.onInactive();
        }
    }

    function startPolling() {
        if (pollInterval) return;
        pollInterval = setInterval(checkActive, 1000);
    }

    function stopPolling() {
        if (pollInterval) {
            clearInterval(pollInterval);
            pollInterval = null;
        }
    }

    /** 仅在页面存在 video 元素时才启动轮询，避免无视频页面空转耗电 */
    function updateVideoPresence() {
        var videos = document.querySelectorAll('video');
        videos.forEach(bindVideoEvents);
        if (videos.length > 0) {
            startPolling();
            checkActive();
        } else {
            stopPolling();
            if (activeVideo) {
                activeVideo = null;
                lastBounds = null;
                if (callbacks.onInactive) callbacks.onInactive();
            }
        }
    }

    function schedulePresenceCheck() {
        if (presenceCheckTimer !== null) return;
        presenceCheckTimer = setTimeout(function() {
            presenceCheckTimer = null;
            updateVideoPresence();
        }, 100);
    }

    function bindVideoEvents(video) {
        if (boundVideos && boundVideos.has(video)) return;
        if (boundVideos) boundVideos.add(video);
        video.addEventListener('play', schedulePresenceCheck);
        video.addEventListener('pause', schedulePresenceCheck);
        video.addEventListener('loadedmetadata', schedulePresenceCheck);
        video.addEventListener('emptied', schedulePresenceCheck);
        video.addEventListener('ended', schedulePresenceCheck);
    }

    function startObserver() {
        if (observer) return;
        observer = new MutationObserver(schedulePresenceCheck);
        if (document.body) {
            observer.observe(document.body, { childList: true, subtree: true });
            updateVideoPresence();
        } else {
            document.addEventListener('DOMContentLoaded', function once() {
                document.removeEventListener('DOMContentLoaded', once);
                startObserver();
            });
        }
    }

    window.__videoBridge = {
        play: function() {
            var v = findActiveVideo();
            if (v) v.play();
        },
        pause: function() {
            var v = findActiveVideo();
            if (v) v.pause();
        },
        togglePlay: function() {
            var v = findActiveVideo();
            if (v) {
                if (v.paused) v.play(); else v.pause();
            }
        },
        seekBy: function(seconds) {
            var v = findActiveVideo();
            if (v && !isNaN(v.duration)) {
                v.currentTime = Math.max(0, Math.min(v.duration, v.currentTime + seconds));
            }
        },
        setRate: function(rate) {
            var v = findActiveVideo();
            if (v) v.playbackRate = rate;
        },
        getCurrentTime: function() {
            var v = findActiveVideo();
            return v ? v.currentTime : 0;
        },
        getDuration: function() {
            var v = findActiveVideo();
            return v && !isNaN(v.duration) ? v.duration : 0;
        },
        setCallbacks: function(onActive, onInactive, onBounds) {
            callbacks.onActive = onActive;
            callbacks.onInactive = onInactive;
            callbacks.onBounds = onBounds;
            schedulePresenceCheck();
        }
    };

    startObserver();
    window.addEventListener('scroll', schedulePresenceCheck, true);
    window.addEventListener('resize', schedulePresenceCheck);
    document.addEventListener('fullscreenchange', checkActive);
    document.addEventListener('webkitfullscreenchange', checkActive);
})();
