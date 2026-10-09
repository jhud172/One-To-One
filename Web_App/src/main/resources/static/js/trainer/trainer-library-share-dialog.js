(function () {
    'use strict';
    const origins = new WeakMap();
    document.querySelectorAll('details[data-share-dialog]').forEach((target) => {
        const summary = target.querySelector('summary');
        summary?.addEventListener('click', () => origins.set(target, summary));
        target.addEventListener('keydown', (event) => {
            if (event.key !== 'Escape' || !target.open) return;
            event.preventDefault();
            event.stopPropagation();
            target.open = false;
            const origin = origins.get(target);
            (origin?.isConnected ? origin : target.querySelector('summary'))?.focus();
        });
    });
    document.querySelectorAll('[data-share-dialog-open]').forEach((trigger) => {
        const target = document.getElementById(trigger.dataset.shareDialogOpen);
        if (!target || target.tagName !== 'DETAILS') return;
        trigger.setAttribute('aria-controls', target.id);
        trigger.setAttribute('aria-expanded', String(target.open));
        target.addEventListener('toggle', () => trigger.setAttribute('aria-expanded', String(target.open)));
        trigger.addEventListener('click', (event) => {
            event.preventDefault();
            target.open = true;
            origins.set(target, trigger);
            target.scrollIntoView({ block: 'nearest' });
            const control = target.querySelector('select') || target.querySelector('summary');
            control?.focus();
        });
    });
}());
