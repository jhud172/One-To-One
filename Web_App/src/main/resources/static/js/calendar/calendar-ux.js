function initCalendarUx() {
    if (window.__calendarUxInit) return;
    window.__calendarUxInit = true;
    const scheduleButton = document.getElementById("scheduleDrawerButton");
    const scheduleDrawer = document.getElementById("scheduleDrawer");
    const scheduleSearch = document.getElementById("schedule-search");
    const scheduleList = document.getElementById("schedule-list");

    const contextSummary = document.getElementById('schedule-context-summary');
    const contextMeta = document.getElementById('schedule-context-meta');
    const deployStartInput = document.getElementById('schedule-deploy-start');
    const repeatSelect = document.getElementById('schedule-deploy-repeat');
    const repeatCustomRow = document.getElementById('schedule-repeat-custom');
    const repeatIntervalInput = document.getElementById('schedule-repeat-interval');
    const repeatUnitSelect = document.getElementById('schedule-repeat-unit');
    const repeatEndInput = document.getElementById('schedule-repeat-end');
    const repeatEndRow = document.getElementById('schedule-repeat-end-row');

    const previewToggle = document.getElementById('schedule-preview-toggle');
    const previewClearButton = document.getElementById('schedule-preview-clear');
    const strategyButtons = Array.from(scheduleDrawer?.querySelectorAll('[data-deploy-strategy]') || []);
    const impactSummary = document.getElementById('schedule-impact-summary');
    const impactReviewButton = document.getElementById('schedule-impact-review');
    const impactApplyButton = document.getElementById('schedule-impact-apply');
    const previewDeployButton = document.getElementById('schedule-preview-deploy');
    const simEnableToggle = document.getElementById('schedule-sim-enable');
    const simWeeksInput = document.getElementById('schedule-sim-weeks');
    const simRunButton = document.getElementById('schedule-sim-run');
    const simToggleButton = document.getElementById('schedule-sim-toggle');
    const simPanel = document.getElementById('schedule-sim-panel');
    const deployPanel = scheduleDrawer?.querySelector('[data-deploy-panel]') || null;
    const deployFooter = scheduleDrawer?.querySelector('[data-deploy-footer]') || null;
    const deployCancelButton = document.getElementById('schedule-deploy-cancel');
    const previewConfirmBar = document.getElementById('schedule-preview-confirm');
    const previewConfirmName = document.getElementById('schedule-preview-confirm-name');
    const previewConfirmApply = document.getElementById('schedule-preview-confirm-apply');
    const previewConfirmCancel = document.getElementById('schedule-preview-confirm-cancel');

    const filterType = document.getElementById('schedule-filter-type');
    const filterStatus = document.getElementById('schedule-filter-status');
    const filterFrequency = document.getElementById('schedule-filter-frequency');
    const filterFavourites = document.getElementById('schedule-filter-favourites');
    const filterRecent = document.getElementById('schedule-filter-recent');
    const sortSelect = document.getElementById('schedule-sort');
    const scopeButtons = Array.from(scheduleDrawer?.querySelectorAll('[data-schedule-scope]') || []);
    const customScopeRow = scheduleDrawer?.querySelector('[data-scope-custom]') || null;

    const pinnedList = document.getElementById('schedule-pinned-list');
    const recentList = document.getElementById('schedule-recent-list');

    const csrfTokenMeta = document.querySelector('meta[name="_csrf"]');
    const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
    const csrfToken = csrfTokenMeta?.content;
    const csrfHeader = csrfHeaderMeta?.content || 'X-CSRF-TOKEN';

    const cardById = new Map();
    const scheduleCards = Array.from(scheduleDrawer?.querySelectorAll('.calendar-schedule-card[data-schedule-id], .calendar-schedule-card-compact[data-schedule-id], .sched-card[data-schedule-id]') || []);
    scheduleCards.forEach((card) => {
        const id = card.getAttribute('data-schedule-id');
        if (id) cardById.set(id, card);
    });

    let selectedDay = null;
    let pendingPreviewPayload = null;
    let pendingPreviewScheduleId = null;
    let selectedScheduleId = null;
    let selectedStrategy = 'merge';
    let previewEnabled = true;
    const favouriteStorageKey = 'calendar.scheduleDrawer.favourites';
    const pinnedStorageKey = 'calendar.scheduleDrawer.pinned';
    const recentStorageKey = 'calendar.scheduleDrawer.recent';
    let draggedScheduleId = null;
    let lastFocusedElement = null;
    let undoTimeout = null;
    let searchFilterTimer = null;
    let searchQuery = '';
    let selectedScope = 'visible';
    const scheduleMetadataCache = new Map();
    let impactRevision = 0;
    let reviewedDeployment = null;
    let deploymentInFlight = false;

    function deploymentMessage(key) { return scheduleDrawer?.dataset[key] || ""; }

    function getCurrentPane() {
        return document.querySelector('[data-pane-center="true"]')
            || document.querySelector('[data-month-pane]')
            || document.querySelector('[data-week-pane-slot="current"] [data-week-pane]')
            || document;
    }

    function getVisibleDateBounds() {
        const pane = getCurrentPane();
        const dates = Array.from(pane.querySelectorAll('.calendar-day-card[data-date]'))
            .map((card) => card.getAttribute('data-date'))
            .filter(Boolean)
            .sort();

        if (!dates.length) return null;
        return { start: dates[0], end: dates[dates.length - 1] };
    }

    function toLocalIsoDate(date) {
        return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
    }

    function parseDate(dateIso) {
        if (!dateIso) return null;
        const date = new Date(`${dateIso}T00:00:00`);
        if (Number.isNaN(date.getTime()) || toLocalIsoDate(date) !== dateIso) return null;
        return date;
    }

    function getIsoDayNumber(dateIso) {
        const date = parseDate(dateIso);
        if (!date) return null;
        const jsDay = date.getDay();
        return jsDay === 0 ? 7 : jsDay;
    }

    function formatDateLabel(dateIso) {
        const date = parseDate(dateIso);
        if (!date) return dateIso || '--';
        return date.toLocaleDateString(document.documentElement.lang || 'en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
    }

    function clearPreview() {
        document.querySelectorAll('.calendar-preview-ghost').forEach((entry) => entry.remove());
        document.querySelectorAll('.calendar-day-card--preview-conflict').forEach((card) => {
            card.classList.remove('calendar-day-card--preview-conflict');
        });
        document.querySelectorAll('.calendar-day-card--simulated').forEach((card) => {
            card.classList.remove('calendar-day-card--simulated');
        });
    }

    function getListItemForSchedule(scheduleId) {
        return scheduleList?.querySelector(`li[data-schedule-id="${scheduleId}"]`) || null;
    }

    function loadSet(storageKey) {
        try {
            return new Set(JSON.parse(localStorage.getItem(storageKey) || '[]'));
        } catch {
            return new Set();
        }
    }

    function saveSet(storageKey, values) {
        try { localStorage.setItem(storageKey, JSON.stringify(Array.from(values))); } catch { /* Selection remains usable when storage is unavailable. */ }
    }

    function loadRecent() {
        try {
            const parsed = JSON.parse(localStorage.getItem(recentStorageKey) || '[]');
            return Array.isArray(parsed) ? parsed : [];
        } catch {
            return [];
        }
    }

    function saveRecent(values) {
        try { localStorage.setItem(recentStorageKey, JSON.stringify(values.slice(0, 12))); } catch { /* Selection remains usable when storage is unavailable. */ }
    }

    function trackRecent(scheduleId) {
        if (!scheduleId) return;
        const next = loadRecent().filter((id) => id !== scheduleId);
        next.unshift(scheduleId);
        saveRecent(next);
    }

    function typeLabelToFilterValue(typeRaw) {
        const value = (typeRaw || '').toLowerCase();
        if (value.includes('custom')) return 'custom';
        if (value.includes('rotation')) return 'rotational';
        return 'weekly';
    }

    function applyFiltersAndSort() {
        if (!scheduleList) return;
        const lis = Array.from(scheduleList.querySelectorAll('li[data-schedule-id]'));
        const favourites = loadSet(favouriteStorageKey);
        const pinned = loadSet(pinnedStorageKey);
        const recent = new Set(loadRecent());

        const selectedType = filterType?.value || 'all';
        const selectedStatus = filterStatus?.value || 'all';
        const selectedFrequency = filterFrequency?.value || 'all';
        const onlyFavourites = !!filterFavourites?.checked;
        const onlyRecent = !!filterRecent?.checked;

        lis.forEach((li) => {
            const scheduleId = li.getAttribute('data-schedule-id') || '';
            const card = li.querySelector('.calendar-schedule-card, .calendar-schedule-card-compact, .sched-card');
            const status = (li.getAttribute('data-schedule-status') || '').toLowerCase();
            const type = typeLabelToFilterValue(li.getAttribute('data-schedule-type'));
            const frequencyBucket = card?.getAttribute('data-frequency-bucket') || 'all';
            const name = (li.getAttribute('data-schedule-name') || '').toLowerCase();
            const matchSearch = !searchQuery || name.includes(searchQuery);

            const matchType = selectedType === 'all' || selectedType === type;
            const matchStatus = selectedStatus === 'all' || selectedStatus === status;
            const matchFrequency = selectedFrequency === 'all' || selectedFrequency === frequencyBucket;
            const matchFavourite = !onlyFavourites || favourites.has(scheduleId);
            const matchRecent = !onlyRecent || recent.has(scheduleId);

            li.style.display = (matchType && matchStatus && matchFrequency && matchFavourite && matchRecent && matchSearch) ? '' : 'none';

            const title = li.querySelector('.calendar-schedule-card-title');
            if (title) {
                const base = li.getAttribute('data-schedule-name') || title.textContent || '';
                if (searchQuery && base.toLowerCase().includes(searchQuery)) {
                    const escaped = searchQuery.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
                    const parts = base.split(new RegExp(`(${escaped})`, 'ig'));
                    title.replaceChildren(...parts.map((part, index) => {
                        if (index % 2 === 0) return document.createTextNode(part);
                        const mark = document.createElement('mark');
                        mark.textContent = part;
                        return mark;
                    }));
                } else {
                    title.textContent = base;
                }
            }
        });

        const sortMode = sortSelect?.value || 'recentlyApplied';
        const sorted = lis.slice().sort((a, b) => {
            const aPinned = pinned.has(a.getAttribute('data-schedule-id') || '');
            const bPinned = pinned.has(b.getAttribute('data-schedule-id') || '');
            if (aPinned !== bPinned) return aPinned ? -1 : 1;

            if (sortMode === 'mostUsed') {
                return Number(b.getAttribute('data-schedule-apply-count') || 0) - Number(a.getAttribute('data-schedule-apply-count') || 0);
            }

            if (sortMode === 'recentlyCreated') {
                return Number(b.getAttribute('data-schedule-id') || 0) - Number(a.getAttribute('data-schedule-id') || 0);
            }

            if (sortMode === 'name') {
                return (a.getAttribute('data-schedule-name') || '').localeCompare(b.getAttribute('data-schedule-name') || '');
            }

            const aDate = a.getAttribute('data-schedule-latest-applied') || '';
            const bDate = b.getAttribute('data-schedule-latest-applied') || '';
            return bDate.localeCompare(aDate);
        });

        sorted.forEach((li) => scheduleList.appendChild(li));

        const visibleCount = lis.filter((li) => li.style.display !== 'none').length;
        let empty = scheduleList.querySelector('[data-empty-results]');
        if (!visibleCount) {
            if (!empty) {
                empty = document.createElement('li');
                empty.className = 'calendar-schedule-empty';
                empty.setAttribute('data-empty-results', 'true');
                scheduleList.appendChild(empty);
            }
            empty.textContent = searchQuery
                ? 'No matching schedules. Try a different search or filter.'
                : (selectedStatus === 'archived' ? 'All schedules in this view are archived.' : 'No schedules for this filter yet.');
        } else if (empty) {
            empty.remove();
        }
    }

    function renderQuickAccessChips() {
        if (!pinnedList || !recentList) return;
        const pinned = Array.from(loadSet(pinnedStorageKey));
        const recent = loadRecent();

        const renderInto = (container, ids, emptyText) => {
            container.innerHTML = '';
            const availableIds = ids.filter((id) => !!getListItemForSchedule(id));
            if (!availableIds.length) {
                const empty = document.createElement('span');
                empty.className = 'calendar-quick-chip-empty';
                empty.textContent = emptyText;
                container.appendChild(empty);
                return;
            }

            availableIds.slice(0, 8).forEach((id) => {
                const li = getListItemForSchedule(id);
                if (!li) return;
                const name = li.getAttribute('data-schedule-name') || `Schedule ${id}`;
                const chip = document.createElement('button');
                chip.type = 'button';
                chip.className = 'calendar-quick-chip';
                chip.textContent = name;
                chip.addEventListener('click', () => {
                    activateSchedule(id, true).catch((error) => console.error(error));
                });
                container.appendChild(chip);
            });
        };

        renderInto(pinnedList, pinned, 'No pinned schedules');
        renderInto(recentList, recent, 'No recent schedules');
    }

    function applyPinnedUi() {
        const pinned = loadSet(pinnedStorageKey);
        scheduleDrawer?.querySelectorAll('[data-pin-toggle]').forEach((button) => {
            const id = button.getAttribute('data-schedule-id') || '';
            const isPinned = pinned.has(id);
            button.classList.toggle('is-pinned', isPinned);
            button.setAttribute('aria-pressed', String(isPinned));
        });
    }

    function togglePin(scheduleId) {
        if (!scheduleId) return;
        const pinned = loadSet(pinnedStorageKey);
        if (pinned.has(scheduleId)) pinned.delete(scheduleId);
        else pinned.add(scheduleId);
        saveSet(pinnedStorageKey, pinned);
        applyPinnedUi();
        renderQuickAccessChips();
    }

    function clearDropTargets() {
        document.querySelectorAll('.calendar-day-card--drop-target, .calendar-day-card--drop-hover').forEach((card) => {
            card.classList.remove('calendar-day-card--drop-target', 'calendar-day-card--drop-hover');
        });
    }

    function showDropTargets() {
        getCurrentPane().querySelectorAll('.calendar-day-card[data-date]').forEach((card) => {
            card.classList.add('calendar-day-card--drop-target');
        });
    }

    function bindDragToApply() {
        const handles = scheduleDrawer?.querySelectorAll('[data-schedule-drag]') || [];
        handles.forEach((handle) => {
            handle.addEventListener('dragstart', (event) => {
                const scheduleId = handle.getAttribute('data-schedule-id');
                if (!scheduleId) return;
                draggedScheduleId = scheduleId;

                event.dataTransfer.effectAllowed = 'copy';
                event.dataTransfer.setData('text/schedule-id', scheduleId);

                const dragBadge = document.createElement('div');
                dragBadge.className = 'calendar-drag-badge';
                dragBadge.textContent = 'Deploy schedule';
                document.body.appendChild(dragBadge);
                event.dataTransfer.setDragImage(dragBadge, 40, 18);
                setTimeout(() => dragBadge.remove(), 0);

                showDropTargets();
            });

            handle.addEventListener('dragend', () => {
                draggedScheduleId = null;
                clearDropTargets();
            });
        });

        document.addEventListener('dragover', (event) => {
            if (!draggedScheduleId || !scheduleDrawer?.classList.contains('open')) return;
            const dayCard = event.target.closest('.calendar-day-card[data-date]');
            if (!dayCard) return;
            event.preventDefault();
            dayCard.classList.add('calendar-day-card--drop-hover');
        });

        document.addEventListener('dragleave', (event) => {
            const dayCard = event.target.closest?.('.calendar-day-card[data-date]');
            if (!dayCard) return;
            dayCard.classList.remove('calendar-day-card--drop-hover');
        });

        document.addEventListener('drop', (event) => {
            if (!draggedScheduleId || !scheduleDrawer?.classList.contains('open')) return;
            const dayCard = event.target.closest('.calendar-day-card[data-date]');
            if (!dayCard) return;
            event.preventDefault();
            event.stopPropagation();

            selectedDay = dayCard.getAttribute('data-date');
            if (deployStartInput && selectedDay) {
                deployStartInput.value = selectedDay;
            }
            if (repeatSelect) {
                repeatSelect.value = 'weekly';
            }
            updateRepeatVisibility();

            activateSchedule(draggedScheduleId, true)
                .then(() => requestImpact())
                .then(() => {
                    const config = computeRepeatConfig();
                    const confirmApply = window.confirm(`Deploy schedule starting ${formatDateLabel(config.startDate)} (${config.repeat})?`);
                    if (confirmApply) {
                        return applyDeployment();
                    }
                    return null;
                })
                .catch((error) => {
                    console.error(error);
                    if (impactSummary) {
                        impactSummary.textContent = 'Drop deployment failed. Please retry from controls.';
                    }
                })
                .finally(() => {
                    clearDropTargets();
                    draggedScheduleId = null;
                });
        }, true);
    }

    function clearConflictHighlights() {
        document.querySelectorAll('.calendar-day-card--deploy-conflict').forEach((card) => {
            card.classList.remove('calendar-day-card--deploy-conflict');
        });
    }

    function updateScheduleSelectionUi() {
        scheduleCards.forEach((card) => {
            const isActive = card.getAttribute('data-schedule-id') === selectedScheduleId;
            card.classList.toggle('is-selected', isActive);
        });

        const enabled = !!selectedScheduleId;
        if (impactReviewButton) impactReviewButton.disabled = !enabled || deploymentInFlight;
        if (impactApplyButton) {
            impactApplyButton.disabled = deploymentInFlight || !enabled || impactApplyButton.dataset.hasImpact !== 'true';
            impactApplyButton.textContent = enabled ? `Deploy "${getSelectedScheduleName()}"` : 'Deploy schedule';
        }
        if (previewDeployButton) {
            previewDeployButton.disabled = !enabled;
        }
        if (deployPanel) {
            deployPanel.classList.toggle('is-hidden', !enabled);
            deployPanel.hidden = !enabled;
        }
        if (deployFooter) {
            deployFooter.classList.toggle('is-hidden', !enabled);
            deployFooter.hidden = !enabled;
        }
        if (enabled) {
            updateContextHeader();
        }
        if (simRunButton) simRunButton.disabled = !enabled || !simEnableToggle?.checked;
    }

    function getSelectedScheduleName() {
        if (!selectedScheduleId) return 'schedule';
        const li = getListItemForSchedule(selectedScheduleId);
        return li?.getAttribute('data-schedule-name') || 'schedule';
    }

    function isPremiumUser() {
        return scheduleDrawer?.dataset.isPremium === 'true';
    }

    function showPreviewConfirm() {
        if (!previewConfirmBar) return;
        if (previewConfirmName) previewConfirmName.textContent = getSelectedScheduleName();
        previewConfirmBar.classList.remove('hidden');
    }

    function hidePreviewConfirm() {
        if (!previewConfirmBar) return;
        previewConfirmBar.classList.add('hidden');
    }

    function markScheduleDeployed(scheduleId) {
        const card = cardById.get(scheduleId || '') || null;
        if (!card) return;
        let badge = card.querySelector('[data-deploy-status]');
        if (!badge) {
            badge = document.createElement('span');
            badge.setAttribute('data-deploy-status', 'true');
            badge.className = 'sched-deploy-status';
            badge.textContent = 'Deployed just now';
            const header = card.querySelector('.sched-card-header');
            header?.appendChild(badge);
            return;
        }
        badge.textContent = 'Deployed just now';
        badge.classList.remove('hidden');
    }

    function renderHealthIndicators(card, metadata) {
        const container = card.querySelector('[data-health-indicators]');
        if (!container) return;
        container.innerHTML = '';

        const warnings = Array.isArray(metadata?.healthWarnings) ? metadata.healthWarnings : [];
        if (!warnings.length) return;

        warnings.forEach((warning) => {
            const badge = document.createElement('span');
            badge.className = 'calendar-health-warning';
            badge.title = warning.description || warning.label || 'Schedule warning';
            badge.textContent = warning.label || 'Warning';
            container.appendChild(badge);
        });
    }

    function renderDeploymentPreview(impact) {
        clearPreview();
        if (!previewEnabled) return;
        const byDate = new Map();
        (impact.plannedOccurrences || []).forEach((occurrence) => {
            if (!byDate.has(occurrence.date)) byDate.set(occurrence.date, []);
            byDate.get(occurrence.date).push(occurrence.name);
        });
        getCurrentPane().querySelectorAll('.calendar-day-card[data-date]').forEach((card) => {
            const names = byDate.get(card.dataset.date) || [];
            const content = card.querySelector('.calendar-day-content');
            if (!content || !names.length) return;
            const ghost = document.createElement('div');
            ghost.className = 'calendar-preview-ghost';
            ghost.textContent = names.slice(0, 2).join(' | ') + (names.length > 2 ? ` +${names.length - 2}` : '');
            content.appendChild(ghost);
        });
    }

    function renderProjectedLayout(scheduleId, impact) {
        if (!impact || !scheduleId) return;
        renderDeploymentPreview(impact);
        clearConflictHighlights();
        getCurrentPane().querySelectorAll('.calendar-preview-ghost').forEach((ghost) => {
            ghost.classList.add('calendar-preview-ghost--sim');
            ghost.closest('.calendar-day-card')?.classList.add('calendar-day-card--simulated');
        });

        const conflictDates = Array.isArray(impact.conflictDates) ? impact.conflictDates : [];
        conflictDates.forEach((dateIso) => {
            const card = document.querySelector(`.calendar-day-card[data-date="${dateIso}"]`);
            if (card) card.classList.add('calendar-day-card--deploy-conflict');
        });
    }

    async function activateSchedule(scheduleId, withPreview = true, withImpact = false) {
        if (!scheduleId) return;
        selectedScheduleId = scheduleId;
        invalidateImpact();
        trackRecent(scheduleId);
        updateScheduleSelectionUi();
        renderQuickAccessChips();
        if (withPreview || withImpact) return requestImpact();
    }

    function weeksBetween(startIso, endIso) {
        const start = parseDate(startIso);
        const end = parseDate(endIso);
        if (!start || !end) return 4;
        const diffDays = Math.max(1, Math.round((end - start) / 86400000) + 1);
        return Math.ceil(diffDays / 7);
    }

    function computeRepeatConfig() {
        const bounds = getVisibleDateBounds();
        const fallback = selectedDay || bounds?.start || toLocalIsoDate(new Date());
        const startDate = selectedScope === 'visible' ? bounds?.start || fallback
            : selectedScope === 'day' ? fallback : deployStartInput?.value || '';
        const endDate = selectedScope === 'visible' ? bounds?.end || startDate
            : selectedScope === 'day' ? startDate : repeatEndInput?.value || '';
        return { startDate, endDate, repeat: repeatSelect?.value || 'weekly', scope: 'weeks',
            weeks: startDate && endDate ? weeksBetween(startDate, endDate) : 1 };
    }

    function syncScopeInputs() {
        const config = computeRepeatConfig();
        if (selectedScope !== 'custom') {
            if (deployStartInput) deployStartInput.value = config.startDate;
            if (repeatEndInput) repeatEndInput.value = config.endDate;
        }
        if (deployStartInput) deployStartInput.readOnly = selectedScope === 'visible';
        if (repeatEndInput) repeatEndInput.readOnly = selectedScope !== 'custom';
        scopeButtons.forEach((button) => {
            const active = button.dataset.scheduleScope === selectedScope;
            button.classList.toggle('is-active', active);
            button.setAttribute('aria-pressed', String(active));
        });
    }

    function validDeploymentWindow() {
        const config = computeRepeatConfig();
        const start = parseDate(config.startDate);
        const end = parseDate(config.endDate);
        const days = start && end ? Math.round((Date.UTC(end.getFullYear(), end.getMonth(), end.getDate())
            - Date.UTC(start.getFullYear(), start.getMonth(), start.getDate())) / 86400000) + 1 : 0;
        const interval = Number(repeatIntervalInput?.value || 1);
        return days >= 1 && days <= 366 && (config.repeat !== 'custom'
            || (Number.isInteger(interval) && interval >= 1 && interval <= 52));
    }

    function buildDeploymentPayload() {
        const config = computeRepeatConfig();
        return { startDate: config.startDate, selectedDate: config.startDate, strategy: selectedStrategy,
            recurrence: { repeat: config.repeat,
                interval: config.repeat === 'custom' ? Number(repeatIntervalInput?.value || 1) : null,
                unit: config.repeat === 'custom' ? repeatUnitSelect?.value || 'week' : null,
                endDate: config.endDate }, scope: config.scope, weeks: config.weeks };
    }

    function showApplyToast(message, undoAction, undoSeconds = 30) {
        document.getElementById('schedule-apply-toast')?.remove();
        if (undoTimeout) window.clearTimeout(undoTimeout);
        const toast = document.createElement('div');
        toast.id = 'schedule-apply-toast';
        toast.className = 'calendar-apply-toast';
        toast.setAttribute('role', 'status');
        toast.setAttribute('aria-live', 'polite');
        const text = document.createElement('span');
        text.className = 'calendar-apply-toast-text';
        text.textContent = message;
        const undoButton = document.createElement('button');
        undoButton.type = 'button';
        undoButton.className = 'calendar-apply-toast-undo';
        undoButton.textContent = deploymentMessage('undo');
        const refreshButton = document.createElement('button');
        refreshButton.type = 'button';
        refreshButton.className = 'calendar-apply-toast-refresh';
        refreshButton.textContent = deploymentMessage('refresh');
        refreshButton.addEventListener('click', () => window.location.reload());
        let expired = false;
        undoButton.hidden = typeof undoAction !== 'function';
        undoButton.addEventListener('click', async () => {
            undoButton.disabled = true;
            try {
                await undoAction?.();
                window.clearTimeout(undoTimeout);
                undoTimeout = null;
            } catch {
                text.textContent = deploymentMessage('undoError');
                undoButton.disabled = expired;
            }
        });
        toast.append(text, undoButton, refreshButton);
        const host = scheduleDrawer?.classList.contains('open') ? scheduleDrawer : document.body;
        host.appendChild(toast);
        (undoButton.hidden ? refreshButton : undoButton).focus();
        if (!undoButton.hidden) undoTimeout = window.setTimeout(() => {
            expired = true;
            undoButton.disabled = true;
            text.textContent = deploymentMessage('undoExpired');
            if (document.activeElement === undoButton) refreshButton.focus();
            undoTimeout = null;
        }, Math.max(1, undoSeconds) * 1000);
    }

    function showDeploymentResult(scheduleId, data) {
        const values = { added: Number(data.created || 0), replaced: Number(data.replaced || 0), skipped: Number(data.skipped || 0) };
        const message = deploymentMessage('success').replace(/\{(\w+)\}/g, (_, key) => values[key] ?? '');
        const token = data.undoToken;
        showApplyToast(message, token ? async () => {
            const response = await fetch(`/api/schedules/${scheduleId}/deployment/undo`, {
                method: 'POST', credentials: 'same-origin',
                headers: { 'Content-Type': 'application/json', ...(csrfToken ? { [csrfHeader]: csrfToken } : {}) },
                body: JSON.stringify({ undoToken: token })
            });
            if (!response.ok) throw new Error('Undo failed');
            window.location.reload();
        } : null, Number(data.undoExpiresInSeconds || 30));
    }

    function ensureSelectedDayInVisibleRange() {
        const bounds = getVisibleDateBounds();
        if (!bounds) return;
        if (!selectedDay || selectedDay < bounds.start || selectedDay > bounds.end) {
            const today = toLocalIsoDate(new Date());
            selectedDay = today >= bounds.start && today <= bounds.end ? today : bounds.start;
        }
        if (deployStartInput && !deployStartInput.value) {
            deployStartInput.value = selectedDay;
        }
    }

    function renderImpact(impact) {
        if (!impactSummary || !impact || !impact.summary) return;
        const summary = impact.summary;
        const conflictDates = Array.isArray(impact.conflictDates) ? impact.conflictDates : [];
        const added = Number(summary.added ?? 0);
        const replaced = Number(summary.replaced ?? 0);
        const skipped = Number(summary.skipped ?? 0);
        const conflicts = Number(summary.existingConflicts ?? conflictDates.length ?? 0);

        const values = { added, replaced, skipped, conflicts: conflictDates.length,
            already: Number(summary.alreadyScheduled || 0), protected: Number(summary.protectedEntries || 0) };
        impactSummary.textContent = deploymentMessage('impactSummary').replace(/\{(\w+)\}/g, (_, key) => values[key] ?? '');

        renderDeploymentPreview(impact);
        clearConflictHighlights();
        conflictDates.forEach((dateIso) => {
            const card = document.querySelector(`.calendar-day-card[data-date="${dateIso}"]`);
            if (card) {
                card.classList.add('calendar-day-card--deploy-conflict');
            }
        });

        if (impactApplyButton) {
            impactApplyButton.disabled = deploymentInFlight || (added === 0 && replaced === 0);
            impactApplyButton.dataset.hasImpact = 'true';
            impactApplyButton.textContent = `Deploy "${getSelectedScheduleName()}"`;
        }

        setScopeBadge(getCurrentPane().querySelectorAll('.calendar-item').length, conflicts, replaced);
    }

    async function requestImpact(payloadOverride = null) {
        if (!selectedScheduleId) return null;
        if (!payloadOverride && !validDeploymentWindow()) {
            invalidateImpact();
            if (impactSummary) impactSummary.textContent = deploymentMessage('invalidWindow');
            return null;
        }
        const scheduleId = selectedScheduleId;
        const payload = payloadOverride || buildDeploymentPayload();
        const fingerprint = JSON.stringify(payload);
        const revision = ++impactRevision;
        if (impactApplyButton) impactApplyButton.disabled = true;
        let res;
        try {
            res = await fetch(`/api/schedules/${scheduleId}/deployment/impact`, {
                method: 'POST', credentials: 'same-origin',
                headers: { 'Content-Type': 'application/json', ...(csrfToken ? { [csrfHeader]: csrfToken } : {}) },
                body: fingerprint
            });
        } catch (error) {
            if (revision !== impactRevision || selectedScheduleId !== scheduleId) return null;
            throw error;
        }
        if (revision !== impactRevision || selectedScheduleId !== scheduleId) return null;
        if (!res.ok) throw new Error('Impact request failed');
        const impact = await res.json();
        if (revision !== impactRevision || selectedScheduleId !== scheduleId
            || (!payloadOverride && fingerprint !== JSON.stringify(buildDeploymentPayload()))) return null;
        reviewedDeployment = { scheduleId, fingerprint };
        renderImpact(impact);
        return impact;
    }

    async function applyDeployment(options = {}) {
        if (!selectedScheduleId) return null;
        if (deploymentInFlight || !validDeploymentWindow()) return null;
        const scheduleId = selectedScheduleId;
        const payload = options.payload || buildDeploymentPayload();
        const fingerprint = JSON.stringify(payload);
        if (reviewedDeployment?.scheduleId !== scheduleId || reviewedDeployment?.fingerprint !== fingerprint) {
            const impact = await requestImpact(payload);
            if (!impact || selectedScheduleId !== scheduleId || fingerprint !== JSON.stringify(options.payload || buildDeploymentPayload())) return null;
        }
        if (payload.strategy === 'replace') {
            const proceed = window.confirm(deploymentMessage('replaceHelp'));
            if (!proceed) return;
        }

        if (deploymentInFlight) return null;
        deploymentInFlight = true;
        scheduleDrawer?.setAttribute("aria-busy", "true");
        invalidateImpact();
        updateScheduleSelectionUi();
        try {
            const res = await fetch(`/api/schedules/${scheduleId}/deployment/apply`, {
                method: 'POST',
                credentials: 'same-origin',
                headers: {
                    'Content-Type': 'application/json',
                    ...(csrfToken ? { [csrfHeader]: csrfToken } : {})
                },
                body: JSON.stringify(payload)
            });

            if (!res.ok) throw new Error('Apply failed');
            const data = await res.json();

            const created = Number(data.created || 0);
            const replaced = Number(data.replaced || 0);
            const skipped = Number(data.skipped || 0);
            if (impactSummary) {
                impactSummary.textContent = `Applied successfully: ${created} added, ${replaced} replaced, ${skipped} skipped.`;
            }

            clearPreview();
            clearConflictHighlights();

            if (options.onSuccess) {
                options.onSuccess(data);
            }

            if (data.undoToken || options.suppressReload) {
                showDeploymentResult(scheduleId, data);
            } else {
                window.location.reload();
            }
            return data;
        } finally {
            deploymentInFlight = false;
            scheduleDrawer?.removeAttribute("aria-busy");
            updateScheduleSelectionUi();
        }
    }

    function setScopeBadge(entryCount, conflictCount = 0, replacedCount = 0) {
        if (!contextMeta) return;
        contextMeta.classList.remove('is-ok', 'is-warn', 'is-danger');
        if (conflictCount > 3 || replacedCount > 0) {
            contextMeta.classList.add('is-danger');
        } else if (conflictCount > 0) {
            contextMeta.classList.add('is-warn');
        } else {
            contextMeta.classList.add('is-ok');
        }
        contextMeta.textContent = entryCount > 0 ? `${entryCount} entries scheduled` : 'No entries yet';
    }

    function updateContextHeader() {
        syncScopeInputs();
        const pane = getCurrentPane();
        const entryCount = pane.querySelectorAll('.calendar-item').length;
        const config = computeRepeatConfig();
        const repeatLabels = {
            forever: repeatSelect?.querySelector('[value=forever]')?.textContent || '',
            daily: 'Daily',
            weekly: 'Weekly',
            monthly: 'Monthly',
            yearly: 'Yearly',
            custom: 'Custom'
        };
        const repeatLabel = repeatLabels[config.repeat] || 'Custom';
        const scopeLabel = selectedScope === 'day' ? 'Selected day' : selectedScope === 'custom' ? 'Custom range' : 'Visible range';
        const endText = !config.endDate
            ? ''
            : ` | Ends ${formatDateLabel(config.endDate)}`;

        if (contextSummary) {
            contextSummary.textContent = `${scopeLabel} | Starts ${formatDateLabel(config.startDate)} | ${repeatLabel}${endText}`;
        }
        const bounds = getVisibleDateBounds();
        const rangeLabel = document.getElementById('schedule-context-range');
        const selectedLabel = document.getElementById('schedule-context-selected-day');
        const existingLabel = document.getElementById('schedule-context-existing');
        if (rangeLabel && bounds) rangeLabel.textContent = `${formatDateLabel(bounds.start)} – ${formatDateLabel(bounds.end)}`;
        if (selectedLabel) selectedLabel.textContent = formatDateLabel(selectedDay);
        if (existingLabel) existingLabel.textContent = String(entryCount);
        setScopeBadge(entryCount);
    }

    function updateRepeatVisibility() {
        if (repeatCustomRow) {
            repeatCustomRow.hidden = repeatSelect?.value !== 'custom';
        }
        if (repeatEndRow) repeatEndRow.hidden = false;
        if (repeatSelect?.value === 'forever') {
            const config = computeRepeatConfig();
            const end = parseDate(config.startDate);
            if (end) {
                end.setFullYear(end.getFullYear() + 1);
                end.setDate(end.getDate() - 1);
                selectedScope = 'custom';
                if (deployStartInput) deployStartInput.value = config.startDate;
                if (repeatEndInput) repeatEndInput.value = toLocalIsoDate(end);
            }
        }
        syncScopeInputs();
        invalidateImpact();
        updateContextHeader();
        refreshImpactIfReady();
    }

    function loadFavourites() {
        try {
            return JSON.parse(localStorage.getItem(favouriteStorageKey) || '[]');
        } catch {
            return [];
        }
    }

    function saveFavourites(ids) {
        try { localStorage.setItem(favouriteStorageKey, JSON.stringify(ids)); } catch { /* Selection remains usable when storage is unavailable. */ }
    }

    function applyFavouriteUi() {
        const favourites = new Set(loadFavourites());
        scheduleDrawer?.querySelectorAll('[data-favourite-toggle]').forEach((button) => {
            const id = button.getAttribute('data-schedule-id');
            const isFavourite = id ? favourites.has(id) : false;
            const iconEl = button.querySelector('[data-fav-icon]');
            if (iconEl) {
                iconEl.textContent = isFavourite ? '\u2605' : '\u2606';
            } else {
                button.textContent = isFavourite ? '\u2605' : '\u2606';
            }
            button.classList.toggle('is-favourite', isFavourite);
            button.setAttribute('aria-pressed', String(isFavourite));
        });
    }

    function toggleFavourite(scheduleId) {
        if (!scheduleId) return;
        const favourites = new Set(loadFavourites());
        if (favourites.has(scheduleId)) favourites.delete(scheduleId);
        else favourites.add(scheduleId);
        saveFavourites(Array.from(favourites));
        applyFavouriteUi();
    }

    async function hydrateScheduleMetadata() {
        const ids = Array.from(cardById.keys());
        if (!ids.length) return;

        const query = ids.map((id) => `ids=${encodeURIComponent(id)}`).join('&');
        const res = await fetch(`/api/schedules/metadata/batch?${query}`, { credentials: 'same-origin' });
        if (!res.ok) return;
        const data = await res.json();

        ids.forEach((id) => {
            const card = cardById.get(id);
            const metadata = data[id];
            if (!card || !metadata) return;
            scheduleMetadataCache.set(id, metadata);

            const sessionsPerWeek = card.querySelector('[data-meta-field="sessionsPerWeek"]');
            const activeDayLabels = card.querySelector('[data-meta-field="activeDayLabels"]');
            const restDays = card.querySelector('[data-meta-field="restDays"]');

            if (sessionsPerWeek) sessionsPerWeek.textContent = `${metadata.sessionsPerWeek ?? 0}x per week`;
            if (card) {
                const perWeek = Number(metadata.sessionsPerWeek ?? 0);
                let bucket = 'low';
                if (perWeek >= 5) bucket = 'high';
                else if (perWeek >= 3) bucket = 'mid';
                card.setAttribute('data-frequency-bucket', bucket);
            }
            if (activeDayLabels) {
                const labels = Array.isArray(metadata.activeDayLabels) ? metadata.activeDayLabels : [];
                activeDayLabels.textContent = labels.length ? labels.join('/') : 'Any day';
            }
            if (restDays) restDays.textContent = String(metadata.restDays ?? '--');
            renderHealthIndicators(card, metadata);
        });

        applyFiltersAndSort();
    }

    function openScheduleDrawer() {
        if (!scheduleDrawer) return;
        lastFocusedElement = document.activeElement instanceof HTMLElement ? document.activeElement : null;
        scheduleDrawer.removeAttribute("inert");
        scheduleDrawer.classList.add("open");
        scheduleDrawer.setAttribute("aria-hidden", "false");
        document.body.classList.add("calendar-schedule-open");
        document.body.style.overflow = "hidden";
        ensureSelectedDayInVisibleRange();
        updateContextHeader();
        updateRepeatVisibility();
        applyFavouriteUi();
        applyPinnedUi();
        renderQuickAccessChips();
        applyFiltersAndSort();
        if (selectedScheduleId && previewEnabled) {
            activateSchedule(selectedScheduleId, true).catch((error) => console.error(error));
        }
        const focusDrawerWhenVisible = () => {
            if (!scheduleDrawer.classList.contains("open")) return;
            if (window.getComputedStyle(scheduleDrawer).visibility !== "visible") {
                window.requestAnimationFrame(focusDrawerWhenVisible);
                return;
            }
            scheduleDrawer.querySelector("[data-drawer-close]")?.focus();
        };
        window.requestAnimationFrame(focusDrawerWhenVisible);
    }

    function closeScheduleDrawer(options = {}) {
        if (!scheduleDrawer) return;
        scheduleDrawer.classList.remove("open");
        scheduleDrawer.setAttribute("aria-hidden", "true");
        scheduleDrawer.setAttribute("inert", "");
        document.body.classList.remove("calendar-schedule-open");
        document.body.style.overflow = "";
        if (!options.keepPreview) {
            clearPreview();
            clearConflictHighlights();
        }
        if (lastFocusedElement && typeof lastFocusedElement.focus === 'function') {
            lastFocusedElement.focus();
        } else if (scheduleButton) {
            scheduleButton.focus();
        }
    }

    scheduleButton?.addEventListener("click", () => {
        if (scheduleDrawer?.classList.contains("open")) {
            closeScheduleDrawer();
            return;
        }
        openScheduleDrawer();
    });
    scheduleDrawer?.querySelector("[data-drawer-overlay]")?.addEventListener("click", closeScheduleDrawer);
    scheduleDrawer?.querySelector("[data-drawer-close]")?.addEventListener("click", closeScheduleDrawer);

    document.addEventListener("keydown", (e) => {
        if (!scheduleDrawer?.classList.contains("open")) return;
        if (e.key === "Escape") {
            closeScheduleDrawer();
            return;
        }
        if (e.key === "Tab") {
            const focusable = Array.from(scheduleDrawer.querySelectorAll(
                'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
            )).filter((element) => !element.closest("[hidden]") && element.getClientRects().length > 0);
            if (!focusable.length) {
                e.preventDefault();
                return;
            }
            const first = focusable[0];
            const last = focusable[focusable.length - 1];
            if (e.shiftKey && document.activeElement === first) {
                e.preventDefault();
                last.focus();
            } else if (!e.shiftKey && document.activeElement === last) {
                e.preventDefault();
                first.focus();
            }
        }
    });

    scheduleSearch?.addEventListener("input", (e) => {
        const query = e.target.value.toLowerCase().trim();
        if (searchFilterTimer) {
            window.clearTimeout(searchFilterTimer);
        }
        searchFilterTimer = window.setTimeout(() => {
            searchQuery = query;
            applyFiltersAndSort();
        }, 80);
    });

    scopeButtons.forEach((button) => {
        button.addEventListener('click', () => {
            selectedScope = button.dataset.scheduleScope || 'visible';
            syncScopeInputs();
            invalidateImpact();
            updateContextHeader();
            refreshImpactIfReady();
        });
    });

    document.addEventListener('click', (event) => {
        const pinButton = event.target.closest('[data-pin-toggle]');
        if (pinButton) {
            event.preventDefault();
            togglePin(pinButton.getAttribute('data-schedule-id'));
            return;
        }

        const favouriteButton = event.target.closest('[data-favourite-toggle]');
        if (favouriteButton) {
            event.preventDefault();
            toggleFavourite(favouriteButton.getAttribute('data-schedule-id'));
            applyFiltersAndSort();
            return;
        }

        const duplicateButton = event.target.closest('[data-duplicate-schedule]');
        if (duplicateButton) {
            event.preventDefault();
            const scheduleId = duplicateButton.getAttribute('data-schedule-id');
            if (!scheduleId) return;
            duplicateButton.setAttribute('disabled', 'true');

            fetch(`/api/schedules/${scheduleId}/duplicate`, {
                method: 'POST',
                credentials: 'same-origin',
                headers: csrfToken ? { [csrfHeader]: csrfToken } : {}
            }).then((res) => {
                if (!res.ok) throw new Error('Failed to duplicate schedule');
                window.location.reload();
            }).catch((error) => {
                console.error(error);
                duplicateButton.removeAttribute('disabled');
            });
            return;
        }

    });

    scheduleCards.forEach((card) => {
        const scheduleId = card.getAttribute('data-schedule-id');
        if (!scheduleId) return;

        card.addEventListener('mouseenter', () => {
            if (!previewEnabled) return;
            activateSchedule(scheduleId, true).catch((error) => console.error(error));
        });

        card.addEventListener('focusin', () => {
            if (!previewEnabled) return;
            activateSchedule(scheduleId, true).catch((error) => console.error(error));
        });

        card.addEventListener('click', () => {
            activateSchedule(scheduleId, previewEnabled, true).catch((error) => console.error(error));
        });
    });

    document.addEventListener('click', (event) => {
        if (!scheduleDrawer?.classList.contains('open')) return;
        const card = event.target.closest('.calendar-day-card[data-date]');
        if (!card) return;
        if (event.target.closest('button, input, textarea, select, form, a')) return;

        event.preventDefault();
        event.stopPropagation();
        selectedDay = card.getAttribute('data-date');
        invalidateImpact();
        if (deployStartInput && selectedDay) {
            deployStartInput.value = selectedDay;
        }
        updateContextHeader();
    }, true);

    deployStartInput?.addEventListener('change', () => {
        if (selectedScope === 'day') selectedDay = deployStartInput.value;
        invalidateImpact();
        updateContextHeader();
        refreshImpactIfReady();
    });
    repeatSelect?.addEventListener('change', updateRepeatVisibility);
    repeatEndInput?.addEventListener('change', () => {
        invalidateImpact();
        updateContextHeader();
        refreshImpactIfReady();
    });
    repeatIntervalInput?.addEventListener('input', () => {
        invalidateImpact();
        updateContextHeader();
        refreshImpactIfReady();
    });
    repeatUnitSelect?.addEventListener('change', () => {
        invalidateImpact();
        updateContextHeader();
        refreshImpactIfReady();
    });

    previewToggle?.addEventListener('change', () => {
        previewEnabled = !!previewToggle.checked;
        if (!previewEnabled) {
            clearPreview();
            return;
        }
        if (selectedScheduleId) {
            activateSchedule(selectedScheduleId, true).catch((error) => console.error(error));
        }
    });

    previewClearButton?.addEventListener('click', () => {
        selectedScheduleId = null;
        pendingPreviewPayload = null;
        pendingPreviewScheduleId = null;
        if (impactApplyButton) impactApplyButton.dataset.hasImpact = 'false';
        updateScheduleSelectionUi();
        clearPreview();
        if (impactSummary) {
            impactSummary.textContent = 'Pick a schedule, choose where it goes, then deploy.';
        }
        clearConflictHighlights();
        setScopeBadge(getCurrentPane().querySelectorAll('.calendar-item').length);
    });

    previewDeployButton?.addEventListener('click', () => {
        if (!selectedScheduleId || !isPremiumUser()) return;
        if (!document.querySelector('.calendar-month-view')) {
            if (impactSummary) {
                impactSummary.textContent = 'Preview deploy is available in month view.';
            }
            return;
        }

        pendingPreviewPayload = buildDeploymentPayload();
        pendingPreviewScheduleId = selectedScheduleId;

        closeScheduleDrawer({ keepPreview: true });

        requestImpact(pendingPreviewPayload).then((impact) => {
            if (impact) {
                renderProjectedLayout(selectedScheduleId, impact);
            }
            if (impact) showPreviewConfirm();
        }).catch((error) => {
            console.error(error);
            if (impactSummary) {
                impactSummary.textContent = 'Preview deploy failed. Please try again.';
            }
        });
    });

    previewConfirmCancel?.addEventListener('click', () => {
        pendingPreviewPayload = null;
        pendingPreviewScheduleId = null;
        hidePreviewConfirm();
        clearPreview();
        clearConflictHighlights();
        openScheduleDrawer();
    });

    previewConfirmApply?.addEventListener('click', () => {
        if (!pendingPreviewPayload || !pendingPreviewScheduleId) return;
        selectedScheduleId = pendingPreviewScheduleId;
        applyDeployment({
            payload: pendingPreviewPayload,
            suppressReload: true,
            onSuccess: () => {
                hidePreviewConfirm();
                clearPreview();
                clearConflictHighlights();
                collapseMonthView();
                openScheduleDrawer();
            }
        }).catch((error) => {
            console.error(error);
            if (impactSummary) {
                impactSummary.textContent = 'Deployment failed. Please review and retry.';
            }
        });
    });

    strategyButtons.forEach((button) => {
        button.addEventListener('click', () => {
            const strategy = button.getAttribute('data-deploy-strategy') || 'merge';
            selectedStrategy = strategy;
            strategyButtons.forEach((candidate) => {
                candidate.classList.toggle('is-active', candidate === button);
                candidate.setAttribute('aria-pressed', String(candidate === button));
            });
            if (impactApplyButton) {
                impactApplyButton.disabled = true;
                impactApplyButton.dataset.hasImpact = 'false';
            }
            invalidateImpact();
            const help = document.getElementById('schedule-strategy-help');
            if (help) {
                if (strategy === 'replace') {
                    help.textContent = deploymentMessage('replaceHelp');
                } else if (strategy === 'skip') {
                    help.textContent = 'Skips days that already have scheduled items.';
                } else {
                    help.textContent = 'Adds new entries without removing what is already scheduled.';
                }
            }
        });
    });

    function invalidateImpact() {
        impactRevision++;
        reviewedDeployment = null;
        clearPreview();
        clearConflictHighlights();
        if (impactSummary) impactSummary.textContent = deploymentMessage('reviewNeeded');
        if (impactApplyButton) {
            impactApplyButton.disabled = true;
            impactApplyButton.dataset.hasImpact = 'false';
        }
    }

    function refreshImpactIfReady() {
        if (!selectedScheduleId) return;
        requestImpact().catch((error) => {
            console.error(error);
            if (impactSummary) {
                impactSummary.textContent = 'Unable to calculate impact. Please try again.';
            }
        });
    }

    simEnableToggle?.addEventListener('change', () => {
        if (simRunButton) simRunButton.disabled = !selectedScheduleId || !simEnableToggle.checked;
        if (!simEnableToggle.checked) {
            clearPreview();
            if (selectedScheduleId && previewEnabled) {
                activateSchedule(selectedScheduleId, true).catch((error) => console.error(error));
            }
        }
    });

    simToggleButton?.addEventListener('click', () => {
        if (!simPanel) return;
        const nextOpen = !simPanel.classList.contains('is-open');
        simPanel.classList.toggle('is-open', nextOpen);
        simPanel.hidden = !nextOpen;
        simToggleButton.setAttribute('aria-expanded', String(nextOpen));
    });

    simRunButton?.addEventListener('click', () => {
        if (!selectedScheduleId || !simEnableToggle?.checked) return;
        const weeks = Number(simWeeksInput?.value || 6);
        if (!Number.isInteger(weeks) || weeks < 1 || weeks > 12 || !validDeploymentWindow()) {
            if (impactSummary) impactSummary.textContent = deploymentMessage('invalidWindow');
            return;
        }
        invalidateImpact();
        const revision = ++impactRevision;
        const scheduleId = selectedScheduleId;
        const payload = {
            ...buildDeploymentPayload(),
            scope: 'weeks',
            weeks,
            strategy: selectedStrategy
        };

        const simulationEnd = parseDate(payload.startDate);
        simulationEnd.setDate(simulationEnd.getDate() + weeks * 7 - 1);
        payload.recurrence.endDate = toLocalIsoDate(simulationEnd);

        fetch(`/api/schedules/${scheduleId}/deployment/impact`, {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                'Content-Type': 'application/json',
                ...(csrfToken ? { [csrfHeader]: csrfToken } : {})
            },
            body: JSON.stringify(payload)
        }).then((res) => {
            if (!res.ok) throw new Error('Simulation failed');
            return res.json();
        }).then((impact) => {
            if (revision !== impactRevision || selectedScheduleId !== scheduleId || !simEnableToggle?.checked) return;
            renderProjectedLayout(scheduleId, impact);
            if (impactSummary) {
                const summary = impact.summary || {};
                impactSummary.innerHTML = `
                    Simulation: ${weeks} week(s) | ${formatDateLabel(impact.windowStart)} -> ${formatDateLabel(impact.windowEnd)}
                    <br/>Projected adds: ${summary.added ?? 0} | Potential conflicts: ${summary.existingConflicts ?? 0}
                    <br/>Rotation: ${(getListItemForSchedule(selectedScheduleId)?.getAttribute('data-schedule-rotation') || 'weekly_repeat').replaceAll('_', ' ')}
                `;
            }
        }).catch((error) => {
            console.error(error);
            if (impactSummary) {
                impactSummary.textContent = 'Simulation could not be generated. Try a smaller range.';
            }
        });
    });

    [filterType, filterStatus, filterFrequency, sortSelect].forEach((control) => {
        control?.addEventListener('change', applyFiltersAndSort);
    });

    [filterFavourites, filterRecent].forEach((control) => {
        control?.addEventListener('change', applyFiltersAndSort);
    });

    impactReviewButton?.addEventListener('click', () => {
        requestImpact().catch((error) => {
            console.error(error);
            if (impactSummary) {
                impactSummary.textContent = 'Unable to calculate impact. Please try again.';
            }
        });
    });

    impactApplyButton?.addEventListener('click', () => {
        applyDeployment().catch((error) => {
            console.error(error);
            if (impactSummary) {
                impactSummary.textContent = 'Deployment failed. Please review conflicts and retry.';
            }
        });
    });

    deployCancelButton?.addEventListener('click', () => {
        selectedScheduleId = null;
        pendingPreviewPayload = null;
        pendingPreviewScheduleId = null;
        if (impactApplyButton) impactApplyButton.dataset.hasImpact = 'false';
        updateScheduleSelectionUi();
        clearPreview();
        clearConflictHighlights();
        if (impactSummary) {
            impactSummary.textContent = 'Pick a schedule, choose where it goes, then deploy.';
        }
        setScopeBadge(getCurrentPane().querySelectorAll('.calendar-item').length);
    });

    [document.getElementById('month-prev'), document.getElementById('month-next'), document.getElementById('week-prev'), document.getElementById('week-next')]
        .filter(Boolean)
        .forEach((nav) => {
            nav.addEventListener('click', () => {
                clearPreview();
                clearConflictHighlights();
                setTimeout(() => {
                    if (scheduleDrawer?.classList.contains('open')) {
                        ensureSelectedDayInVisibleRange();
                        updateContextHeader();
                        if (selectedScheduleId && previewEnabled) {
                            activateSchedule(selectedScheduleId, true).catch((error) => console.error(error));
                        }
                    }
                }, 450);
            });
        });

    document.querySelectorAll("[data-heatmap-legend-toggle]").forEach((toggle) => {
        const wrapper = toggle.closest("[data-heatmap-legend-wrapper]");
        const legend = wrapper?.querySelector("[data-heatmap-legend]");
        if (!legend) return;

        toggle.addEventListener("click", (event) => {
            event.stopPropagation();
            const isHidden = legend.classList.contains("hidden");
            legend.classList.toggle("hidden", !isHidden);
            toggle.setAttribute("aria-expanded", String(isHidden));
        });

        document.addEventListener("click", (event) => {
            if (!wrapper.contains(event.target)) {
                legend.classList.add("hidden");
                toggle.setAttribute("aria-expanded", "false");
            }
        });
    });

    scheduleDrawer?.querySelectorAll('[data-premium-only]').forEach((el) => {
        el.classList.toggle('hidden', !isPremiumUser());
    });
    updateRepeatVisibility();
    updateScheduleSelectionUi();
    bindDragToApply();
    applyPinnedUi();
    renderQuickAccessChips();
    hydrateScheduleMetadata().catch((error) => console.error(error));
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initCalendarUx);
} else {
    initCalendarUx();
}
