import { test } from 'node:test';
import assert from 'node:assert/strict';
import { CatmullRomCurve3, Vector3 } from 'three';
import {
    frameDelta,
    openingPose,
    pointerPose,
    transitionPose,
    needsSceneFrame
} from '../../src/main/frontend/animation-clock.mjs';

test('a first frame older than its scheduling time leaves neural pulses at the start', () => {
    const elapsed = frameDelta(110, 112.5);
    const path = new CatmullRomCurve3([new Vector3(-2, 1, 1), new Vector3(-1, 1, 2), new Vector3(0, 0, 1)]);
    const pulse = path.getPoint((elapsed * 0.22) % 1);
    assert.deepEqual(pulse.toArray(), [-2, 1, 1]);
});

test('normal frames progress while a background-tab gap cannot create a large jump', () => {
    assert.equal(frameDelta(116, 100), 0.016);
    assert.equal(frameDelta(60100, 100), 0.05);
    assert.equal(frameDelta(100, 100), 0);
});

test('opening cannot begin until pointer alignment and the closed side turn finish', () => {
    assert.equal(openingPose(0).align, 0);
    assert.equal(openingPose(0.22).align, 1);
    for (let frame = 0; frame <= 42; frame++) assert.equal(openingPose(frame / 100).open, 0);
    assert.equal(openingPose(0.42).turn, 1.1);
    assert.ok(openingPose(0.6).open > 0);
    assert.equal(openingPose(1).open, 1);
    assert.equal(openingPose(1).turn, 0);
});

test('pointer following is relative to its area and bounded outside the edges', () => {
    const bounds = { left: 100, top: 200, width: 400, height: 300 };
    assert.deepEqual(pointerPose(300, 350, bounds), { rotation: 0, tilt: 0 });
    assert.deepEqual(pointerPose(500, 500, bounds), { rotation: 0.42, tilt: 0.18 });
    assert.deepEqual(pointerPose(-500, -500, bounds), { rotation: -0.42, tilt: -0.18 });
});

test('a mode click moves on its first frame without replaying the entrance pause', () => {
    assert.ok(transitionPose(0.016 / 0.85).open > 0);
    assert.equal(transitionPose(0.016 / 2.65, true).open, 0);
    assert.equal(transitionPose(1).open, 1);
    assert.equal(transitionPose(1).turn, 0);
});

test('an offscreen mode transition completes without pointer events then stops ambient rendering', () => {
    let progress = 0;
    let frames = 0;
    while (
        needsSceneFrame({
            visible: false,
            hidden: false,
            transition: progress,
            settling: false,
            ambient: true
        })
    ) {
        progress = Math.min(1, progress + 0.016 / 0.85);
        frames++;
        assert.ok(frames < 100);
    }
    assert.equal(progress, 1);
    assert.ok(frames > 1);
    assert.equal(
        needsSceneFrame({ visible: false, hidden: false, transition: 1, settling: false, ambient: true }),
        false
    );
    assert.equal(
        needsSceneFrame({ visible: true, hidden: true, transition: 0, settling: true, ambient: true }),
        false
    );
});
