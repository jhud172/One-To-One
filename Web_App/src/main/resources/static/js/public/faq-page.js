(() => {
    const searchInput = document.getElementById('faq-search');
    const items = Array.from(document.querySelectorAll('[data-faq-item]'));
    const empty = document.getElementById('faq-empty');
    const clearButton = document.querySelector('[data-faq-clear]');
    const resultsStatus = document.querySelector('[data-faq-results]');
    const resultsLabel = resultsStatus ? resultsStatus.dataset.faqResultsLabel : '';
    const topics = Array.from(document.querySelectorAll('[data-faq-topic]'));
    let selectedTopic = 'all';
    let restoringState = false;
    const supportLink = document.querySelector('[data-faq-support]');
    function updateSupportContext() {
        if (!supportLink) return;
        const answer = items.find(item => item.open && !item.hidden);
        const context = answer?.querySelector('.guest-faq__question')?.textContent.trim() || searchInput.value.trim();
        const url = new URL('/support', window.location.origin);
        if (context) url.searchParams.set('subject', context.slice(0, 180));
        supportLink.href = url.pathname + url.search;
    }

    if (!searchInput || items.length === 0) {
        return;
    }

    const locale = document.documentElement.lang || 'en';
    const normalise = (value) => (value || '')
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .toLocaleLowerCase(locale)
        .trim();

    const updateResultsStatus = (shown) => {
        if (!resultsStatus) {
            return;
        }

        resultsStatus.textContent = shown === 0 && empty
            ? empty.textContent.trim()
            : [shown, items.length].join(' / ') + (resultsLabel ? ' · ' + resultsLabel : '');
    };

    const filterFaq = () => {
        const query = normalise(searchInput.value);
        let shown = 0;

        items.forEach((item) => {
            const match = (selectedTopic === 'all' || item.dataset.faqCategory === selectedTopic)
                && (query === '' || normalise(item.textContent).includes(query));
            item.hidden = !match;
            if (!match) {
                item.open = false;
            } else {
                shown += 1;
            }
        });

        if (empty) {
            empty.hidden = shown !== 0;
        }
        if (clearButton) {
            clearButton.hidden = query === '' && selectedTopic === 'all';
        }
        topics.forEach(topic => topic.setAttribute('aria-pressed', String(topic.dataset.faqTopic === selectedTopic)));
        updateResultsStatus(shown);
        updateSupportContext();
        if (!restoringState) {
            const url = new URL(window.location.href);
            if (searchInput.value.trim()) url.searchParams.set('q', searchInput.value.trim());
            else url.searchParams.delete('q');
            if (selectedTopic !== 'all') url.searchParams.set('topic', selectedTopic);
            else url.searchParams.delete('topic');
            if (url.hash && !items.some(item => '#' + item.id === url.hash && !item.hidden && item.open)) url.hash = '';
            history.replaceState(null, '', url);
        }
    };

    const clearSearch = () => {
        searchInput.value = '';
        selectedTopic = 'all';
        filterFaq();
        searchInput.focus();
    };

    searchInput.addEventListener('input', filterFaq);
    searchInput.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && searchInput.value !== '') {
            event.preventDefault();
            clearSearch();
        }
    });
    if (clearButton) {
        clearButton.addEventListener('click', clearSearch);
    }

    topics.forEach(topic => topic.addEventListener('click', () => {
        selectedTopic = topic.dataset.faqTopic;
        filterFaq();
    }));
    items.forEach(item => item.addEventListener('toggle', () => {
        if (restoringState) return;
        const url = new URL(window.location.href);
        if (item.open && !item.hidden) url.hash = item.id;
        else if (url.hash === '#' + item.id) url.hash = '';
        history.replaceState(null, '', url);
        updateSupportContext();
    }));
    function restoreState() {
        restoringState = true;
        const url = new URL(window.location.href);
        const answer = items.find(item => '#' + item.id === url.hash);
        searchInput.value = url.searchParams.get('q') || '';
        selectedTopic = topics.some(topic => topic.dataset.faqTopic === url.searchParams.get('topic')) ? url.searchParams.get('topic') : 'all';
        if (answer) {
            searchInput.value = '';
            selectedTopic = answer.dataset.faqCategory;
        }
        filterFaq();
        items.forEach(item => { item.open = item === answer; });
        updateSupportContext();
        restoringState = false;
    }
    window.addEventListener('popstate', restoreState);
    window.addEventListener('hashchange', restoreState);
    restoreState();
    if (topics.length) topics[0].parentElement.hidden = false;
})();
