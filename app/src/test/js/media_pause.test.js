const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const scriptPath = path.join(__dirname, '../../main/assets/media_pause.js');

test('pauses every audio and video in the page and accessible child frames', () => {
    const makeMedia = () => ({ paused: false, pause() { this.paused = true; } });
    const topVideo = makeMedia();
    const topAudio = makeMedia();
    const childVideo = makeMedia();
    const childDocument = {
        querySelectorAll(selector) {
            return selector === 'audio,video' ? [childVideo] : [];
        }
    };
    const inaccessibleFrame = {
        get contentDocument() { throw new Error('cross-origin frame'); }
    };
    const document = {
        querySelectorAll(selector) {
            return selector === 'audio,video'
                ? [topVideo, topAudio]
                : [{ contentDocument: childDocument }, inaccessibleFrame];
        }
    };

    vm.runInNewContext(fs.readFileSync(scriptPath, 'utf8'), { document });

    assert.equal(topVideo.paused, true);
    assert.equal(topAudio.paused, true);
    assert.equal(childVideo.paused, true);
});
