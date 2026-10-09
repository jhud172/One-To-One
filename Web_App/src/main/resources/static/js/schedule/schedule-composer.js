(() => {
    'use strict';
    const form = document.getElementById('schedule-composer-form');
    if (!form) return;
    const rows = form.querySelector('[data-composer-rows]');
    const undo = form.querySelector('[data-composer-undo]');
    const status = form.querySelector('[data-composer-status]');
    const history = [];
    function refresh() {
        const items = [...rows.children];
        items.forEach((row, index) => {
            row.querySelector('[data-row-index]').textContent = String(index + 1);
            for (const field of ['day', 'workout']) {
                const id = `composer-${field}-${index}`;
                row.querySelector(`[data-${field}-select]`).id = id;
                row.querySelector(`[data-${field}-label]`).htmlFor = id;
            }
            row.querySelectorAll('[data-row-command]').forEach(button => {
                const command = button.dataset.rowCommand;
                button.value = `${command}:${index}`;
                button.setAttribute('aria-label', `${button.textContent.trim()}: ${row.querySelector('[data-row-heading]').textContent}`);
                button.disabled = command === 'up' ? index === 0 : command === 'down' ? index === items.length - 1 : false;
            });
        });
        undo.hidden = history.length === 0;
        const empty = form.querySelector('[data-composer-empty]');
        if (empty) empty.hidden = items.length !== 0;
        status.textContent = status.dataset.message;
    }
    function snapshot() {
        // Select values are properties, so explicitly retain them in cloned rows.
        history.push([...rows.children].map(row => {
            const clone = row.cloneNode(true);
            row.querySelectorAll('select').forEach((select, index) => { clone.querySelectorAll('select')[index].value = select.value; });
            return clone;
        }));
        if (history.length > 20) history.shift();
    }
    form.addEventListener('click', event => {
        const button = event.target.closest('[data-row-command]');
        if (!button || button.disabled) return;
        event.preventDefault();
        const row = button.closest('.schedule-placement');
        const index = [...rows.children].indexOf(row);
        snapshot();
        if (button.dataset.rowCommand === 'up') rows.insertBefore(row, row.previousElementSibling);
        else if (button.dataset.rowCommand === 'down') rows.insertBefore(row.nextElementSibling, row);
        else row.remove();
        refresh();
        const target = row.isConnected ? row : rows.children[Math.min(index, rows.children.length - 1)];
        if (target) { target.classList.add('is-changed'); target.querySelector('[data-row-heading]').focus(); }
        else document.getElementById('composer-add-day').focus();
    });
    undo.addEventListener('click', () => {
        const previous = history.pop();
        if (!previous) return;
        rows.replaceChildren(...previous);
        refresh();
        const heading = rows.querySelector('[data-row-heading]');
        if (heading) heading.focus();
    });
    form.addEventListener('change', event => {
        const select = event.target.closest('[data-workout-select]');
        if (select) {
            select.closest('.schedule-placement').querySelector('[data-row-heading]').textContent = select.selectedOptions[0].textContent;
            refresh();
        }
    });
})();
