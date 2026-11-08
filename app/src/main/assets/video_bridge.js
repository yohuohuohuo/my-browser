(function() {
    if (window.__videoBridge) return;

    var activeVideo = null;
    var callbacks = { onActive: null, onInactive: null, onBounds: null };
    var pollInterval = null;
    var observer = null;
    var presenceCheckTimer = null;
    var boundVideos = typeof WeakSet === 'function' ? new WeakSet() : null;
    var playedVideos = typeof WeakSet === 'function' ? new WeakSet() : [];
    var lastBounds = null;

    function markVideoPlayed(video) {
        if (typeof playedVideos.add === 'function') playedVideos.add(video);
        else if (playedVideos.indexOf(video) < 0) playedVideos.push(video);
    }

    function wasVideoPlayed(video) {
        return typeof playedVideos.has === 'function' ?
            playedVideos.has(video) : playedVideos.indexOf(video) >= 0;
    }

    function canHandleVideo(video) {
        return (!video.paused && video.readyState >= 2) ||
            wasVideoPlayed(video) ||
            (video.readyState >= 2 && video.currentTime > 0);
    }

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
        var fullscreenVideo = findFullscreenVideo();
        if (fullscreenVideo) {
            if (!fullscreenVideo.paused && fullscreenVideo.readyState >= 2) markVideoPlayed(fullscreenVideo);
            return fullscreenVideo;
        }
        var videos = document.querySelectorAll('video');
        if (activeVideo && Array.prototype.indexOf.call(videos, activeVideo) >= 0 &&
            isInViewport(activeVideo) && canHandleVideo(activeVideo)) {
            return activeVideo;
        }
        var largestVisible = null;
        var largestVisibleArea = 0;
        var largestPlaying = null;
        var largestPlayingArea = 0;
        videos.forEach(function(v) {
            if (!isInViewport(v)) return;
            if (!canHandleVideo(v)) return;
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
        // 保持已选视频的身份，避免暂停后切换到页面里的另一段视频。
        // 未启动的视频继续由网页处理首次点击；已播放过的视频可在暂停后恢复。
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

    function findFullscreenVideo() {
        var fullscreenElement = document.fullscreenElement || document.webkitFullscreenElement;
        if (!fullscreenElement) return null;
        if (fullscreenElement.tagName === 'VIDEO') return fullscreenElement;
        if (!fullscreenElement.querySelectorAll) return null;
        var videos = fullscreenElement.querySelectorAll('video');
        var largestVideo = null;
        var largestArea = 0;
        videos.forEach(function(v) {
            var rect = v.getBoundingClientRect();
            var area = rect.width * rect.height;
            if (area > largestArea) {
                largestArea = area;
                largestVideo = v;
            }
        });
        return largestVideo;
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
        if (v) {
            if (activeVideo !== v) {
                activeVideo = v;
                lastBounds = null;
                if (callbacks.onActive) callbacks.onActive();
            }
            notifyVideoBounds(getVideoBounds(v));
        } else if (activeVideo) {
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
        if (!video.paused && video.readyState >= 2) markVideoPlayed(video);
        video.addEventListener('play', function() {
            markVideoPlayed(video);
            schedulePresenceCheck();
        });
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
        getActiveVideoDimensions: function() {
            var v = findActiveVideo();
            if (!v) return "";
            var width = Number(v.videoWidth);
            var height = Number(v.videoHeight);
            if (!(width > 0) || !(height > 0)) {
                var rect = v.getBoundingClientRect();
                width = rect.width;
                height = rect.height;
            }
            if (!(width > 0) || !(height > 0)) return "";
            return width + "," + height;
        },
        setCallbacks: function(onActive, onInactive, onBounds) {
            callbacks.onActive = onActive;
            callbacks.onInactive = onInactive;
            callbacks.onBounds = onBounds;
            if (activeVideo) {
                if (callbacks.onActive) callbacks.onActive();
                lastBounds = null;
                notifyVideoBounds(getVideoBounds(activeVideo));
            }
            schedulePresenceCheck();
        }
    };

    startObserver();
    window.addEventListener('scroll', schedulePresenceCheck, true);
    window.addEventListener('resize', schedulePresenceCheck);
    document.addEventListener('fullscreenchange', checkActive);
    document.addEventListener('webkitfullscreenchange', checkActive);
})();
