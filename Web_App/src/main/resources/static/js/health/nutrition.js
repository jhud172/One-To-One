(() => {
    'use strict';
    const form = document.querySelector('[data-nutrition-form]');
    if (!form) return;
    const fields = [...form.elements].filter(field => field.matches('input:not([type=hidden]), textarea'));
    const snapshot = () => fields.map(field => field.value);
    const initial = snapshot();
    const dirty = () => form.dataset.rejected === 'true' || snapshot().some((value, index) => value !== initial[index]);
    const destinations = [...document.querySelectorAll('[data-nutrition-navigate]')];
    let leaving = false;
    const update = () => destinations.forEach(destination => {
        if (dirty()) destination.setAttribute('data-confirm', form.dataset.unsaved);
        else destination.removeAttribute('data-confirm');
    });
    form.addEventListener('input', update);
    form.addEventListener('change', update);
    form.addEventListener('submit', event => { leaving = !event.defaultPrevented; });
    // The shared confirmation handler replays an approved click or native GET submission.
    destinations.forEach(destination => destination.addEventListener(destination.matches('form') ? 'submit' : 'click', event => {
        if (!event.defaultPrevented) leaving = true;
    }));
    window.addEventListener('beforeunload', event => {
        if (leaving || !dirty()) return;
        event.preventDefault(); event.returnValue = '';
    });
    update();
})();
