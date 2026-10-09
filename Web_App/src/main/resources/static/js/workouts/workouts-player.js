(() => {
    const root = document.querySelector('.personal-player[data-workout-session-id]');
    if (!root) return;
    const forms = [...root.querySelectorAll('form[data-set-id]')];
    const notice = root.querySelector('[data-session-notice]');
    const text = key => root.dataset[key] || '';
    const mediaDrafts = new Set();
    let saving = false;
    const dirty = () => saving || mediaDrafts.size > 0 || forms.some(form => form.dataset.saveState !== 'saved');
    const status = (form, state) => {
        form.dataset.saveState = state;
        form.querySelector('[data-save-status]').textContent = text({saved:'savedLabel',unsaved:'unsavedLabel',saving:'savingLabel',failed:'failedLabel'}[state]);
    };
    const csrfHeaders = form => {
        const token = form.querySelector('input[name="_csrf"]')?.value;
        return token ? {[root.dataset.csrfHeader || 'X-CSRF-TOKEN']:token} : {};
    };
    const jsonRequest = async (url, options) => {
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 12000);
        try {
            const response = await fetch(url, {...options, signal:controller.signal});
            if (!response.ok || !response.headers.get('content-type')?.includes('application/json')) throw new Error('Unconfirmed response');
            return await response.json();
        } finally { clearTimeout(timeout); }
    };
    const summary = (data, setId) => {
        if (String(data.setId) !== setId || !Number.isFinite(data.totalVolume) || data.totalVolume < 0
            || typeof data.completed !== 'boolean' || !Number.isInteger(data.completedSets) || !Number.isInteger(data.totalSets)
            || data.completedSets < 0 || data.completedSets > data.totalSets
            || data.percent !== (data.totalSets ? Math.floor(data.completedSets * 100 / data.totalSets) : 0)) throw new Error('Invalid summary');
        root.querySelector('[data-session-volume]').textContent = new Intl.NumberFormat(document.documentElement.lang,{maximumFractionDigits:1}).format(data.totalVolume);
        root.querySelector('[data-session-sets]').textContent = `${data.completedSets} / ${data.totalSets}`;
        root.querySelector('[data-session-percent]').textContent = `${data.percent}%`;
        const progress = root.querySelector('[data-session-progress]');
        progress.value = data.percent; progress.textContent = `${data.percent}%`; progress.setAttribute('aria-valuenow',String(data.percent));
        root.querySelector('[data-session-complete]').hidden = !data.completed;
    };
    forms.forEach(form => {
        let revision = 0;
        form.addEventListener('input', () => { revision++; status(form,'unsaved'); });
        form.addEventListener('submit', async event => {
            event.preventDefault();
            if (saving || !form.reportValidity()) return;
            const submittedRevision = revision;
            const payload = {weight:form.elements.weight.value === '' ? null : Number(form.elements.weight.value),
                reps:form.elements.reps.value === '' ? null : Number(form.elements.reps.value),
                notes:form.elements.notes.value, completed:form.elements.completed.checked, replaceValues:true};
            saving = true; status(form,'saving'); form.setAttribute('aria-busy','true');
            const saveButtons = forms.flatMap(item => [...item.querySelectorAll('button[type=submit]')]);
            saveButtons.forEach(button => { button.disabled = true; });
            try {
                const data = await jsonRequest(form.action,{method:'POST',headers:{...csrfHeaders(form),'Content-Type':'application/json'},body:JSON.stringify(payload)});
                summary(data,form.dataset.setId);
                form.closest('.session-set').classList.toggle('is-complete',payload.completed);
                form.closest('.session-set').querySelector('[data-set-state]').textContent = payload.completed ? text('completeLabel') : text('openLabel');
                status(form,revision === submittedRevision ? 'saved' : 'unsaved');
            } catch { status(form,'failed'); }
            finally { saving = false; form.removeAttribute('aria-busy'); saveButtons.forEach(button => { button.disabled = false; }); }
        });
    });
    const guardNativeForm = form => form.addEventListener('submit', event => {
        if (dirty()) { event.preventDefault(); notice.textContent = text('otherChangesLabel'); notice.scrollIntoView({block:'center'}); }
    });
    root.querySelectorAll('form:not([data-set-id])').forEach(guardNativeForm);
    window.addEventListener('beforeunload', event => { if (dirty()) { event.preventDefault(); event.returnValue = ''; } });

    const timer = root.querySelector('[data-rest-timer]');
    const clock = timer.querySelector('[data-rest-clock]');
    const timerStatus = timer.querySelector('[data-rest-status]');
    const pause = timer.querySelector('[data-rest-pause]');
    let interval = null, deadline = 0, remaining = 0;
    timer.hidden = false;
    const tick = () => {
        if (deadline) remaining = Math.max(0,Math.ceil((deadline - Date.now()) / 1000));
        clock.textContent = `${String(Math.floor(remaining/60)).padStart(2,'0')}:${String(remaining%60).padStart(2,'0')}`;
        if (deadline && remaining === 0) { clearInterval(interval); interval = null; deadline = 0; pause.disabled = true; timerStatus.textContent = timer.dataset.finishedLabel; }
    };
    forms.forEach(form => {
        const button = form.querySelector('[data-rest]'); button.hidden = false;
        button.addEventListener('click', () => {
            remaining = Number(form.dataset.restSeconds);
            if (!Number.isInteger(remaining) || remaining <= 0 || remaining > 3600) return;
            clearInterval(interval); deadline = Date.now() + remaining * 1000;
            pause.disabled = false; pause.textContent = timer.dataset.pauseLabel;
            timerStatus.textContent = timer.dataset.runningLabel; timer.querySelector('details').open = true;
            tick(); interval = setInterval(tick,250);
        });
    });
    pause.addEventListener('click', () => {
        if (deadline) { tick(); deadline = 0; clearInterval(interval); interval = null; pause.textContent = timer.dataset.resumeLabel; timerStatus.textContent = timer.dataset.pausedLabel; }
        else if (remaining > 0) { deadline = Date.now() + remaining * 1000; interval = setInterval(tick,250); pause.textContent = timer.dataset.pauseLabel; timerStatus.textContent = timer.dataset.runningLabel; }
    });
    timer.querySelector('[data-rest-reset]').addEventListener('click', () => { clearInterval(interval); interval = null; deadline = 0; remaining = 0; pause.disabled = true; timerStatus.textContent = timer.dataset.readyLabel; tick(); });

    // Camera capture remains optional. Nothing is uploaded until Upload is explicitly selected.
    let activeCamera = null;
    const cameraCleanups = [];
    root.querySelectorAll('[data-video-panel]').forEach(panel => {
        const camera = panel.querySelector('[data-camera]');
        const videoStatus = panel.querySelector('[data-video-status]');
        const start = panel.querySelector('[data-record-start]'), stop = panel.querySelector('[data-record-stop]');
        const upload = panel.querySelector('[data-record-upload]'), discard = panel.querySelector('[data-record-discard]');
        const preview = panel.querySelector('[data-record-preview]');
        let recorder = null, stream = null, blob = null, objectUrl = null, pending = false, disposed = false, tooLarge = false;
        const releaseStream = () => { stream?.getTracks().forEach(track => track.stop()); stream = null; if (activeCamera === panel) activeCamera = null; };
        const releaseUrl = () => { if (objectUrl) URL.revokeObjectURL(objectUrl); objectUrl = null; };
        const discardPreview = () => { releaseUrl(); blob = null; preview.removeAttribute('src'); preview.load(); preview.hidden = true; upload.hidden = true; discard.hidden = true; mediaDrafts.delete(panel); };
        cameraCleanups.push(() => { disposed = true; if (recorder?.state === 'recording') recorder.stop(); releaseStream(); releaseUrl(); });
        const mime = typeof MediaRecorder !== 'undefined' ? ['video/webm','video/mp4'].find(type => MediaRecorder.isTypeSupported(type)) : null;
        if (!mime || !navigator.mediaDevices?.getUserMedia || !window.isSecureContext) return;
        camera.hidden = false;
        start.addEventListener('click', async () => {
            if (activeCamera || pending || saving || dirty()) { notice.textContent = text('otherChangesLabel'); return; }
            activeCamera = panel; pending = true; start.disabled = true; mediaDrafts.add(panel);
            try {
                stream = await navigator.mediaDevices.getUserMedia({video:true,audio:false});
                if (disposed) { releaseStream(); return; }
                releaseUrl(); blob = null; let chunks = [], size = 0; tooLarge = false;
                recorder = new MediaRecorder(stream,{mimeType:mime});
                recorder.ondataavailable = event => {
                    if (!event.data.size) return;
                    size += event.data.size;
                    if (size > 8 * 1024 * 1024) { tooLarge = true; if (recorder.state === 'recording') recorder.stop(); }
                    else chunks.push(event.data);
                };
                recorder.onstop = () => {
                    releaseStream(); stop.hidden = true; start.hidden = false;
                    if (disposed) return;
                    if (tooLarge || !chunks.length) { mediaDrafts.delete(panel); videoStatus.textContent = text('videoFailedLabel'); return; }
                    blob = new Blob(chunks,{type:recorder.mimeType}); objectUrl = URL.createObjectURL(blob);
                    preview.src = objectUrl; preview.hidden = false; upload.hidden = false; discard.hidden = false;
                    videoStatus.textContent = text('recordedLabel'); upload.focus();
                };
                recorder.onerror = () => { releaseStream(); mediaDrafts.delete(panel); stop.hidden = true; start.hidden = false; videoStatus.textContent = text('cameraFailedLabel'); };
                recorder.start(1000); start.hidden = true; stop.hidden = false;
                videoStatus.textContent = text('recordingLabel'); stop.focus();
            } catch { releaseStream(); mediaDrafts.delete(panel); videoStatus.textContent = text('cameraFailedLabel'); }
            finally { pending = false; start.disabled = false; }
        });
        stop.addEventListener('click', () => { if (recorder?.state === 'recording') recorder.stop(); releaseStream(); });
        discard.addEventListener('click', () => { discardPreview(); videoStatus.textContent = ''; start.focus(); });
        upload.addEventListener('click', async () => {
            if (!blob || saving || forms.some(form => form.dataset.saveState !== 'saved')) { notice.textContent = text('otherChangesLabel'); return; }
            const data = new FormData(); data.append('video',blob,`set.${blob.type.includes('mp4') ? 'mp4' : 'webm'}`);
            upload.disabled = true; discard.disabled = true; start.disabled = true; videoStatus.textContent = text('uploadingLabel');
            try {
                const response = await jsonRequest(`/workouts/studio/${root.dataset.workoutSessionId}/sets/${panel.dataset.videoPanel}/video`,{method:'POST',headers:csrfHeaders(panel.querySelector('form')),body:data});
                if (!Number.isSafeInteger(response.videoId) || response.status !== 'STORED') throw new Error('Unconfirmed recording');
                mediaDrafts.delete(panel); blob = null; upload.hidden = true; discard.hidden = true;
                videoStatus.textContent = text('videoSavedLabel');
                panel.querySelectorAll('form[data-confirm]').forEach(form => { form.hidden = true; });
                const removeForm = document.createElement('form');
                removeForm.method = 'post';
                removeForm.action = `/workouts/studio/${root.dataset.workoutSessionId}/sets/${panel.dataset.videoPanel}/recording/${response.videoId}/delete`;
                removeForm.dataset.confirm = text('deleteConfirm');
                const token = panel.querySelector('input[name="_csrf"]');
                if (token) removeForm.append(token.cloneNode(true));
                const removeButton = document.createElement('button');
                removeButton.type = 'submit'; removeButton.className = 'training-launch__action';
                removeButton.textContent = text('recordDeleteLabel'); removeForm.append(removeButton);
                guardNativeForm(removeForm); panel.append(removeForm);
            } catch { videoStatus.textContent = text('videoFailedLabel'); }
            finally { upload.disabled = false; discard.disabled = false; start.disabled = false; }
        });
    });
    window.addEventListener('pagehide', () => { clearInterval(interval); cameraCleanups.forEach(cleanup => cleanup()); });
})();
