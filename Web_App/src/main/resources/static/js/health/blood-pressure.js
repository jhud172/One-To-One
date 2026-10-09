(() => {
    'use strict';
    const form = document.querySelector('[data-bp-reading-form]');
    if (!form) return;
    const fields = [...form.elements].filter(field => field.matches('input:not([type=hidden]), textarea, select'));
    const snapshot = () => fields.map(field => field.value);
    const initial = snapshot();
    const rejected = form.dataset.rejected === 'true';
    const dirty = () => rejected || snapshot().some((value, index) => value !== initial[index]);
    let leaving = false;
    const update = () => document.querySelectorAll('[data-bp-cancel]').forEach(link => {
        if (dirty()) link.setAttribute('data-confirm', form.dataset.unsaved);
        else link.removeAttribute('data-confirm');
    });
    form.addEventListener('input', update);
    form.addEventListener('change', update);
    form.addEventListener('submit', event => { leaving = !event.defaultPrevented; });
    // The shared confirmation handler stops the first click and replays only an approved action.
    document.querySelectorAll('[data-bp-cancel]').forEach(link => link.addEventListener('click', event => {
        if (!event.defaultPrevented) leaving = true;
    }));
    window.addEventListener('beforeunload', event => {
        if (leaving || !dirty()) return;
        event.preventDefault(); event.returnValue = '';
    });
    update();
})();
