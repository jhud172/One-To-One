// A queued RAF timestamp can precede the performance.now() that scheduled it.
// Bound both ends so startup cannot run a path backwards, or resume with a jump.
export function frameDelta(timestamp, previous) {
    return Math.max(0, Math.min((timestamp - previous) / 1000, 0.05));
}

const clamp = (value) => Math.max(0, Math.min(1, value));
const ease = (value) => {
    const t = clamp(value);
    return t * t * (3 - 2 * t);
};

// Re-centre first, turn the closed sculpture, then release its two hinges.
export function openingPose(progress) {
    const p = clamp(progress);
    return {
        align: ease(p / 0.22),
        open: ease((p - 0.42) / 0.58),
        turn: 1.1 * ease((p - 0.22) / 0.2) * (1 - ease((p - 0.42) / 0.58)),
        phase: p < 0.22 ? 'aligning' : p < 0.42 ? 'turning' : 'opening'
    };
}

export function pointerPose(clientX, clientY, bounds) {
    const x = clamp((clientX - bounds.left) / Math.max(1, bounds.width)) * 2 - 1;
    const y = clamp((clientY - bounds.top) / Math.max(1, bounds.height)) * 2 - 1;
    return { rotation: x * 0.42, tilt: y * 0.18 };
}

// Preserve the cinematic entrance, but give subsequent mode selections an immediate response.
export function transitionPose(progress, cinematic = false) {
    if (cinematic) return openingPose(progress);
    return { align: ease(progress / 0.25), open: ease(progress), turn: 0, phase: 'changing' };
}

export function needsSceneFrame({ visible, hidden, transition, settling, ambient, requested = false }) {
    return !hidden && (requested || transition < 1 || settling || (visible && ambient));
}
