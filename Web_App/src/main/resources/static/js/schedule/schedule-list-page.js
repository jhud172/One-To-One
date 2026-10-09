(() => {
    const root = document.querySelector('.schedule-centre');
    const search = root?.querySelector('[data-schedule-search]');
    const input = search?.querySelector('input');
    if (!root || !search || !input) return;
    const rows = Array.from(root.querySelectorAll('[data-schedule-name]'));
    const matches = search.querySelector('[data-schedule-matches]');
    const empty = root.querySelector('#schedule-search-empty');
    search.hidden = false;
    const filter = () => {
        const term = input.value.trim().toLocaleLowerCase(document.documentElement.lang || undefined);
        let count = 0;
        rows.forEach(row => {
            row.hidden = !row.dataset.scheduleName.toLocaleLowerCase(document.documentElement.lang || undefined).includes(term);
            if (!row.hidden) count++;
        });
        if (matches) matches.textContent = matches.dataset.matchMessage.replace('{0}', String(count));
        if (empty) empty.hidden = !term || count > 0;
    };
    input.addEventListener('input', filter);
    filter();
})();
