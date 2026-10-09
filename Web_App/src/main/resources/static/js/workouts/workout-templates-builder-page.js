(() => {
    'use strict';
    const root = document.querySelector('.display-builder');
    const form = root?.querySelector('#display-template-form');
    const canvas = root?.querySelector('#display-canvas');
    if (!form || !canvas) return;
    const prototypes = new Map([...root.querySelectorAll('[data-preview-prototype]')].map(t => [t.dataset.previewPrototype,t]));
    const fields = [...form.querySelectorAll('.display-builder__settings input, .display-builder__settings select')];
    let components = [...canvas.querySelectorAll('[name="components"]')].map(input => input.value);
    const history = [];
    let navigating = false;
    const snapshot = () => ({components:[...components],fields:fields.map(field => field.type === 'checkbox' ? field.checked : field.value)});
    let last = snapshot();
    const initial = snapshot();
    const initialChanged = root.dataset.draftChanged === 'true';
    const dirty = () => initialChanged || JSON.stringify(snapshot()) !== JSON.stringify(initial);
    const undo = root.querySelector('[data-undo]');
    const controls = root.querySelector('[data-enhanced-controls]');
    controls.hidden = false;
    let dragging = null;
    root.querySelectorAll('[data-add-component]').forEach(button => {
        button.draggable = true;
        button.addEventListener('dragstart',event => {
            dragging = {component:button.dataset.addComponent,copy:true};
            event.dataTransfer.effectAllowed='copy'; event.dataTransfer.setData('text/plain',dragging.component);
        });
    });
    canvas.addEventListener('dragstart',event => {
        const row=event.target.closest('[data-component]');
        if (!row || event.target.closest('button,input,select,a')) { event.preventDefault(); return; }
        dragging={component:row.dataset.component,copy:false};
        event.dataTransfer.effectAllowed='move'; event.dataTransfer.setData('text/plain',dragging.component);
    });
    root.addEventListener('dragend',() => { dragging=null; });
    canvas.addEventListener('dragover',event => { if(dragging) { event.preventDefault(); event.dataTransfer.dropEffect=dragging.copy?'copy':'move'; } });
    canvas.addEventListener('drop',event => {
        if (!dragging || !prototypes.has(dragging.component)) return;
        event.preventDefault();
        const target=event.target.closest('[data-component]')?.dataset.component;
        const item=dragging.component; dragging=null;
        if (item===target) return;
        remember(); components=components.filter(component=>component!==item);
        const index=components.indexOf(target);
        components.splice(index<0?components.length:index,0,item);
        render(item); last=snapshot();
    });
    function remember() { history.push(last); if (history.length > 20) history.shift(); undo.disabled = false; }
    function restore(state) {
        components = [...state.components];
        fields.forEach((field,index) => { if (field.type === 'checkbox') field.checked=state.fields[index]; else field.value=state.fields[index]; });
        render(); last=snapshot();
    }
    function updateAppearance() {
        canvas.dataset.displayLayout = form.elements.layoutType.value.toLowerCase();
        for (const key of ['theme','density','transition']) canvas.dataset['display'+key[0].toUpperCase()+key.slice(1)] = form.elements[key].value;
        canvas.dataset.displayProgress = String(form.elements.progress.checked);
        canvas.dataset.displayRest = String(form.elements.restTimer.checked);
    }
    function render(focusComponent, action) {
        canvas.replaceChildren();
        components.forEach((component,index) => {
            const prototype = prototypes.get(component);
            if (!prototype) return;
            const row = prototype.content.firstElementChild.cloneNode(true);
            row.draggable = true;
            const actions = row.querySelector('[data-module-actions]');
            for (const [command,label,disabled] of [['up',root.dataset.up,index===0],['down',root.dataset.down,index===components.length-1],['remove',root.dataset.remove,false]]) {
                const button=document.createElement('button'); button.type='submit'; button.name='editAction';
                button.value=command+':'+component; button.formNoValidate=true; button.className='training-launch__action';
                button.textContent=label; button.disabled=disabled;
                button.setAttribute('aria-label',root.dataset.actionFor.replace('{0}',label).replace('{1}',prototype.dataset.label));
                actions.append(button);
            }
            canvas.append(row);
        });
        root.querySelectorAll('[data-add-component]').forEach(button => { button.disabled=components.includes(button.dataset.addComponent); });
        root.querySelector('[data-canvas-empty]').hidden=components.length>0;
        updateAppearance();
        if (focusComponent) {
            const row=[...canvas.children].find(item=>item.dataset.component===focusComponent);
            const control=row?.querySelector(`button[value="${action || 'remove'}:${focusComponent}"]:not(:disabled)`)
                || row?.querySelector('button:not(:disabled)') || root.querySelector(`[data-add-component="${focusComponent}"]`);
            control?.focus();
        }
    }
    form.addEventListener('submit',event => {
        const command=event.submitter?.value || 'save';
        if (command === 'save') { navigating=true; return; }
        event.preventDefault();
        const [action,component]=command.split(':');
        if (!prototypes.has(component)) return;
        const index=components.indexOf(component);
        remember();
        if (action==='add' && index<0) components.push(component);
        else if (action==='remove' && index>=0) components.splice(index,1);
        else if (action==='up' || action==='down') {
            const next=index+(action==='up'?-1:1);
            if(index>=0 && next>=0 && next<components.length) [components[index],components[next]]=[components[next],components[index]];
        }
        render(component,action); last=snapshot();
    });
    fields.forEach(field => field.addEventListener('change',() => { remember(); updateAppearance(); last=snapshot(); }));
    undo.addEventListener('click',() => { const state=history.pop(); if(state) restore(state); undo.disabled=history.length===0; });
    root.querySelector('[data-reset]').addEventListener('click',() => { remember(); restore(initial); });
    root.addEventListener('click',event => {
        const link=event.target.closest('a[href]');
        if (!link || event.defaultPrevented || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey || event.button!==0) return;
        if (dirty() && !window.confirm(root.dataset.leave)) { event.preventDefault(); return; }
        navigating=true;
    });
    window.addEventListener('beforeunload',event => { if(dirty() && !navigating) { event.preventDefault(); event.returnValue=''; } });
    render();
})();
