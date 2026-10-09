(() => {
    'use strict';
    const player = document.querySelector('.session-player');
    const timer = player?.querySelector('[data-rest-timer]');
    if (!timer) return;
    const clock = timer.querySelector('[data-rest-clock]');
    const duration = timer.querySelector('[data-rest-duration]');
    const start = timer.querySelector('[data-rest-start]');
    const pause = timer.querySelector('[data-rest-pause]');
    const reset = timer.querySelector('[data-rest-reset]');
    const status = timer.querySelector('[data-rest-status]');
    const storageKey = 'one-to-one.rest-timer';
    const sessionId = player.dataset.sessionId;
    const dirtyForms = new Set();
    const setForms = Array.from(player.querySelectorAll('.session-set__form'));
    const notice = player.querySelector('[data-session-notice]');
    let saving = false;
    const snapshot = form => JSON.stringify(Array.from(form.querySelectorAll('input:not([type="hidden"])')).map(input => [input.name, input.value]));
    const savedValues = new Map(setForms.map(form => [form, snapshot(form)]));
    function showStatus(form, message, state) {
        const element = form.querySelector('[data-save-status]');
        if (element) element.textContent = message;
        form.dataset.saveState = state;
    }
    player.querySelectorAll('.session-set__form').forEach(form => {
        form.addEventListener('input', () => {
            if (snapshot(form) === savedValues.get(form)) {
                dirtyForms.delete(form);
                showStatus(form, player.dataset.savedLabel, 'saved');
            } else {
                dirtyForms.add(form);
                showStatus(form, player.dataset.unsavedLabel, 'unsaved');
            }
        });
    });
    // Capture before the shared confirmation handler: other drafts must survive
    // adding/deleting sets, finishing, or marking the final set complete.
    window.addEventListener('submit', event => {
        const form = event.target;
        if (!(form instanceof HTMLFormElement) || !player.contains(form)) return;
        const isSetSave = form.matches('.session-set__form');
        const ownSetForm = form.closest('.session-set')?.querySelector('.session-set__form');
        const marksDone = event.submitter?.name === 'completed' && event.submitter.value === 'true';
        if (saving || ((!isSetSave || marksDone) && Array.from(dirtyForms).some(draft => draft !== ownSetForm))) {
            event.preventDefault();
            event.stopImmediatePropagation();
            notice.textContent = player.dataset.otherChangesLabel;
            const firstDraft = Array.from(dirtyForms).find(draft => draft !== ownSetForm);
            firstDraft?.querySelector('input:not([type="hidden"])')?.focus();
            return;
        }
        if (!isSetSave || !window.fetch || !window.AbortController) return;
        event.preventDefault();
        event.stopImmediatePropagation();
        saveSet(form, event.submitter);
    }, true);
    player.addEventListener('submit', event => {
        // Only runs after shared deletion confirmation is approved.
        const ownSetForm = event.target.closest('.session-set')?.querySelector('.session-set__form');
        if (ownSetForm) dirtyForms.delete(ownSetForm);
    });
    window.addEventListener('beforeunload', event => {
        if (!dirtyForms.size && !saving) return;
        event.preventDefault();
        event.returnValue = '';
    });
    player.addEventListener('click', event => {
        if (!(event.target instanceof Element) || !event.target.closest('[data-session-instruction]') || (!saving && !dirtyForms.size)) return;
        event.preventDefault();
        notice.textContent = player.dataset.otherChangesLabel;
        dirtyForms.values().next().value?.querySelector('input:not([type="hidden"])')?.focus();
    });
    async function saveSet(form, submitter) {
        saving = true;
        dirtyForms.add(form);
        const body = new URLSearchParams(new FormData(form));
        if (submitter?.name) body.set(submitter.name, submitter.value);
        const controls = Array.from(player.querySelectorAll('form input, form button'));
        const disabledBefore = controls.map(control => control.disabled);
        controls.forEach(control => { control.disabled = true; });
        form.setAttribute('aria-busy', 'true');
        showStatus(form, player.dataset.savingLabel, 'saving');
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 15000);
        let completed = false;
        try {
            const response = await fetch(form.action, {
                method: 'POST', credentials: 'same-origin', body,
                headers: {Accept: 'application/json', 'X-Workout-Async': '1'},
                signal: controller.signal
            });
            if (!response.ok || response.redirected || !response.headers.get('content-type')?.includes('application/json')) throw new Error('Save not confirmed');
            const view = await response.json();
            const exercise = view.exercises?.find(item => item.sets?.some(set => String(set.setId) === form.dataset.setId));
            const saved = exercise?.sets.find(set => String(set.setId) === form.dataset.setId);
            if (String(view.sessionId) !== sessionId || !saved || typeof saved.completed !== 'boolean' || !view.summary) throw new Error('Invalid save response');
            ['weight', 'reps', 'notes'].forEach(name => { form.elements.namedItem(name).value = saved[name] ?? ''; });
            savedValues.set(form, snapshot(form));
            dirtyForms.delete(form);
            const row = form.closest('.session-set');
            row.classList.toggle('is-complete', saved.completed);
            row.querySelector('[data-set-state]').textContent = saved.completed ? player.dataset.completeLabel : player.dataset.openLabel;
            const toggle = form.querySelector('button[name="completed"]');
            toggle.value = String(!saved.completed);
            toggle.textContent = saved.completed ? player.dataset.reopenLabel : player.dataset.doneLabel;
            const article = form.closest('[data-exercise-id]');
            article.querySelector('[data-exercise-state]').textContent = exercise.completed ? player.dataset.completeLabel : player.dataset.openLabel;
            const summary = view.summary;
            player.querySelector('[data-session-percent]').textContent = summary.completionPercent + '%';
            const progress = player.querySelector('[data-session-progress]');
            progress.value = summary.completionPercent;
            progress.textContent = summary.completionPercent + '%';
            progress.setAttribute('aria-valuenow', String(summary.completionPercent));
            player.querySelector('[data-session-exercises]').textContent = summary.completedExercises + ' / ' + summary.exerciseCount;
            player.querySelector('[data-session-sets]').textContent = summary.completedSets + ' / ' + summary.totalSets;
            player.querySelector('[data-session-volume]').textContent = Number(summary.totalVolume).toLocaleString(document.documentElement.lang, {minimumFractionDigits: 1, maximumFractionDigits: 1});
            showStatus(form, player.dataset.savedLabel, 'saved');
            notice.textContent = player.dataset.savedLabel;
            completed = view.completed === true;
        } catch (_) {
            // A timed-out request may have reached the server. Retrying updates
            // this same set; never clear the draft or assert that nothing saved.
            savedValues.delete(form);
            showStatus(form, player.dataset.failedLabel, 'failed');
            notice.textContent = player.dataset.failedLabel;
        } finally {
            clearTimeout(timeout);
            saving = false;
            form.removeAttribute('aria-busy');
            controls.forEach((control, index) => { control.disabled = disabledBefore[index]; });
        }
        if (completed && !dirtyForms.size) location.assign('/workout-session/' + sessionId + '/complete');
        else submitter?.focus();
    }
    let remaining = Number(duration.value) * 1000;
    let deadline = null;
    let interval = null;

    // Only timer state is retained in this tab, never workout or health values.
    function persist() {
        try {
            sessionStorage.setItem(storageKey, JSON.stringify({sessionId, duration: Number(duration.value), remaining, deadline}));
        } catch (_) { /* Timer remains usable when browser storage is unavailable. */ }
    }
    function render() {
        const seconds = Math.max(0, Math.ceil(remaining / 1000));
        clock.textContent = String(Math.floor(seconds / 60)).padStart(2, '0') + ':' + String(seconds % 60).padStart(2, '0');
        start.disabled = deadline !== null;
        pause.disabled = deadline === null;
        duration.disabled = deadline !== null;
    }
    function stopTicking() {
        clearInterval(interval);
        interval = null;
    }
    function tick() {
        remaining = Math.max(0, deadline - Date.now());
        if (remaining === 0) {
            deadline = null;
            stopTicking();
            status.textContent = timer.dataset.finishedLabel;
            persist();
        }
        render();
    }
    function run() {
        stopTicking();
        interval = setInterval(tick, 250);
        status.textContent = timer.dataset.runningLabel;
        tick();
    }
    try {
        const saved = JSON.parse(sessionStorage.getItem(storageKey));
        const validDuration = saved && Array.from(duration.options).some(option => Number(option.value) === saved.duration);
        if (saved?.sessionId === sessionId && validDuration && Number.isFinite(saved.remaining) && saved.remaining >= 0 && saved.remaining <= 180000 && (saved.deadline === null || Number.isFinite(saved.deadline))) {
            duration.value = String(saved.duration);
            remaining = saved.remaining;
            deadline = saved.deadline;
            if (deadline !== null) run();
            else status.textContent = remaining === 0 ? timer.dataset.finishedLabel : remaining < saved.duration * 1000 ? timer.dataset.pausedLabel : timer.dataset.readyLabel;
        }
    } catch (_) { /* Invalid or unavailable storage uses the visible default. */ }

    start.addEventListener('click', () => {
        if (remaining === 0) remaining = Number(duration.value) * 1000;
        deadline = Date.now() + remaining;
        persist();
        run();
    });
    pause.addEventListener('click', () => {
        remaining = Math.max(0, deadline - Date.now());
        deadline = null;
        stopTicking();
        status.textContent = timer.dataset.pausedLabel;
        persist();
        render();
    });
    function resetTimer() {
        deadline = null;
        remaining = Number(duration.value) * 1000;
        stopTicking();
        status.textContent = timer.dataset.readyLabel;
        persist();
        render();
    }
    reset.addEventListener('click', resetTimer);
    duration.addEventListener('change', resetTimer);
    document.addEventListener('visibilitychange', () => {
        if (!document.hidden && deadline !== null) tick();
    });
    window.addEventListener('pagehide', persist);
    render();
    timer.classList.remove('hidden');
})();
