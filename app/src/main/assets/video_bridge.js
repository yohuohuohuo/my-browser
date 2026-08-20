(function() {
    if (window.__videoBridge) return;

    var activeVideo = null;
    var callbacks = { onActive: null, onInactive: null };

    function findActiveVideo() {
        var videos = document.querySelectorAll('video');
        var best = null;
        var bestArea = 0;
        videos.forEach(function(v) {
            var rect = v.getBoundingClientRect();
            var area = rect.width * rect.height;
            if (area > bestArea && rect.width > 0 && rect.height > 0) {
                bestArea = area;
                best = v;
            }
        });
        return best;
    }

    function checkActive() {
        var v = findActiveVideo();
        if (v && !v.paused && v.readyState >= 2) {
            if (activeVideo !== v) {
                activeVideo = v;
                if (callbacks.onActive) callbacks.onActive();
            }
        } else if (activeVideo && (v !== activeVideo || v.paused)) {
            activeVideo = null;
            if (callbacks.onInactive) callbacks.onInactive();
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
        setCallbacks: function(onActive, onInactive) {
            callbacks.onActive = onActive;
            callbacks.onInactive = onInactive;
        }
    };

    // Monitor video activity
    setInterval(checkActive, 1000);
    document.addEventListener('fullscreenchange', checkActive);
    document.addEventListener('webkitfullscreenchange', checkActive);
})();
