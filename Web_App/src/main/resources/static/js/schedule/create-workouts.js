(() => {
    'use strict';
    const root = document.querySelector('.schedule-workshop');
    const form = root?.querySelector('[data-schedule-draft]');
    const selected = form?.querySelector('#workout-list');
    const customForm = root?.querySelector('#custom-exercise-form');
    if (!form || !selected || !customForm) return;
    const formId = form.getAttribute('id'); // Named inputs can mask HTMLFormElement properties.
    const status = root.querySelector('[data-workshop-status]');
    const format = (text, value) => (text || '').replace('{0}', String(value));
    const csrf = form.querySelector('input[name="_csrf"]');
    const headers = { 'Content-Type': 'application/json' };
    if (csrf) headers['X-CSRF-TOKEN'] = csrf.value;
    let dirty = root.dataset.draftChanged === 'true';
    let customDirty = false;
    let navigating = false;
    let pending = false;
    root.classList.add('is-enhanced');
    function announce(message, failed = false) {
        status.textContent = message;
        status.hidden = !message;
        status.classList.toggle('schedule-workshop__notice--error', failed);
    }
    function button(text, label) {
        const control = document.createElement('button');
        control.type = 'button'; control.className = 'training-launch__action'; control.textContent = text;
        if (label) control.setAttribute('aria-label', label);
        return control;
    }
    function current(type) { return [...form.querySelectorAll(`[data-selected-type="${type}"]`)]; }
    function sync() {
        const count = current('e').length + current('c').length;
        form.querySelector('[data-selected-count]').textContent = format(root.dataset.count, count);
        form.querySelector('#no-exercises-msg').hidden = count > 0;
        root.querySelectorAll('#suggested-list button[name="editAction"], #custom-exercise-list button[name="editAction"]').forEach(control => {
            const [, type, id] = control.value.split(':');
            control.disabled = current(type).some(input => input.value === id) || count >= 50;
        });
    }
    function movement(data) {
        const row = document.createElement('li'); row.className = 'schedule-movement';
        row.dataset.id = data.id; row.dataset.type = data.type; row.dataset.name = data.name;
        row.dataset.color = data.color || '';
        const heading = document.createElement('div'); heading.className = 'schedule-movement__heading';
        const title = document.createElement('h3'); title.textContent = data.name;
        const remove = button(root.dataset.remove, format(root.dataset.removeFor, data.name));
        remove.type = 'submit'; remove.name = 'editAction'; remove.value = `remove:${data.type}:${data.id}`;
        remove.setAttribute('form', formId); remove.formNoValidate = true;
        heading.append(title, remove);
        const kind = document.createElement('p'); kind.className = 'schedule-movement__type';
        kind.textContent = data.type === 'c' ? root.dataset.custom : root.dataset.exercise;
        const details = document.createElement('details'); const summary = document.createElement('summary');
        summary.textContent = root.dataset.details; details.append(summary);
        for (const value of [data.description, data.howto]) {
            if (value) { const paragraph = document.createElement('p'); paragraph.textContent = value; details.append(paragraph); }
        }
        if (data.embed) {
            try {
                const url = new URL(data.embed);
                if (url.protocol === 'https:' && ['www.youtube.com', 'player.vimeo.com'].includes(url.hostname)) {
                    const link = document.createElement('a'); link.href = url.href;
                    link.target = '_blank'; link.rel = 'noopener noreferrer'; link.textContent = customForm.querySelector('label[for="custom-exercise-video"]').textContent;
                    details.append(link);
                }
            } catch { /* Invalid optional media never blocks editing. */ }
        }
        row.append(heading, kind, details); return row;
    }
    function add(source) {
        if (!source) return;
        const {type, id} = source.dataset;
        if (!['e', 'c'].includes(type) || !/^\d+$/.test(id)) return;
        if (current(type).some(input => input.value === id)) return;
        if (current('e').length + current('c').length >= 50) { announce(root.dataset.limit, true); return; }
        const input = document.createElement('input'); input.type = 'hidden'; input.dataset.selectedType = type;
        input.name = type === 'c' ? 'customExerciseIds' : 'exerciseIds'; input.value = id; form.append(input);
        const row = movement(source.dataset);
        const firstCustom = selected.querySelector('[data-type="c"]');
        if (type === 'e' && firstCustom) selected.insertBefore(row, firstCustom); else selected.append(row);
        dirty = true; sync(); announce(root.dataset.draftUpdated);
    }
    function remove(type, id) {
        current(type).find(input => input.value === id)?.remove();
        selected.querySelector(`[data-type="${type}"][data-id="${id}"]`)?.remove();
        dirty = true; sync(); announce(root.dataset.draftUpdated);
    }
    form.addEventListener('input', () => { dirty = true; });
    form.addEventListener('submit', event => {
        const command = event.submitter?.value || 'save';
        if (command === 'save') {
            if (customDirty && !window.confirm(root.dataset.leave)) { event.preventDefault(); return; }
            if (pending) { event.preventDefault(); return; }
            navigating = true;
            requestAnimationFrame(() => { form.querySelector('#save-workout-btn').disabled = true; });
            return;
        }
        event.preventDefault();
        const [action, type, id] = command.split(':');
        if (!['e','c'].includes(type) || !/^\d+$/.test(id)) return;
        if (action === 'remove') remove(type, id);
        else if (action === 'add') add(root.querySelector(`#${type === 'c' ? 'custom-exercise-list' : 'suggested-list'} > [data-id="${id}"]`));
    });
    const searches = [...root.querySelectorAll('[data-schedule-search]')];
    const fold = value => value.normalize('NFKC').toLocaleLowerCase(document.documentElement.lang || undefined).trim();
    function filter(input) {
        const list = root.querySelector('#' + input.dataset.searchTarget); if (!list) return;
        const query = fold(input.value);
        let matches = 0;
        [...list.children].forEach(item => {
            item.hidden = !fold(item.dataset.name || '').includes(query);
            if (!item.hidden) matches++;
        });
        const search = input.closest('.schedule-search');
        search.querySelector('[data-search-clear]').hidden = !input.value;
        search.querySelector('[data-search-status]').textContent = format(root.dataset.matches, matches);
        root.querySelector(`[data-no-results-for="${list.id}"]`).hidden = matches > 0 || list.children.length === 0;
    }
    searches.forEach(input => {
        input.addEventListener('input', () => filter(input));
        input.addEventListener('keydown', event => {
            if (event.key === 'Escape') { event.preventDefault(); input.value = ''; filter(input); }
        });
        input.closest('.schedule-search').querySelector('[data-search-clear]').addEventListener('click', () => {
            input.value = ''; filter(input); input.focus();
        });
        filter(input);
    });
    root.addEventListener('click', event => {
        const link = event.target.closest('a[data-custom-edit]');
        if (link) {
            if (customDirty && !window.confirm(root.dataset.leave)) { event.preventDefault(); return; }
            event.preventDefault();
            const item = link.closest('li');
            customForm.querySelector('#custom-exercise-id').value = item.dataset.id;
            for (const [field,key] of [['name','name'],['description','description'],['howto','howto'],['video','video'],['color','color']]) {
                customForm.querySelector('#custom-exercise-' + field).value = item.dataset[key] || '';
            }
            customDirty = false; root.querySelector('#custom-exercise-editor').open = true;
            customForm.querySelector('#custom-exercise-name').focus();
            return;
        }
        const navigation = event.target.closest('a[href]');
        if (!navigation || navigation.target === '_blank' || navigation.getAttribute('href').startsWith('#')) return;
        if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey || event.button !== 0) return;
        if (pending || ((dirty || customDirty) && !window.confirm(root.dataset.leave))) { event.preventDefault(); return; }
        if (navigation.closest('#existing-workouts')) {
            const url = new URL(navigation.href);
            searches.forEach(input => { if (input.value) url.searchParams.set(input.name, input.value); });
            navigation.href = url.href;
        }
        navigating = true;
    });
    root.addEventListener('submit', event => {
        if (event.target === form || event.target === customForm || event.target.method === 'dialog'
                || event.target.matches('[data-custom-delete]')) return;
        if (pending || ((dirty || customDirty) && !window.confirm(root.dataset.leave))) { event.preventDefault(); return; }
        navigating = true;
    });
    window.addEventListener('beforeunload', event => {
        if (!navigating && (dirty || customDirty || pending)) { event.preventDefault(); event.returnValue = ''; }
    });
    async function request(url, payload) {
        const controller = new AbortController(); const timeout = setTimeout(() => controller.abort(), 12000);
        try {
            const response = await fetch(url, {method:'POST', headers, signal:controller.signal,
                body:JSON.stringify(payload || {})});
            if (!response.ok) {
                const error = new Error('Unconfirmed'); error.status = response.status; throw error;
            }
            return await response.json();
        } finally { clearTimeout(timeout); }
    }
    function csrfForm(action) {
        const target = document.createElement('form'); target.method = 'post'; target.action = action;
        if (csrf) target.append(csrf.cloneNode(true)); return target;
    }
    function upsert(data) {
        if (!Number.isSafeInteger(data.id) || data.id <= 0 || typeof data.name !== 'string') throw new Error('Unconfirmed');
        const list = root.querySelector('#custom-exercise-list');
        let item = list.querySelector(`[data-id="${data.id}"]`);
        if (!item) { item = document.createElement('li'); list.prepend(item); }
        Object.assign(item.dataset, {id:String(data.id), type:'c', name:data.name, description:data.description || '',
            howto:data.howTo || '', video:data.videoUrl || '', embed:data.embedUrl || '', color:data.colorTag || ''});
        const heading = document.createElement('h3'); heading.textContent = data.name;
        const description = document.createElement('p'); description.textContent = data.description || '';
        const actions = document.createElement('div'); actions.className = 'schedule-workshop__actions';
        const addControl = button(root.dataset.add, format(root.dataset.addFor, data.name));
        addControl.type='submit'; addControl.name='editAction'; addControl.value=`add:c:${data.id}`;
        addControl.setAttribute('form', formId); addControl.formNoValidate=true;
        const edit = document.createElement('a'); edit.className='training-launch__action'; edit.textContent=root.dataset.edit;
        edit.href=`/workout?customEdit=${data.id}`; edit.dataset.customEdit='';
        const deleteForm=csrfForm(`/workout/custom-exercises/${data.id}/remove`);
        deleteForm.dataset.customDelete=''; deleteForm.dataset.confirm=`${root.dataset.delete}: ${data.name}?`;
        const deleteControl=button(root.dataset.delete, `${root.dataset.delete}: ${data.name}`);
        deleteControl.type='submit'; deleteControl.classList.add('schedule-workshop__delete'); deleteForm.append(deleteControl);
        actions.append(addControl, edit, deleteForm); item.replaceChildren(heading, description, actions);
        const old=selected.querySelector(`[data-type="c"][data-id="${data.id}"]`);
        if (old) old.replaceWith(movement(item.dataset));
        item.draggable=true; sync(); searches.forEach(filter); return item;
    }
    customForm.addEventListener('input', () => { customDirty=true; });
    customForm.addEventListener('reset', event => {
        event.preventDefault();
        for (const input of customForm.querySelectorAll('input:not([name="_csrf"]), textarea, select')) input.value='';
        customDirty=false;
    });
    customForm.addEventListener('submit', async event => {
        event.preventDefault(); if (pending) return;
        pending=true; const save=customForm.querySelector('[type="submit"]'); save.disabled=true;
        const snapshot=new FormData(customForm);
        const id=snapshot.get('id'); const payload=Object.fromEntries(['name','description','howTo','videoUrl','colorTag'].map(key=>[key,snapshot.get(key) || '']));
        try {
            upsert(await request(id ? `/workout/custom-exercises/${id}` : '/workout/custom-exercises', payload));
            // Preserve edits made while a save was in flight.
            const latest=new FormData(customForm);
            const changed=[...snapshot.keys()].some(key=>snapshot.get(key)!==latest.get(key));
            if (!changed) customForm.reset();
            announce(root.dataset.customSaved);
        } catch { announce(root.dataset.customFailed, true); }
        finally { pending=false; save.disabled=false; }
    });
    root.addEventListener('submit', async event => {
        if (!event.target.matches('[data-custom-delete]')) return;
        event.preventDefault(); if (pending) return;
        const item=event.target.closest('li'); const id=item.dataset.id;
        pending=true; const control=event.submitter; if (control) control.disabled=true;
        try {
            await request(`/workout/custom-exercises/${id}/delete`);
            item.remove(); if (current('c').some(input=>input.value===id)) remove('c',id);
            if (customForm.querySelector('#custom-exercise-id').value===id) customForm.reset();
            searches.forEach(filter);
        } catch (error) { announce(error.status===409 ? root.dataset.customBlocked : root.dataset.failed, true); }
        finally { pending=false; if (control?.isConnected) control.disabled=false; }
    });
    root.querySelectorAll('#suggested-list > li, #custom-exercise-list > li').forEach(item=>{ item.draggable=true; });
    root.addEventListener('dragstart', event=>{
        const item=event.target.closest('#suggested-list > li, #custom-exercise-list > li');
        if (item && event.dataTransfer) event.dataTransfer.setData('text/plain', `${item.dataset.type}:${item.dataset.id}`);
    });
    selected.addEventListener('dragover', event=>event.preventDefault());
    selected.addEventListener('drop', event=>{
        event.preventDefault(); const value=event.dataTransfer?.getData('text/plain') || '';
        if (!/^[ec]:\d+$/.test(value)) return;
        const [type,id]=value.split(':');
        add(root.querySelector(`#${type==='c' ? 'custom-exercise-list' : 'suggested-list'} > [data-id="${id}"]`));
    });
    const ai=root.querySelector('#ai-suggestions-btn');
    ai.addEventListener('click', async ()=>{
        if (root.dataset.premium!=='true') {
            const dialog=root.querySelector('#upgrade-modal');
            if (typeof dialog.showModal==='function') dialog.showModal();
            else announce(root.dataset.aiFailed,true);
            return;
        }
        if (pending) return; pending=true; ai.disabled=true;
        try {
            const data=await request('/workout/ai-suggestions',{prompt:''});
            if (!Array.isArray(data.suggestions) || data.suggestions.some(item=>typeof item!=='string')) throw new Error('Unconfirmed');
            const list=root.querySelector('#ai-suggestions-list'); list.replaceChildren();
            for (const text of data.suggestions) {
                const row=document.createElement('li'); const name=document.createElement('h3'); name.textContent=text;
                const addControl=button(root.dataset.add,format(root.dataset.addFor,text));
                addControl.addEventListener('click',async()=>{
                    if (pending) return; pending=true; addControl.disabled=true;
                    try { add(upsert(await request('/workout/custom-exercises',{name:text,description:''}))); }
                    catch { announce(root.dataset.customFailed,true); }
                    finally { pending=false; addControl.disabled=false; }
                });
                row.append(name,addControl); list.append(row);
            }
            root.querySelector('#ai-suggestions').hidden=false;
        } catch { announce(root.dataset.aiFailed,true); }
        finally { pending=false; ai.disabled=false; }
    });
    sync();
})();
