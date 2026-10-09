(() => {
    'use strict';
    const studio = document.querySelector('.studio-v2');
    const form = studio?.querySelector('[data-studio-builder]');
    if (!form) return;
    const rows = form.querySelector('[data-studio-rows]');
    const template = form.querySelector('[data-studio-row-template]');
    const add = form.querySelector('[data-add-row]');
    const emptyMessage = form.querySelector('[data-empty-rows]');
    const status = studio.querySelector('[data-studio-status]');
    if (!rows || !template || !add) return;
    const format = (text, value) => (text || '').replace('{0}', String(value));
    let dirty = studio.dataset.draftChanged === 'true';
    studio.classList.add('is-enhanced');
    function reindex() {
        const items = [...rows.children];
        items.forEach((row, index) => {
            row.querySelector('[data-row-title]').textContent = format(studio.dataset.rowTitle, index + 1);
            row.querySelectorAll('[data-field]').forEach(field => {
                field.name = `exercises[${index}].${field.dataset.field}`;
                field.id = `studio-row-${index}-${field.dataset.field}`;
            });
            row.querySelectorAll('[data-for]').forEach(label => { label.htmlFor = `studio-row-${index}-${label.dataset.for}`; });
            const up = row.querySelector('[data-move-up]');
            const down = row.querySelector('[data-move-down]');
            const remove = row.querySelector('[data-remove-row]');
            up.value = `up:${index}`; up.disabled = index === 0;
            down.value = `down:${index}`; down.disabled = index === items.length - 1;
            remove.value = `remove:${index}`;
            remove.setAttribute('aria-label', format(studio.dataset.removeFor, index + 1));
        });
        add.disabled = items.length >= 50;
        if (emptyMessage) emptyMessage.hidden = items.length > 0;
    }
    function syncName(row) {
        const select = row.querySelector('[data-field="exerciseRef"]');
        if (select.value) row.querySelector('[data-field="exerciseName"]').value = select.selectedOptions[0].textContent.trim();
    }
    function addRow(ref) {
        const empty = [...rows.children].find(row => !row.querySelector('[data-field="exerciseRef"]').value
            && !row.querySelector('[data-field="exerciseName"]').value.trim() && !row.querySelector('[data-field="notes"]').value.trim());
        if (rows.children.length >= 50 && !(ref && empty)) {
            if (status) status.textContent = studio.dataset.limits;
            return false;
        }
        const row = ref && empty ? empty : template.content.firstElementChild.cloneNode(true);
        if (!row.isConnected) rows.append(row);
        reindex();
        if (ref) { row.querySelector('[data-field="exerciseRef"]').value = ref; syncName(row); }
        dirty = true;
        row.querySelector(ref ? '[data-field="sets"]' : '[data-field="exerciseName"]').focus();
        return true;
    }
    [...rows.children].forEach(syncName);
    reindex();
    add.addEventListener('click', event => { event.preventDefault(); addRow(); });
    form.addEventListener('studio:add-exercise', event => {
        if (!addRow(event.detail.ref)) event.preventDefault();
    });
    form.addEventListener('input', () => { dirty = true; });
    rows.addEventListener('change', event => {
        dirty = true;
        if (event.target.matches('[data-field="exerciseRef"]')) syncName(event.target.closest('[data-builder-row]'));
    });
    rows.addEventListener('click', event => {
        const button = event.target.closest('button');
        if (!button || !button.matches('[data-remove-row],[data-move-up],[data-move-down]')) return;
        event.preventDefault();
        const row = button.closest('[data-builder-row]');
        if (button.hasAttribute('data-remove-row')) {
            const next = row.nextElementSibling || row.previousElementSibling;
            row.remove(); reindex();
            (next?.querySelector('[data-field="exerciseName"]') || add).focus();
        } else if (button.hasAttribute('data-move-up') && row.previousElementSibling) {
            rows.insertBefore(row, row.previousElementSibling); reindex();
            (button.disabled ? row.querySelector('[data-field="exerciseName"]') : button).focus();
        } else if (button.hasAttribute('data-move-down') && row.nextElementSibling) {
            rows.insertBefore(row.nextElementSibling, row); reindex();
            (button.disabled ? row.querySelector('[data-field="exerciseName"]') : button).focus();
        } else return;
        dirty = true;
    });
    form.addEventListener('submit', () => { reindex(); dirty = false; });
    window.addEventListener('beforeunload', event => {
        if (dirty) { event.preventDefault(); event.returnValue = ''; }
    });
})();
