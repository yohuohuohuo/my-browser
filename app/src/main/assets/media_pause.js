(function () {
    function pauseDocument(doc) {
        try {
            doc.querySelectorAll('audio,video').forEach(function (media) {
                try { media.pause(); } catch (_) { /* Continue with other media. */ }
            });
            doc.querySelectorAll('iframe,frame').forEach(function (frame) {
                try {
                    var child = frame.contentDocument || frame.contentWindow.document;
                    if (child) pauseDocument(child);
                } catch (_) { /* Cross-origin frames are not script-accessible. */ }
            });
        } catch (_) { /* A detached document must not abort the rest. */ }
    }
    pauseDocument(document);
})();
