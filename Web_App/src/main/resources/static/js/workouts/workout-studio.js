(() => {
    'use strict';
    const studio = document.querySelector('.studio-v2');
    if (!studio) return;
    studio.classList.add('is-enhanced');
    const form = studio.querySelector('[data-studio-builder]');
    const template = studio.querySelector('[data-studio-row-template]');
    const status = studio.querySelector('[data-studio-status]');
    const tabs = [...studio.querySelectorAll('[data-mode-tab]')];
    const format = (text, value) => (text || '').replace('{0}', String(value));
    const announce = (message, error = false) => { status.textContent = message; status.classList.toggle('studio-v2__status--error', error); };
    function mode(value, focus = false) {
        tabs.forEach(tab => { const active = tab.dataset.modeTab === value; tab.setAttribute('aria-selected', String(active)); tab.tabIndex = active ? 0 : -1; });
        studio.querySelectorAll('[data-mode-panel]').forEach(panel => { panel.hidden = panel.dataset.modePanel !== value; });
        if (focus) tabs.find(tab => tab.dataset.modeTab === value)?.focus();
    }
    mode(studio.dataset.mode);
    tabs.forEach((tab, index) => {
        tab.addEventListener('click', event => { event.preventDefault(); mode(tab.dataset.modeTab); });
        tab.addEventListener('keydown', event => {
            if (event.ctrlKey || event.metaKey || event.altKey) return;
            const rtl = document.documentElement.dir === 'rtl'; let next;
            if (event.key === 'ArrowRight') next = index + (rtl ? -1 : 1);
            if (event.key === 'ArrowLeft') next = index + (rtl ? 1 : -1);
            if (event.key === 'Home') next = 0;
            if (event.key === 'End') next = tabs.length - 1;
            if (next === undefined) return;
            event.preventDefault(); mode(tabs[(next + tabs.length) % tabs.length].dataset.modeTab, true);
        });
    });
    studio.querySelectorAll('[data-open-builder], [data-open-library]').forEach(link => link.addEventListener('click', event => { event.preventDefault(); mode(link.hasAttribute('data-open-builder') ? 'builder' : 'library', true); }));
    studio.addEventListener('click', event => {
        const link = event.target.closest('[data-add-ref]');
        if (!link) return;
        event.preventDefault(); mode('builder');
        const card = link.closest('[data-library-card]');
        if (form.dispatchEvent(new CustomEvent('studio:add-exercise', {detail:{ref:card.dataset.ref},cancelable:true}))) {
            announce(format(studio.dataset.added, card.dataset.name));
        }
    });
    function filter(search, selector, empty, category = '') {
        let count = 0; const query = search.trim().toLocaleLowerCase();
        studio.querySelectorAll(selector).forEach(card => { card.hidden = !((card.dataset.search || `${card.dataset.name} ${card.dataset.category}`).toLocaleLowerCase().includes(query) && (!category || card.dataset.category === category)); if (!card.hidden) count++; });
        studio.querySelector(empty).hidden = count > 0 || !studio.querySelector(selector);
    }
    studio.querySelector('[data-workout-search]').addEventListener('input', event => filter(event.target.value, '[data-workout-card]', '[data-workout-no-matches]'));
    const search = studio.querySelector('[data-library-search]'); const category = studio.querySelector('[data-library-category]');
    const filterLibrary = () => filter(search.value, '[data-library-card]', '[data-library-no-matches]', category.value);
    search.addEventListener('input', filterLibrary); category.addEventListener('change', filterLibrary);
    async function request(url, options = {}) {
        const controller = new AbortController(); const timeout = setTimeout(() => controller.abort(), 15000);
        try {
            const headers = {Accept:'application/json', ...options.headers};
            if (options.method && options.method !== 'GET') headers[document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN'] = document.querySelector('meta[name="_csrf"]')?.content || '';
            const response = await fetch(url, {...options, headers, signal:controller.signal});
            if (!response.ok || response.redirected) throw new Error('Request not confirmed'); return response;
        } finally { clearTimeout(timeout); }
    }
    const favourites = new Set(); let favouritesLoaded = false;
    function favouriteState(button) { const selected = favourites.has(button.dataset.favouriteId); button.textContent = selected ? '★' : '☆'; button.setAttribute('aria-pressed', String(selected)); button.setAttribute('aria-label', `${selected ? studio.dataset.unfavourite : studio.dataset.favourite}: ${button.closest('[data-library-card]').dataset.name}`); }
    async function loadFavourites() {
        const result = await (await request('/api/favourites')).json(); if (!Array.isArray(result)) throw new Error('Invalid favourites');
        favourites.clear(); result.forEach(item => { if (item.exerciseId != null) favourites.add(String(item.exerciseId)); });
        favouritesLoaded = true; studio.querySelectorAll('[data-favourite-id]').forEach(favouriteState);
    }
    loadFavourites().catch(() => announce(studio.dataset.favouriteFailed, true));
    studio.querySelectorAll('[data-favourite-id]').forEach(button => button.addEventListener('click', async () => {
        if (button.disabled) return; button.disabled = true;
        try {
            if (!favouritesLoaded) await loadFavourites(); const id = button.dataset.favouriteId; const selected = favourites.has(id);
            await request(selected ? `/api/favourites/${id}` : '/api/favourites', selected ? {method:'DELETE'} : {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({exerciseId:Number(id)})});
            if (selected) favourites.delete(id); else favourites.add(id); favouriteState(button);
        } catch { announce(studio.dataset.favouriteFailed, true); } finally { button.disabled = false; }
    }));
    const dialog = studio.querySelector('[data-custom-dialog]'); const customForm = studio.querySelector('[data-custom-form]'); const customStatus = dialog.querySelector('[data-custom-status]');
    studio.querySelector('[data-open-custom-exercise]').addEventListener('click', () => dialog.showModal());
    dialog.querySelector('[data-close-custom]').addEventListener('click', () => dialog.close());
    customForm.addEventListener('submit', async event => {
        event.preventDefault(); const save = customForm.querySelector('[type="submit"]'); if (save.disabled) return; save.disabled = true; customStatus.textContent = '';
        try {
            const saved = await (await request('/workout/custom-exercises', {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(Object.fromEntries(new FormData(customForm)))})).json();
            if (!Number.isSafeInteger(saved.id) || saved.id <= 0 || typeof saved.name !== 'string') throw new Error('Invalid exercise');
            const ref = `c:${saved.id}`;
            [...studio.querySelectorAll('select[data-field="exerciseRef"]'), template.content.querySelector('select[data-field="exerciseRef"]')].forEach(select => { const option = document.createElement('option'); option.value = ref; option.textContent = saved.name; select.lastElementChild.append(option); });
            const card = document.createElement('li'); card.dataset.libraryCard = ''; card.dataset.ref = ref; card.dataset.name = saved.name; card.dataset.category = '';
            const article = document.createElement('article'); const title = document.createElement('h3'); title.textContent = saved.name;
            const link = document.createElement('a'); link.className = 'training-launch__action'; link.dataset.addRef = ''; link.href = `/workouts?mode=builder&exerciseRef=${encodeURIComponent(ref)}`;
            link.textContent = studio.querySelector('[data-add-ref]')?.textContent || saved.name; link.setAttribute('aria-label', format(studio.dataset.addFor, saved.name));
            article.append(title,link); card.append(article); studio.querySelector('.studio-v2__library').append(card);
            filterLibrary(); customForm.reset(); dialog.close(); announce(studio.dataset.customSaved);
        } catch { customStatus.textContent = studio.dataset.customFailed; } finally { save.disabled = false; }
    });
})();
