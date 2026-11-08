const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

function makeVideo({ paused, width = 320, height = 180 }) {
    const events = {};
    return {
        tagName: 'VIDEO',
        paused,
        readyState: 2,
        duration: 120,
        currentTime: 0,
        videoWidth: width,
        videoHeight: height,
        playbackRate: 1,
        getBoundingClientRect: () => ({ left: 0, top: 0, right: width, bottom: height, width, height }),
        addEventListener(name, listener) { events[name] = listener; },
        play() { this.paused = false; events.play?.(); },
        pause() { this.paused = true; events.pause?.(); }
    };
}

function makeHarness(videos) {
    const timers = [];
    const documentEvents = {};
    const document = {
        body: {},
        documentElement: { clientWidth: 320, clientHeight: 180 },
        querySelectorAll: () => videos,
        fullscreenElement: null,
        addEventListener(name, listener) { documentEvents[name] = listener; }
    };
    const window = {
        innerWidth: 320,
        innerHeight: 180,
        addEventListener() {}
    };
    const context = {
        window, document,
        WeakSet, MutationObserver: class { observe() {} },
        setInterval: () => 1,
        clearInterval() {},
        setTimeout(fn) { timers.push(fn); return timers.length; }
    };
    const script = fs.readFileSync(path.join(__dirname, '../../main/assets/video_bridge.js'), 'utf8');
    vm.runInNewContext(script, context);
    let active = 0;
    let inactive = 0;
    window.__videoBridge.setCallbacks(() => active++, () => inactive++, () => {});
    const flush = () => { while (timers.length) timers.shift()(); };
    flush();
    return {
        bridge: window.__videoBridge, document, flush,
        fullscreenChanged: () => documentEvents.fullscreenchange(),
        getActive: () => active, getInactive: () => inactive
    };
}

test('a video paused after playing remains active and can be resumed', () => {
    const video = makeVideo({ paused: false });
    const harness = makeHarness([video]);
    assert.equal(harness.getActive(), 1);

    video.pause();
    harness.flush();
    assert.equal(harness.getInactive(), 0);
    harness.bridge.togglePlay();
    harness.flush();
    assert.equal(video.paused, false);
    harness.bridge.togglePlay();
    harness.flush();
    assert.equal(video.paused, true);
    assert.equal(harness.getInactive(), 0);
});

test('an unstarted paused video does not swallow its site startup click', () => {
    const video = makeVideo({ paused: true });
    const harness = makeHarness([video]);
    assert.equal(harness.getActive(), 0);

    video.play();
    harness.flush();
    assert.equal(harness.getActive(), 1);
});

test('pausing a smaller active video does not switch controls to a larger paused video', () => {
    const active = makeVideo({ paused: false, width: 100, height: 100 });
    const other = makeVideo({ paused: true, width: 320, height: 180 });
    const harness = makeHarness([active, other]);
    active.pause();
    harness.flush();
    harness.bridge.togglePlay();
    harness.flush();

    assert.equal(active.paused, false);
    assert.equal(other.paused, true);
    assert.equal(harness.getInactive(), 0);
});

test('all controls and readings target fullscreen video after selecting an inline video', () => {
    const inline = makeVideo({ paused: false, width: 100, height: 100 });
    const fullscreen = makeVideo({ paused: false });
    inline.currentTime = 20;
    fullscreen.currentTime = 40;
    fullscreen.duration = 200;
    const videos = [inline];
    const harness = makeHarness(videos);
    videos.push(fullscreen);
    const bridge = harness.bridge;
    harness.document.fullscreenElement = fullscreen;

    assert.equal(bridge.getActiveVideoDimensions(), '320,180');
    assert.equal(bridge.getCurrentTime(), 40);
    assert.equal(bridge.getDuration(), 200);
    bridge.seekBy(10);
    bridge.setRate(3);
    bridge.togglePlay();
    assert.equal(fullscreen.currentTime, 50);
    assert.equal(fullscreen.playbackRate, 3);
    assert.equal(fullscreen.paused, true);
    bridge.play();
    assert.equal(fullscreen.paused, false);
    bridge.pause();
    assert.equal(fullscreen.paused, true);
    assert.equal(inline.currentTime, 20);
    assert.equal(inline.playbackRate, 1);
    assert.equal(inline.paused, false);
});

test('fullscreen container selects its video and keeps the paused selection after exiting', () => {
    const inline = makeVideo({ paused: false, width: 100, height: 100 });
    const fullscreen = makeVideo({ paused: false });
    const videos = [inline];
    const harness = makeHarness(videos);
    videos.push(fullscreen);
    harness.document.fullscreenElement = { querySelectorAll: () => [fullscreen] };
    harness.fullscreenChanged();
    harness.bridge.togglePlay();
    harness.flush();
    assert.equal(fullscreen.paused, true);
    assert.equal(inline.paused, false);

    harness.document.fullscreenElement = null;
    harness.fullscreenChanged();
    harness.bridge.togglePlay();
    assert.equal(fullscreen.paused, false);
    assert.equal(inline.paused, false);
});
