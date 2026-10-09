(function initMonthView() {
    const csrfTokenMeta = document.querySelector('meta[name="_csrf"]');
    const csrfParamMeta = document.querySelector('meta[name="_csrf_param"]');
    const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');

    const csrfToken = csrfTokenMeta ? csrfTokenMeta.content : null;
    const csrfParam = csrfParamMeta ? csrfParamMeta.content : null;
    const csrfHeader = csrfHeaderMeta ? csrfHeaderMeta.content : 'X-CSRF-TOKEN';

    let hoverTimer = null;
    let closeTimer = null;

    const preview = document.getElementById('preview-card');
    const logModal = document.getElementById('calendar-log-modal');
    const logForm = document.getElementById('calendar-log-form');
    const logStatus = document.getElementById('calendar-log-status');
    const logTitle = document.getElementById('calendar-log-title');
    const logTaskFields = logModal?.querySelector('[data-log-task-fields]') || null;
    const logWorkoutFields = logModal?.querySelector('[data-log-workout-fields]') || null;
    const logWorkoutLink = document.getElementById('calendar-log-workout-link');
    const logActions = logModal?.querySelector('.calendar-log-actions') || null;
    let activeItem = null;

    function positionPreviewAtItem(item) {
        if (!preview || !item) return;
        const offset = 16;
        const itemRect = item.getBoundingClientRect();
        const previewRect = preview.getBoundingClientRect();

        let left = itemRect.right + offset;
        let top = itemRect.top;

        if (left + previewRect.width > window.innerWidth - offset) {
            left = itemRect.left - previewRect.width - offset;
        }
        left = Math.max(offset, left);

        const maxTop = window.innerHeight - previewRect.height - offset;
        top = Math.min(Math.max(itemRect.top, offset), Math.max(offset, maxTop));

        preview.style.left = left + 'px';
        preview.style.top = top + 'px';
    }

    function showPreview(item) {
        if (!preview) return;
        preview.classList.remove('hidden');
        preview.classList.remove('opacity-0');
        requestAnimationFrame(() => {
            positionPreviewAtItem(item);
            preview.classList.add('opacity-100');
        });
    }

    function hidePreview() {
        if (!preview) return;
        preview.classList.remove('opacity-100');
        preview.classList.add('opacity-0');

        setTimeout(() => {
            preview.classList.add('hidden');
            activeItem = null;
        }, 180);
    }

    function getCompletedMessage(item) {
        const messages = [
            'Completed. Nice work keeping the streak alive.',
            'Done and dusted. Keep the momentum going.',
            'Completed. Small wins add up fast.',
            'Checked off. You are on track today.'
        ];
        const seed = `${item.dataset.id || ''}${item.dataset.date || ''}`;
        let hash = 0;
        for (let i = 0; i < seed.length; i += 1) {
            hash = (hash * 31 + seed.charCodeAt(i)) % messages.length;
        }
        return messages[hash] || messages[0];
    }

    function buildPreviewHtml(item) {
        const type = item.dataset.type;
        const completed = item.dataset.completed;
        const title = window.OneToOneDom.escapeHtml(item.dataset.title || 'Untitled');
        const time = window.OneToOneDom.escapeHtml(item.dataset.time || '\u2014');
        const notes = window.OneToOneDom.escapeHtml(item.dataset.notes || 'No description');
        const isCompleted = completed === 'true';
        const isWorkout = type === 'occurrence';

        let html = `
            <p class="mb-1 text-lg font-semibold text-slate-900 dark:text-slate-100">${title}</p>
            <p class="text-xs uppercase tracking-widest text-slate-500 dark:text-slate-400">${isWorkout ? 'Workout' : 'Task'}</p>
            <p class="mt-2 text-sm text-slate-700 dark:text-slate-300">Time: ${time}</p>
            <p class="mb-4 text-sm text-slate-500 dark:text-slate-400">${isWorkout ? `From: ${notes || 'Schedule'}` : notes}</p>
        `;

        if (isCompleted) {
            html += `
                <div class="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm font-semibold text-emerald-700 dark:border-emerald-900/40 dark:bg-emerald-950/30 dark:text-emerald-200">
                    &#10003; Completed
                </div>
                <p class="mt-2 text-xs text-slate-500 dark:text-slate-400">${getCompletedMessage(item)}</p>
            `;
        }

        if (type === 'task') {
            if (!isCompleted) {
                html += `
                    <div class="calendar-preview-actions">
                        <button type="button" class="calendar-preview-action" data-preview-action="complete">Mark completed</button>
                        <button type="button" class="calendar-preview-action calendar-preview-action--ghost" data-preview-action="log">Log completion</button>
                    </div>
                `;
            }
        } else if (type === 'occurrence') {
            const actionLabel = isCompleted ? 'Review session' : 'Complete workout';
            const actionType = isCompleted ? 'workout-review' : 'workout-log';
            html += `
                <div class="calendar-preview-actions">
                    <button type="button" class="calendar-preview-action" data-preview-action="${actionType}">${actionLabel}</button>
                </div>
            `;
        }

        return html;
    }

    let previewDelegated = false;
    let longPressTimer = null;
    function bindPreviewDelegation() {
        if (previewDelegated || !preview || !csrfToken || !csrfParam) return;
        previewDelegated = true;

        const ITEM_SELECTOR = '.calendar-item, .calendar-grouped-item';

        document.addEventListener('mouseover', (event) => {
            const item = event.target.closest(ITEM_SELECTOR);
            if (!item) return;
            if (activeItem === item) return;
            clearTimeout(closeTimer);
            clearTimeout(hoverTimer);
            activeItem = item;
            hoverTimer = setTimeout(() => {
                preview.innerHTML = buildPreviewHtml(item);
                showPreview(item);
            }, 600);
        });

        document.addEventListener('mouseout', (event) => {
            const item = event.target.closest(ITEM_SELECTOR);
            if (!item) return;
            const related = event.relatedTarget;
            if (related && (related.closest?.(ITEM_SELECTOR) === item || related.closest?.('#preview-card'))) return;
            clearTimeout(hoverTimer);
            closeTimer = setTimeout(() => {
                if (!preview.matches(':hover')) hidePreview();
            }, 200);
        });

        // Long-press for touch devices (iPhone / Android)
        document.addEventListener('touchstart', (event) => {
            const item = event.target.closest(ITEM_SELECTOR);
            if (!item) return;
            clearTimeout(longPressTimer);
            longPressTimer = setTimeout(() => {
                activeItem = item;
                preview.innerHTML = buildPreviewHtml(item);
                showPreview(item);
            }, 600);
        }, { passive: true });

        document.addEventListener('touchend', () => {
            clearTimeout(longPressTimer);
        }, { passive: true });

        document.addEventListener('touchmove', () => {
            clearTimeout(longPressTimer);
        }, { passive: true });
    }

    function attachPreviewHandlers() {
        bindPreviewDelegation();
    }

    function setLogStatus(message, isError = false) {
        if (!logStatus) return;
        logStatus.textContent = message || '';
        logStatus.classList.toggle('is-error', isError);
        logStatus.classList.toggle('is-visible', !!message);
    }

    function openLogModal(item, mode) {
        if (!logModal || !logForm) return;
        const isWorkout = mode === 'workout';
        if (logTaskFields) logTaskFields.classList.toggle('hidden', isWorkout);
        if (logWorkoutFields) logWorkoutFields.classList.toggle('hidden', !isWorkout);
        if (logActions) logActions.classList.toggle('hidden', isWorkout);
        logForm.dataset.mode = isWorkout ? 'workout' : 'task';

        const title = item.dataset.title || 'Task';
        if (logTitle) logTitle.textContent = isWorkout ? 'Complete workout' : `Log ${title}`;

        logForm.taskId.value = item.dataset.id || '';
        logForm.date.value = item.dataset.date || '';
        logForm.title.value = item.dataset.title || '';
        logForm.time.value = item.dataset.time || '';
        logForm.notes.value = item.dataset.notes || '';
        logForm.exercise.value = item.dataset.exercise || 'false';
        logForm.completed.value = item.dataset.completed || 'false';

        if (logWorkoutLink) {
            const occId = item.dataset.id;
            logWorkoutLink.href = occId ? `/workout-session/launch/occurrence/${occId}` : '#';
        }

        setLogStatus('');
        logModal.classList.remove('hidden');
        logModal.setAttribute('aria-hidden', 'false');
        const focusTarget = isWorkout ? logWorkoutLink : logForm.querySelector('textarea, select, input');
        if (focusTarget) focusTarget.focus();
    }

    function closeLogModal() {
        if (!logModal) return;
        logModal.classList.add('hidden');
        logModal.setAttribute('aria-hidden', 'true');
        setLogStatus('');
    }

    function buildLogEntry(formData) {
        const summary = formData.get('summary') || '';
        const effort = formData.get('effort') || '';
        const mood = formData.get('mood') || '';
        const extra = formData.get('logNotes') || '';
        const stamped = new Date().toLocaleString();
        const parts = [`Completion log (${stamped})`];
        if (summary) parts.push(`Summary: ${summary}`);
        if (effort) parts.push(`Effort: ${effort}/5`);
        if (mood) parts.push(`Mood: ${mood}`);
        if (extra) parts.push(`Notes: ${extra}`);
        return parts.join('\n');
    }

    async function postForm(action, body) {
        const headers = {
            'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
            'X-Requested-With': 'XMLHttpRequest'
        };
        if (csrfToken) headers[csrfHeader] = csrfToken;
        return fetch(action, {
            method: 'POST',
            headers,
            body: body.toString()
        });
    }

    function updateTaskCompletionUi(taskId, completed) {
        const items = document.querySelectorAll(
            `.calendar-item[data-type="task"][data-id="${taskId}"], .calendar-grouped-item[data-type="task"][data-id="${taskId}"]`
        );
        items.forEach((el) => {
            el.classList.toggle('completed', completed);
            el.dataset.completed = String(completed);
            if (el.classList.contains('calendar-item')) {
                let check = el.querySelector('.calendar-item-check');
                if (completed && !check) {
                    check = document.createElement('div');
                    check.className = 'calendar-item-check';
                    check.textContent = '\u2713';
                    el.appendChild(check);
                }
                if (!completed && check) {
                    check.remove();
                }
            }
        });
    }

    async function toggleTaskCompletion(item) {
        const taskId = item.dataset.id;
        const date = item.dataset.date;
        if (!taskId || !date) return;
        const nextCompleted = item.dataset.completed !== 'true';
        item.dataset.completed = String(nextCompleted);
        updateTaskCompletionUi(taskId, nextCompleted);
        if (preview && activeItem && activeItem.dataset.id === taskId) {
            preview.innerHTML = buildPreviewHtml(activeItem);
        }

        const body = new URLSearchParams();
        body.append('taskId', taskId);
        if (csrfParam && csrfToken) body.append(csrfParam, csrfToken);

        try {
            const res = await postForm(`/calendar/day/${date}/toggle-complete`, body);
            if (!res.ok) {
                throw new Error(`Toggle failed: ${res.status}`);
            }
        } catch (error) {
            console.error(error);
            const revert = !nextCompleted;
            item.dataset.completed = String(revert);
            updateTaskCompletionUi(taskId, revert);
            if (preview && activeItem && activeItem.dataset.id === taskId) {
                preview.innerHTML = buildPreviewHtml(activeItem);
            }
        }
    }

    function attachDayCardNavigation(root) {
        if (!root) return;

        root.querySelectorAll('.calendar-day-card[data-day-link]').forEach((card) => {
            if (card.dataset.dayCardNavBound === 'true') return;
            card.dataset.dayCardNavBound = 'true';
            card.classList.add('cursor-pointer');

            card.addEventListener('click', (event) => {
                if (event.defaultPrevented) return;
                if (event.target.closest('button, input, textarea, select, form')) return;

                const href = card.getAttribute('data-day-link');
                if (href) {
                    window.location.href = href;
                }
            });
        });
    }

    if (preview) {
        preview.addEventListener('mouseenter', () => clearTimeout(closeTimer));
        preview.addEventListener('mouseleave', () => {
            closeTimer = setTimeout(() => hidePreview(), 250);
        });
    }

    function handlePreviewAction(actionEl, event) {
        if (!actionEl || !activeItem) return;
        event.preventDefault();
        event.stopPropagation();
        const action = actionEl.getAttribute('data-preview-action');
        if (action === 'complete') {
            toggleTaskCompletion(activeItem).catch((error) => console.error(error));
        }
        if (action === 'log') {
            openLogModal(activeItem, 'task');
        }
        if (action === 'workout-log') {
            const occId = activeItem.dataset.id;
            window.location.href = occId ? `/workout-session/launch/occurrence/${occId}` : '/workout-management';
        }
        if (action === 'workout-review') {
            const occId = activeItem.dataset.id;
            window.location.href = occId ? `/workout-session/launch/occurrence/${occId}` : '/workout-management';
        }
    }

    preview?.addEventListener('click', (event) => {
        const actionEl = event.target.closest('[data-preview-action]');
        if (!actionEl) return;
        handlePreviewAction(actionEl, event);
    });

    document.addEventListener('click', (event) => {
        const actionEl = event.target.closest('[data-preview-action]');
        if (!actionEl) return;
        handlePreviewAction(actionEl, event);
    }, true);

    if (logModal) {
        const backdrop = logModal.querySelector('[data-log-backdrop]');
        const closeBtn = logModal.querySelector('[data-log-close]');
        const cancelBtn = logModal.querySelector('[data-log-cancel]');
        if (backdrop) backdrop.addEventListener('click', closeLogModal);
        if (closeBtn) closeBtn.addEventListener('click', closeLogModal);
        if (cancelBtn) cancelBtn.addEventListener('click', closeLogModal);
        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape' && !logModal.classList.contains('hidden')) {
                closeLogModal();
            }
        });
    }

    if (logForm) {
        logForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (!activeItem) return;
            if (logForm.dataset.mode === 'workout') return;

            const formData = new FormData(logForm);
            const taskId = formData.get('taskId');
            const date = formData.get('date');
            if (!taskId || !date) return;

            const logEntry = buildLogEntry(formData);
            const existingNotes = (formData.get('notes') || '').toString().trim();
            const combinedNotes = existingNotes ? `${existingNotes}\n\n${logEntry}` : logEntry;
            const title = formData.get('title') || '';
            const time = formData.get('time') || '';
            const exercise = formData.get('exercise') === 'true' ? 'true' : 'false';

            try {
                setLogStatus('Saving log...');
                const editBody = new URLSearchParams();
                editBody.append('title', String(title));
                editBody.append('time', String(time));
                editBody.append('notes', combinedNotes);
                editBody.append('exercise', exercise);
                if (csrfParam && csrfToken) editBody.append(csrfParam, csrfToken);
                await postForm(`/calendar/task/${taskId}/edit-inline`, editBody);

                if (activeItem.dataset.completed !== 'true') {
                    await toggleTaskCompletion(activeItem);
                }

                activeItem.dataset.notes = combinedNotes;
                if (preview) preview.innerHTML = buildPreviewHtml(activeItem);
                setLogStatus('Log saved.');
                setTimeout(closeLogModal, 600);
            } catch (error) {
                console.error(error);
                setLogStatus('Unable to save log. Please try again.', true);
            }
        });
    }

    window.addEventListener('resize', () => {
        if (!preview || preview.classList.contains('hidden') || !activeItem) return;
        positionPreviewAtItem(activeItem);
    });

    let monthPane = document.querySelector('[data-month-pane]');
    const paneYear = parseInt(monthPane?.getAttribute('data-pane-year') || '', 10);
    const paneMonth = parseInt(monthPane?.getAttribute('data-pane-month') || '', 10);
    let currentYear = Number.isFinite(paneYear) ? paneYear : new Date().getFullYear();
    let currentMonth = Number.isFinite(paneMonth) ? paneMonth : new Date().getMonth() + 1;
    const heatmapCache = new Map();

    const monthNameEl = document.getElementById('month-name');
    const monthYearEl = document.getElementById('month-year');
    const monthRedirectInput = document.getElementById('month-redirect');
    const jumpTodayBtn = document.getElementById('month-jump-today');
    const jumpDateInput = document.getElementById('month-jump-date');
    const jumpDateBtn = document.getElementById('month-jump-date-go');
    const jumpNextWorkoutBtn = document.getElementById('month-jump-next-workout');
    const jumpTaskInput = document.getElementById('month-jump-task');
    const jumpTaskBtn = document.getElementById('month-jump-task-go');
    const jumpStatusEl = document.getElementById('month-jump-status');
    const jumpControls = [jumpTodayBtn, jumpDateInput, jumpDateBtn, jumpNextWorkoutBtn, jumpTaskInput, jumpTaskBtn].filter(Boolean);
    let jumpActionInProgress = false;

    function setMonthHeader(year, month) {
        if (monthNameEl) {
            monthNameEl.textContent = new Date(year, month - 1, 1).toLocaleString('en-GB', { month: 'long' });
        }
        if (monthYearEl) {
            monthYearEl.textContent = String(year);
        }
        if (monthRedirectInput) {
            monthRedirectInput.value = `/calendar?view=month&month=${month}&year=${year}`;
        }
    }

    function formatDate(year, month, day) {
        const mm = String(month).padStart(2, '0');
        const dd = String(day).padStart(2, '0');
        return `${year}-${mm}-${dd}`;
    }

    function heatClassFor(loadScore) {
        if (loadScore <= 0) return 'heat-none';
        if (loadScore <= 3) return 'heat-low';
        if (loadScore <= 7) return 'heat-med';
        return 'heat-high';
    }

    function normaliseSummaries(summaries) {
        if (Array.isArray(summaries)) return summaries;
        if (Array.isArray(summaries?.items)) return summaries.items;
        if (Array.isArray(summaries?.summaries)) return summaries.summaries;
        return [];
    }

    function applySummaryToPane(pane, summaries) {
        if (!pane) return;
        const summaryMap = new Map(normaliseSummaries(summaries).map((item) => [item.date, item]));

        pane.querySelectorAll('.calendar-day-card[data-date]').forEach((card) => {
            const date = card.getAttribute('data-date');
            const summary = summaryMap.get(date);
            const loadScore = summary ? summary.loadScore : 0;
            card.setAttribute('data-load', String(loadScore));
            card.classList.remove('heat-none', 'heat-low', 'heat-med', 'heat-high');
            card.classList.add(heatClassFor(loadScore));

            const workoutIcon = card.querySelector('.calendar-icon-workout');
            const nutritionIcon = card.querySelector('.calendar-icon-nutrition');
            const prIcon = card.querySelector('.calendar-icon-pr');
            const prCount = card.querySelector('[data-pr-count]');

            if (workoutIcon) workoutIcon.classList.toggle('hidden', !(summary && summary.hasWorkout));
            if (nutritionIcon) nutritionIcon.classList.toggle('hidden', !(summary && summary.hasNutrition));

            if (prIcon) {
                const count = summary ? summary.prHitCount : 0;
                prIcon.classList.toggle('hidden', count <= 0);
                if (prCount) {
                    prCount.textContent = count > 1 ? String(count) : '';
                    prCount.classList.toggle('hidden', count <= 1);
                }
            }
        });
    }

    async function fetchHeatmapSummary(start, end) {
        const cacheKey = `${start}|${end}`;
        if (heatmapCache.has(cacheKey)) return heatmapCache.get(cacheKey);

        const url = new URL('/api/calendar/summary', window.location.origin);
        url.searchParams.set('start', start);
        url.searchParams.set('end', end);
        const res = await fetch(url, { credentials: 'same-origin' });
        if (!res.ok) throw new Error(`Failed to load calendar summary: ${res.status}`);
        const data = await res.json();
        heatmapCache.set(cacheKey, data);
        return data;
    }

    async function updateHeatmapForPane(pane) {
        if (!pane) return;
        const year = parseInt(pane.getAttribute('data-pane-year') || '', 10);
        const month = parseInt(pane.getAttribute('data-pane-month') || '', 10);
        if (!Number.isFinite(year) || !Number.isFinite(month)) return;

        const lastDay = new Date(year, month, 0).getDate();
        const start = formatDate(year, month, 1);
        const end = formatDate(year, month, lastDay);
        try {
            const summary = await fetchHeatmapSummary(start, end);
            applySummaryToPane(pane, summary);
        } catch (err) {
            console.error(err);
        }
    }

    function setJumpStatus(message, type = 'error') {
        if (!jumpStatusEl) return;
        jumpStatusEl.textContent = message || '';
        jumpStatusEl.classList.toggle('is-visible', !!message);
        jumpStatusEl.classList.toggle('is-error', !!message && type === 'error');
        jumpStatusEl.classList.toggle('is-success', !!message && type === 'success');
    }

    function setJumpControlsDisabled(disabled) {
        jumpControls.forEach((control) => {
            control.disabled = disabled;
        });
    }

    async function runJumpAction(action) {
        if (jumpActionInProgress) return;
        const focusedControl = jumpControls.includes(document.activeElement)
            ? document.activeElement
            : null;
        jumpActionInProgress = true;
        setJumpControlsDisabled(true);
        try {
            await action();
        } catch (error) {
            console.error(error);
            setJumpStatus('Jump failed. Please try again.', 'error');
        } finally {
            setJumpControlsDisabled(false);
            jumpActionInProgress = false;
            if (focusedControl?.isConnected) {
                focusedControl.focus();
            }
        }
    }

    function toLocalIsoDate(date) {
        const yyyy = date.getFullYear();
        const mm = String(date.getMonth() + 1).padStart(2, '0');
        const dd = String(date.getDate()).padStart(2, '0');
        return `${yyyy}-${mm}-${dd}`;
    }

    function parseIsoDateInput(value) {
        if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
        const [year, month, day] = value.split('-').map((part) => parseInt(part, 10));
        if (!Number.isFinite(year) || !Number.isFinite(month) || !Number.isFinite(day)) return null;
        return new Date(year, month - 1, day);
    }

    function getCurrentPane() {
        if (isExpanded) {
            return currentSlot || monthPane;
        }
        return monthPane;
    }

    function clearJumpHighlights() {
        document.querySelectorAll('.calendar-day-card--jump-target').forEach((card) => {
            card.classList.remove('calendar-day-card--jump-target');
        });
    }

    function scrollJumpTargetIntoView(card) {
        if (!card) return;
        const rect = card.getBoundingClientRect();
        const topInset = 110;
        const bottomInset = 24;
        const outsideViewport = rect.top < topInset || rect.bottom > (window.innerHeight - bottomInset);
        if (!outsideViewport) return;
        card.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'nearest' });
    }

    function highlightJumpCard(card) {
        if (!card) return false;
        clearJumpHighlights();
        card.classList.add('calendar-day-card--jump-target');
        scrollJumpTargetIntoView(card);
        setTimeout(() => {
            card.classList.remove('calendar-day-card--jump-target');
        }, 1800);
        return true;
    }

    function findDateCardInCurrentPane(dateIso) {
        const pane = getCurrentPane();
        if (!pane) return null;
        return pane.querySelector(`.calendar-day-card[data-date="${dateIso}"]`);
    }

    function sortedCardsInPane(pane) {
        return Array.from(pane.querySelectorAll('.calendar-day-card[data-date]')).sort((a, b) => {
            return (a.dataset.date || '').localeCompare(b.dataset.date || '');
        });
    }

    function findNextWorkoutCard(pane, fromDateIso) {
        const cards = sortedCardsInPane(pane);
        return cards.find((card) => {
            const date = card.dataset.date || '';
            if (date < fromDateIso) return false;
            return !!card.querySelector('.calendar-item[data-type="occurrence"]');
        }) || null;
    }

    function findTaskCardByQuery(pane, query, fromDateIso) {
        const cards = sortedCardsInPane(pane);
        return cards.find((card) => {
            const date = card.dataset.date || '';
            if (date < fromDateIso) return false;
            return Array.from(card.querySelectorAll('.calendar-item[data-type="task"]')).some((item) => {
                const title = (item.dataset.title || '').toLowerCase();
                return title.includes(query);
            });
        }) || null;
    }

    async function jumpToDate(dateIso, successMessage = 'Jumped to selected date.') {
        const parsed = parseIsoDateInput(dateIso);
        if (!parsed) {
            setJumpStatus('Please enter a valid date.', 'error');
            return;
        }
        const targetYear = parsed.getFullYear();
        const targetMonth = parsed.getMonth() + 1;
        if (targetYear !== currentYear || targetMonth !== currentMonth) {
            const url = new URL('/calendar', window.location.origin);
            url.searchParams.set('view', 'month');
            url.searchParams.set('month', String(targetMonth));
            url.searchParams.set('year', String(targetYear));
            url.searchParams.set('jumpDate', dateIso);
            window.location.href = url.toString();
            return;
        }
        const targetCard = findDateCardInCurrentPane(dateIso);
        if (!highlightJumpCard(targetCard)) {
            setJumpStatus('No day card found for that date.', 'error');
            return;
        }
        setJumpStatus(successMessage, 'success');
    }

    async function jumpToNextWorkout() {
        const pane = getCurrentPane();
        if (!pane) return;
        const fromDateIso = toLocalIsoDate(new Date());
        const targetCard = findNextWorkoutCard(pane, fromDateIso);
        if (!targetCard) {
            setJumpStatus('No upcoming workout found in this month.', 'error');
            return;
        }
        highlightJumpCard(targetCard);
        setJumpStatus('Jumped to next workout.', 'success');
    }

    async function jumpToTask() {
        const query = (jumpTaskInput?.value || '').trim().toLowerCase();
        if (!query) {
            setJumpStatus('Please enter a task name to search.', 'error');
            return;
        }
        const pane = getCurrentPane();
        if (!pane) return;
        const fromDateIso = toLocalIsoDate(new Date());
        const targetCard = findTaskCardByQuery(pane, query, fromDateIso);
        if (!targetCard) {
            setJumpStatus('No matching task found in this month.', 'error');
            return;
        }
        highlightJumpCard(targetCard);
        setJumpStatus('Jumped to matching task.', 'success');
    }

    jumpTodayBtn?.addEventListener('click', () => {
        runJumpAction(() => jumpToDate(toLocalIsoDate(new Date()), 'Jumped to today.'));
    });

    jumpDateBtn?.addEventListener('click', () => {
        runJumpAction(() => jumpToDate(jumpDateInput?.value || ''));
    });

    jumpDateInput?.addEventListener('keydown', (event) => {
        if (event.key !== 'Enter') return;
        event.preventDefault();
        runJumpAction(() => jumpToDate(jumpDateInput.value || ''));
    });

    jumpNextWorkoutBtn?.addEventListener('click', () => {
        runJumpAction(() => jumpToNextWorkout());
    });

    jumpTaskBtn?.addEventListener('click', () => {
        runJumpAction(() => jumpToTask());
    });

    jumpTaskInput?.addEventListener('keydown', (event) => {
        if (event.key !== 'Enter') return;
        event.preventDefault();
        runJumpAction(() => jumpToTask());
    });

    jumpDateInput?.addEventListener('input', () => {
        if (jumpStatusEl?.classList.contains('is-error')) {
            setJumpStatus('', 'error');
        }
    });

    jumpTaskInput?.addEventListener('input', () => {
        if (jumpStatusEl?.classList.contains('is-error')) {
            setJumpStatus('', 'error');
        }
    });

    const carousel = document.getElementById('month-carousel');
    const track = document.getElementById('month-carousel-track');
    const expandToggle = document.getElementById('month-expand-toggle');
    const monthPrevBtn = document.getElementById('month-prev');
    const monthNextBtn = document.getElementById('month-next');
    const paneCache = new Map();
    const pendingLoads = new Set();
    let isExpanded = false;
    let isAnimating = false;
    let isLoadingMore = false;
    let prevSlot = null;
    let currentSlot = null;
    let nextSlot = null;
    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    let scrollSettleTimer = null;
    let onExpandedScroll = null;
    let pointerStartX = null;
    let pointerActive = false;
    const swipeThresholdPx = 60;
    const SWIPE_IGNORE_SELECTORS = 'a, button, input, textarea, select, label';

    function paneKey(year, month) {
        return `${year}-${String(month).padStart(2, '0')}`;
    }

    function addMonths(year, month, delta) {
        const base = new Date(year, month - 1, 1);
        base.setMonth(base.getMonth() + delta);
        return { year: base.getFullYear(), month: base.getMonth() + 1 };
    }

    function getTrackGap() {
        if (!track) return 0;
        const styles = getComputedStyle(track);
        const gap = parseFloat(styles.columnGap || styles.gap || '0');
        return Number.isFinite(gap) ? gap : 0;
    }

    function getMonthPanes() {
        return Array.from(track?.querySelectorAll('.month-pane') || []);
    }

    function findPaneByKey(key) {
        return getMonthPanes().find((pane) => pane.getAttribute('data-pane-key') === key) || null;
    }

    function markCenterPane(pane) {
        getMonthPanes().forEach((item) => {
            item.removeAttribute('data-pane-center');
        });
        if (pane) pane.setAttribute('data-pane-center', 'true');
    }

    function getCenterPane() {
        if (!carousel) return monthPane;
        const panes = getMonthPanes();
        if (!panes.length) return monthPane;

        const rect = carousel.getBoundingClientRect();
        const centerX = rect.left + rect.width / 2;
        let bestPane = panes[0];
        let bestDistance = Number.POSITIVE_INFINITY;

        panes.forEach((pane) => {
            const paneRect = pane.getBoundingClientRect();
            const paneCenter = paneRect.left + paneRect.width / 2;
            const distance = Math.abs(paneCenter - centerX);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestPane = pane;
            }
        });
        return bestPane;
    }

    function updateCenterState(force = false) {
        if (!force) return;
        const pane = getCurrentPane();
        if (!pane) return;
        markCenterPane(pane);
        const year = parseInt(pane.getAttribute('data-pane-year') || '', 10);
        const month = parseInt(pane.getAttribute('data-pane-month') || '', 10);
        if (Number.isFinite(year) && Number.isFinite(month)) {
            currentYear = year;
            currentMonth = month;
            setMonthHeader(year, month);
        }
    }

    function createLoadingPane(year, month) {
        const pane = document.createElement('div');
        pane.className = 'calendar-month-container month-pane month-pane--loading';
        pane.setAttribute('data-month-pane', 'true');
        pane.setAttribute('data-pane-year', String(year));
        pane.setAttribute('data-pane-month', String(month));
        pane.setAttribute('data-pane-key', paneKey(year, month));
        pane.innerHTML = `
            <div class="animate-pulse w-full">
                <div class="h-6 w-32 bg-slate-200 dark:bg-slate-700 rounded mb-4"></div>
                <div class="grid grid-cols-7 gap-2">
                    ${Array.from({ length: 35 }).map(() => '<div class="h-20 bg-slate-100 dark:bg-slate-800 rounded"></div>').join('')}
                </div>
            </div>
        `;
        return pane;
    }

    function createErrorPane(year, month, message) {
        const pane = document.createElement('div');
        pane.className = 'calendar-month-container month-pane month-pane--error';
        pane.setAttribute('data-month-pane', 'true');
        pane.setAttribute('data-pane-year', String(year));
        pane.setAttribute('data-pane-month', String(month));
        pane.setAttribute('data-pane-key', paneKey(year, month));
        pane.innerHTML = `
            <div class="text-center px-4">
                <p class="text-sm font-semibold text-slate-700 dark:text-slate-200">Failed to load ${year}-${String(month).padStart(2, '0')}</p>
                <p class="mt-2 text-xs text-slate-500 dark:text-slate-400">${window.OneToOneDom.escapeHtml(message || 'Please try again.')}</p>
                <button type="button" class="mt-4" data-retry>Retry</button>
            </div>
        `;
        return pane;
    }

    function validatePane(pane) {
        if (!pane || !pane.hasAttribute('data-month-pane')) return false;
        const hasYear = pane.hasAttribute('data-pane-year');
        const hasMonth = pane.hasAttribute('data-pane-month');
        const hasKey = pane.hasAttribute('data-pane-key');
        if (!hasYear || !hasMonth || !hasKey) return false;
        const dayCards = pane.querySelectorAll('.calendar-day-card[data-date]');
        return dayCards.length > 0;
    }

    async function fetchMonthPane(year, month) {
        const key = paneKey(year, month);
        if (paneCache.has(key)) {
            const cachedHtml = paneCache.get(key);
            const doc = new DOMParser().parseFromString(cachedHtml, 'text/html');
            const pane = doc.querySelector('[data-month-pane]');
            if (!validatePane(pane)) throw new Error('Invalid cached month pane');
            pane.classList.add('calendar-month-container', 'month-pane');
            return pane;
        }

        const url = new URL('/calendar/month-fragment', window.location.origin);
        url.searchParams.set('year', String(year));
        url.searchParams.set('month', String(month));
        const res = await fetch(url, { headers: { 'X-Requested-With': 'XMLHttpRequest' }, credentials: 'same-origin' });
        if (!res.ok) throw new Error(`Failed to load month pane: ${res.status}`);
        const html = await res.text();
        paneCache.set(key, html);
        const doc = new DOMParser().parseFromString(html, 'text/html');
        const pane = doc.querySelector('[data-month-pane]');
        if (!validatePane(pane)) throw new Error('Invalid month pane response');
        pane.classList.add('calendar-month-container', 'month-pane');
        return pane;
    }

    async function setSlot(slot, year, month) {
        const pane = await fetchMonthPane(year, month);
        pane.classList.add('month-pane');
        if (slot) {
            slot.replaceWith(pane);
        } else if (track) {
            track.appendChild(pane);
        }
        attachDayCardNavigation(pane);
        updateHeatmapForPane(pane);
        return pane;
    }

    function updateNavHrefs() {
        if (!monthPrevBtn || !monthNextBtn) return;
        const prev = addMonths(currentYear, currentMonth, -1);
        const next = addMonths(currentYear, currentMonth, 1);
        monthPrevBtn.href = `/calendar?view=month&month=${prev.month}&year=${prev.year}`;
        monthNextBtn.href = `/calendar?view=month&month=${next.month}&year=${next.year}`;
    }

    async function renderAdjacentPanes() {
        const prev = addMonths(currentYear, currentMonth, -1);
        const next = addMonths(currentYear, currentMonth, 1);
        prevSlot = await setSlot(prevSlot, prev.year, prev.month);
        currentSlot = await setSlot(currentSlot, currentYear, currentMonth);
        nextSlot = await setSlot(nextSlot, next.year, next.month);
        monthPane = currentSlot;
        markCenterPane(currentSlot);
        setMonthHeader(currentYear, currentMonth);
        updateNavHrefs();
        if (track) {
            track.dataset.year = String(currentYear);
            track.dataset.month = String(currentMonth);
        }
    }

    function getPaneWidth() {
        if (!carousel) return 0;
        return carousel.clientWidth || monthPane?.getBoundingClientRect().width || 0;
    }

    function scrollToOffset(offset) {
        return new Promise((resolve) => {
            if (!carousel) {
                resolve();
                return;
            }
            if (prefersReducedMotion) {
                carousel.scrollLeft = offset;
                resolve();
                return;
            }
            let done = false;
            const onScroll = () => {
                if (!carousel) return;
                if (Math.abs(carousel.scrollLeft - offset) < 2) {
                    carousel.removeEventListener('scroll', onScroll);
                    done = true;
                    resolve();
                }
            };
            carousel.addEventListener('scroll', onScroll, { passive: true });
            carousel.scrollTo({ left: offset, behavior: 'smooth' });
            setTimeout(() => {
                if (!done) {
                    carousel.removeEventListener('scroll', onScroll);
                    resolve();
                }
            }, 500);
        });
    }

    function snapToCenter() {
        if (!carousel) return;
        const paneWidth = getPaneWidth();
        if (!paneWidth) return;
        carousel.scrollLeft = paneWidth;
    }

    async function prependMonthToTrack() {
        if (isLoadingMore || !track || !carousel) return;
        const panes = getMonthPanes();
        if (!panes.length) return;
        const first = panes[0];
        const firstYear = parseInt(first.getAttribute('data-pane-year'), 10);
        const firstMonth = parseInt(first.getAttribute('data-pane-month'), 10);
        if (!Number.isFinite(firstYear) || !Number.isFinite(firstMonth)) return;
        const prev = addMonths(firstYear, firstMonth, -1);
        isLoadingMore = true;
        try {
            const pane = await fetchMonthPane(prev.year, prev.month);
            pane.classList.add('calendar-month-container', 'month-pane');
            const paneWidth = getPaneWidth();
            const gap = getTrackGap();
            track.prepend(pane);
            carousel.scrollLeft += paneWidth + gap;
            attachDayCardNavigation(pane);
            updateHeatmapForPane(pane);
        } catch (err) {
            console.error('Failed to prepend month:', err);
        } finally {
            isLoadingMore = false;
        }
    }

    async function appendMonthToTrack() {
        if (isLoadingMore || !track) return;
        const panes = getMonthPanes();
        if (!panes.length) return;
        const last = panes[panes.length - 1];
        const lastYear = parseInt(last.getAttribute('data-pane-year'), 10);
        const lastMonth = parseInt(last.getAttribute('data-pane-month'), 10);
        if (!Number.isFinite(lastYear) || !Number.isFinite(lastMonth)) return;
        const next = addMonths(lastYear, lastMonth, 1);
        isLoadingMore = true;
        try {
            const pane = await fetchMonthPane(next.year, next.month);
            pane.classList.add('calendar-month-container', 'month-pane');
            track.append(pane);
            attachDayCardNavigation(pane);
            updateHeatmapForPane(pane);
        } catch (err) {
            console.error('Failed to append month:', err);
        } finally {
            isLoadingMore = false;
        }
    }

    async function go(delta) {
        if (!isExpanded || isAnimating) return;
        isAnimating = true;
        monthPrevBtn?.setAttribute('aria-disabled', 'true');
        monthNextBtn?.setAttribute('aria-disabled', 'true');
        monthPrevBtn?.classList.add('pointer-events-none', 'opacity-60');
        monthNextBtn?.classList.add('pointer-events-none', 'opacity-60');

        try {
            const panes = getMonthPanes();
            let currentIdx = panes.indexOf(currentSlot);
            if (currentIdx < 0) {
                currentIdx = panes.findIndex((p) => p.getAttribute('data-pane-key') === paneKey(currentYear, currentMonth));
            }
            if (currentIdx < 0) return;
            const targetIdx = currentIdx + delta;
            let targetPane;

            if (targetIdx < 0) {
                await prependMonthToTrack();
                targetPane = getMonthPanes()[0];
            } else if (targetIdx >= panes.length) {
                await appendMonthToTrack();
                const updated = getMonthPanes();
                targetPane = updated[updated.length - 1];
            } else {
                targetPane = panes[targetIdx];
            }

            if (!targetPane) return;
            const targetYear = parseInt(targetPane.getAttribute('data-pane-year'), 10);
            const targetMonth = parseInt(targetPane.getAttribute('data-pane-month'), 10);
            if (!Number.isFinite(targetYear) || !Number.isFinite(targetMonth)) return;

            const carouselRect = carousel.getBoundingClientRect();
            const paneRect = targetPane.getBoundingClientRect();
            const targetOffset = carousel.scrollLeft + paneRect.left - carouselRect.left;
            await scrollToOffset(targetOffset);

            currentYear = targetYear;
            currentMonth = targetMonth;
            currentSlot = targetPane;
            markCenterPane(targetPane);
            setMonthHeader(currentYear, currentMonth);
            updateNavHrefs();

            const url = new URL(window.location.href);
            url.searchParams.set('view', 'month');
            url.searchParams.set('month', String(currentMonth));
            url.searchParams.set('year', String(currentYear));
            window.history.pushState({ month: currentMonth, year: currentYear }, '', url.toString());
        } finally {
            monthPrevBtn?.removeAttribute('aria-disabled');
            monthNextBtn?.removeAttribute('aria-disabled');
            monthPrevBtn?.classList.remove('pointer-events-none', 'opacity-60');
            monthNextBtn?.classList.remove('pointer-events-none', 'opacity-60');
            isAnimating = false;
        }
    }


    async function setSlot(slot, year, month) {
        const pane = await fetchMonthPane(year, month);
        pane.classList.add('month-pane');
        if (slot) {
            slot.replaceWith(pane);
        } else if (track) {
            track.appendChild(pane);
        }
        attachDayCardNavigation(pane);
        updateHeatmapForPane(pane);
        return pane;
    }

    function updateNavHrefs() {
        if (!monthPrevBtn || !monthNextBtn) return;
        const prev = addMonths(currentYear, currentMonth, -1);
        const next = addMonths(currentYear, currentMonth, 1);
        monthPrevBtn.href = `/calendar?view=month&month=${prev.month}&year=${prev.year}`;
        monthNextBtn.href = `/calendar?view=month&month=${next.month}&year=${next.year}`;
    }

    async function renderAdjacentPanes() {
        const prev = addMonths(currentYear, currentMonth, -1);
        const next = addMonths(currentYear, currentMonth, 1);
        prevSlot = await setSlot(prevSlot, prev.year, prev.month);
        currentSlot = await setSlot(currentSlot, currentYear, currentMonth);
        nextSlot = await setSlot(nextSlot, next.year, next.month);
        monthPane = currentSlot;
        markCenterPane(currentSlot);
        setMonthHeader(currentYear, currentMonth);
        updateNavHrefs();
        if (track) {
            track.dataset.year = String(currentYear);
            track.dataset.month = String(currentMonth);
        }
    }


    function startSwipe(event) {
        if (event.target.closest(SWIPE_IGNORE_SELECTORS)) return;
        pointerStartX = event.clientX;
        pointerActive = true;
        carousel?.setPointerCapture?.(event.pointerId);
    }

    function endSwipe(event) {
        carousel?.classList.remove('is-dragging');
        if (!pointerActive || pointerStartX == null) return;
        const delta = event.clientX - pointerStartX;
        pointerActive = false;
        pointerStartX = null;
        if (Math.abs(delta) > swipeThresholdPx) {
            go(delta < 0 ? 1 : -1);
        }
        try {
            carousel?.releasePointerCapture?.(event.pointerId);
        } catch (error) {
            console.warn(error);
        }
    }

    function expandCarousel() {
        if (!carousel || !track || !monthPane) return;
        if (isExpanded) return;
        isExpanded = true;
        carousel.classList.add('is-expanded');
        carousel.classList.add('is-opening');
        if (expandToggle) {
            expandToggle.classList.add('active');
            expandToggle.setAttribute('aria-pressed', 'true');
            expandToggle.textContent = 'Collapse';
        }
        monthPane.classList.add('month-pane--enter-center');

        prevSlot = document.createElement('div');
        prevSlot.className = 'calendar-month-container month-pane';
        currentSlot = monthPane;
        nextSlot = document.createElement('div');
        nextSlot.className = 'calendar-month-container month-pane';
        track.replaceChildren(prevSlot, currentSlot, nextSlot);
        track.style.willChange = 'auto';
        track.style.transform = '';

        renderAdjacentPanes().then(() => {
            snapToCenter();
            monthPane.classList.remove('month-pane--enter-center');
            carousel.classList.remove('is-opening');
        });
    }

    function collapseCarousel() {
        if (!carousel || !track) return;
        if (!isExpanded) return;
        isExpanded = false;
        carousel.classList.remove('is-expanded');
        if (expandToggle) {
            expandToggle.classList.remove('active');
            expandToggle.setAttribute('aria-pressed', 'false');
            expandToggle.textContent = 'Expand';
        }
        const centerPane = getCurrentPane();
        if (!centerPane) return;
        track.replaceChildren(centerPane);
        track.style.transition = '';
        track.style.transform = '';
        prevSlot = null;
        nextSlot = null;
        currentSlot = centerPane;
        monthPane = centerPane;
        updateCenterState(true);
    }

    function toggleCarousel() {
        if (isExpanded) {
            collapseCarousel();
        } else {
            expandCarousel();
        }
    }

    if (carousel && track && monthPane) {
        updateCenterState(true);
        let lastExpandedScrollLeft = carousel.scrollLeft;
        onExpandedScroll = () => {
            if (!isExpanded || !carousel) return;

            // Debounced header update: show the month most visible in viewport
            if (scrollSettleTimer) {
                clearTimeout(scrollSettleTimer);
            }
            scrollSettleTimer = setTimeout(() => {
                if (!isExpanded || !carousel) return;
                const carouselRect = carousel.getBoundingClientRect();
                const panes = getMonthPanes();
                let bestPane = null;
                let bestOverlap = 0;
                panes.forEach((pane) => {
                    const paneRect = pane.getBoundingClientRect();
                    const overlapStart = Math.max(paneRect.left, carouselRect.left);
                    const overlapEnd = Math.min(paneRect.right, carouselRect.right);
                    const overlap = Math.max(0, overlapEnd - overlapStart);
                    if (overlap > bestOverlap) {
                        bestOverlap = overlap;
                        bestPane = pane;
                    }
                });
                if (bestPane) {
                    const year = parseInt(bestPane.getAttribute('data-pane-year'), 10);
                    const month = parseInt(bestPane.getAttribute('data-pane-month'), 10);
                    if (Number.isFinite(year) && Number.isFinite(month) && (year !== currentYear || month !== currentMonth)) {
                        currentYear = year;
                        currentMonth = month;
                        currentSlot = bestPane;
                        markCenterPane(bestPane);
                        setMonthHeader(year, month);
                        updateNavHrefs();
                        const url = new URL(window.location.href);
                        url.searchParams.set('view', 'month');
                        url.searchParams.set('month', String(month));
                        url.searchParams.set('year', String(year));
                        window.history.replaceState({ month, year }, '', url.toString());
                    }
                }
            }, 120);

            // Infinite scroll: load more months when approaching the edges.
            // isLoadingMore prevents concurrent loads; check runs on each scroll event for
            // proactive prefetching but returns quickly when a load is already in progress.
            if (!isLoadingMore && !isAnimating) {
                const proximityThreshold = Math.max(120, carousel.clientWidth * 0.5);
                const scrollingRight = carousel.scrollLeft > lastExpandedScrollLeft;
                const scrollingLeft = carousel.scrollLeft < lastExpandedScrollLeft;
                if (scrollingLeft && carousel.scrollLeft <= proximityThreshold) {
                        prependMonthToTrack();
                }
                if (scrollingRight && carousel.scrollLeft >= carousel.scrollWidth - carousel.clientWidth - proximityThreshold) {
                        appendMonthToTrack();
                }
            }
            lastExpandedScrollLeft = carousel.scrollLeft;
        };
        carousel.addEventListener('scroll', onExpandedScroll, { passive: true });
    }

    expandToggle?.addEventListener('click', toggleCarousel);

    if (carousel) {
        carousel.addEventListener('pointerdown', startSwipe);
        carousel.addEventListener('pointerup', endSwipe);
        carousel.addEventListener('pointercancel', (event) => {
            carousel.classList.remove('is-dragging');
            pointerActive = false;
            pointerStartX = null;
            try { carousel.releasePointerCapture?.(event.pointerId); } catch (e) { /* noop */ }
        });
        carousel.addEventListener('pointermove', () => {
            if (!pointerActive || pointerStartX == null) return;
            carousel.classList.add('is-dragging');
        });
    }
    monthPrevBtn?.addEventListener('click', (event) => {
        event.preventDefault();
        go(-1);
    });
    monthNextBtn?.addEventListener('click', (event) => {
        event.preventDefault();
        go(1);
    });

    attachPreviewHandlers(document);
    attachDayCardNavigation(document);
    setMonthHeader(currentYear, currentMonth);
    updateHeatmapForPane(monthPane);

    // Motivation Mode toggle - shows activity type stickers on calendar day cells
    (function initMotivationMode() {
        const btn = document.getElementById('motivation-mode-toggle');
        if (!btn) return;
        let active = false;

        function applyMotivationMode(on) {
            document.querySelectorAll('.activity-sticker-row').forEach(row => {
                if (on) {
                    row.classList.remove('hidden');
                } else {
                    row.classList.add('hidden');
                }
            });
            document.querySelectorAll('.calendar-day-content').forEach(cell => {
                if (on) {
                    cell.classList.add('motivation-mode');
                } else {
                    cell.classList.remove('motivation-mode');
                }
            });
            btn.setAttribute('aria-pressed', on ? 'true' : 'false');
            btn.classList.toggle('calendar-action-btn--active', on);
        }

        btn.addEventListener('click', () => {
            active = !active;
            applyMotivationMode(active);
        });
    }());

    const urlParams = new URLSearchParams(window.location.search);
    const jumpDateFromUrl = urlParams.get('jumpDate');
    if (jumpDateFromUrl) {
        runJumpAction(() => jumpToDate(jumpDateFromUrl));
    }

    // ===== STICKER CALENDAR =====
    (function initStickerCalendar() {
        const planningPanel = document.getElementById('planning-view-panel');
        const stickerPanel = document.getElementById('sticker-view-panel');
        const btnPlanning = document.getElementById('btn-planning-mode');
        const btnMotivation = document.getElementById('btn-motivation-mode');
        const stickerGrid = document.getElementById('sticker-month-grid');
        const targetFill = document.getElementById('sticker-target-fill');
        const targetCount = document.getElementById('sticker-target-count');
        const tooltip = document.getElementById('sticker-tooltip');
        const insightsEl = document.getElementById('motivation-insights');
        let activeTooltipBadge = null;

        if (!planningPanel || !stickerPanel || !stickerGrid) return;

        let currentPack = 'STARS';
        let isStickerMode = false;

        // ---- Pack icons ----
        const PACKS = {
            STARS: {
                legs:    '\uD83C\uDF1F',
                push:    '\u2B50',
                pull:    '\uD83D\uDCAB',
                cardio:  '\u2728',
                core:    '\uD83C\uDF20',
                default: '\u2B50'
            },
            SPORT: {
                legs:    '\uD83E\uDDB5',
                push:    '\uD83D\uDCAA',
                pull:    '\uD83C\uDFCB\uFE0F',
                cardio:  '\uD83C\uDFC3',
                core:    '\uD83E\uDDD8',
                default: '\uD83C\uDFCB\uFE0F'
            },
            EMOJI: {
                legs:    '\uD83D\uDD25',
                push:    '\uD83D\uDCA5',
                pull:    '\uD83C\uDFAF',
                cardio:  '\u26A1',
                core:    '\uD83C\uDF96\uFE0F',
                default: '\uD83C\uDF89'
            }
        };

        const TYPE_CLASSES = {
            legs:    'sticker-badge--legs',
            push:    'sticker-badge--push',
            pull:    'sticker-badge--pull',
            cardio:  'sticker-badge--cardio',
            core:    'sticker-badge--core',
            default: 'sticker-badge--default'
        };

        function detectWorkoutType(name) {
            if (!name) return 'default';
            const n = name.toLowerCase();
            if (n.includes('leg') || n.includes('squat') || n.includes('lower')) return 'legs';
            if (n.includes('push') || n.includes('chest') || n.includes('shoulder') || n.includes('press')) return 'push';
            if (n.includes('pull') || n.includes('back') || n.includes('row') || n.includes('deadlift')) return 'pull';
            if (n.includes('run') || n.includes('cardio') || n.includes('cycle') || n.includes('swim')) return 'cardio';
            if (n.includes('core') || n.includes('abs') || n.includes('plank')) return 'core';
            return 'default';
        }

        // ---- Read pane data ----
        function readPaneData() {
            const pane = currentSlot || monthPane || document.querySelector('[data-pane-center="true"]') || document.querySelector('[data-month-pane]');
            const source = pane?.querySelector('[data-sticker-session-source]');
            if (!source) return null;

            const sessions = {};
            source.querySelectorAll('[data-sticker-session-day]').forEach((dayEl) => {
                const date = dayEl.getAttribute('data-date');
                if (!date) return;
                sessions[date] = Array.from(dayEl.querySelectorAll('[data-sticker-session-name]'))
                    .map((nameEl) => nameEl.textContent.trim())
                    .filter(Boolean);
            });

            return {
                sessions,
                pack: source.getAttribute('data-sticker-pack') || 'STARS',
                target: parseInt(source.getAttribute('data-sticker-target') || '12', 10),
                completed: parseInt(source.getAttribute('data-sticker-completed') || '0', 10),
                year: parseInt(source.getAttribute('data-sticker-year') || '0', 10),
                month: parseInt(source.getAttribute('data-sticker-month') || '0', 10)
            };
        }

        // ---- Build sticker grid ----
        function buildStickerGrid() {
            const data = readPaneData();
            if (!data) { stickerGrid.innerHTML = ''; if (insightsEl) insightsEl.innerHTML = ''; return; }

            const sessions = data.sessions || {};
            const target = data.target || 12;
            const completed = data.completed || 0;
            const year = data.year;
            const month = data.month;
            const pack = currentPack;
            const icons = PACKS[pack] || PACKS.STARS;

            // Update target bar
            const pct = target > 0 ? Math.min(100, Math.round((completed / target) * 100)) : 0;
            if (targetFill) {
                targetFill.style.width = pct + '%';
                targetFill.className = 'sticker-target-progress-fill' +
                    (pct >= 100 ? ' met' : pct >= 70 ? ' near' : '');
                const progressWrap = targetFill.parentElement;
                if (progressWrap) progressWrap.setAttribute('aria-valuenow', pct);
            }
            if (targetCount) {
                targetCount.textContent = completed + ' / ' + target;
            }

            // ---- Build motivation insights ----
            if (insightsEl && year && month) {
                const activeDays = Object.keys(sessions).filter(d => sessions[d] && sessions[d].length > 0).length;

                // Compute current streak (consecutive days with sessions ending at or before today)
                // month is 1-indexed; new Date(year, month, 0) gives last day of month (day-0 trick)
                const now = new Date();
                const todayIso = now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0') + '-' + String(now.getDate()).padStart(2, '0');
                let streak = 0;
                const lastDayOfMonth = new Date(year, month, 0);
                const checkDate = new Date(Math.min(now.getTime(), lastDayOfMonth.getTime()));
                for (let i = 0; i < 31; i++) {
                    const ds = checkDate.getFullYear() + '-' + String(checkDate.getMonth() + 1).padStart(2, '0') + '-' + String(checkDate.getDate()).padStart(2, '0');
                    if (ds > todayIso) { checkDate.setDate(checkDate.getDate() - 1); continue; }
                    if (sessions[ds] && sessions[ds].length > 0) {
                        streak++;
                        checkDate.setDate(checkDate.getDate() - 1);
                    } else {
                        break;
                    }
                }

                // Pick motivational message
                const MESSAGES = [
                    { min: 100, msg: '\uD83C\uDFC6 Goal smashed! You\'re unstoppable this month!', cls: 'motivation-insights__msg--gold' },
                    { min: 70,  msg: '\uD83D\uDD25 Almost there - keep the fire burning!', cls: 'motivation-insights__msg--amber' },
                    { min: 40,  msg: '\uD83D\uDCAA Good progress - stay consistent!', cls: '' },
                    { min: 1,   msg: '\uD83D\uDE80 You\'ve started - now build the habit!', cls: '' },
                    { min: 0,   msg: '\u2728 Log your first workout to start your journey!', cls: '' }
                ];
                const msgEntry = MESSAGES.find(m => pct >= m.min) || MESSAGES[MESSAGES.length - 1];

                insightsEl.innerHTML =
                    '<div class="motivation-insights__stats">' +
                        '<div class="motivation-insights__stat">' +
                            '<span class="motivation-insights__stat-value">' + activeDays + '</span>' +
                            '<span class="motivation-insights__stat-label">Active days</span>' +
                        '</div>' +
                        '<div class="motivation-insights__stat">' +
                            '<span class="motivation-insights__stat-value">' + completed + '</span>' +
                            '<span class="motivation-insights__stat-label">Workouts</span>' +
                        '</div>' +
                        (streak > 0 ? '<div class="motivation-insights__stat">' +
                            '<span class="motivation-insights__stat-value motivation-insights__stat-value--streak">\uD83D\uDD25 ' + streak + '</span>' +
                            '<span class="motivation-insights__stat-label">Day streak</span>' +
                        '</div>' : '') +
                        '<div class="motivation-insights__stat">' +
                            '<span class="motivation-insights__stat-value">' + pct + '%</span>' +
                            '<span class="motivation-insights__stat-label">Goal complete</span>' +
                        '</div>' +
                    '</div>' +
                    '<p class="motivation-insights__msg ' + msgEntry.cls + '">' + msgEntry.msg + '</p>';
            }

            if (!year || !month) { stickerGrid.innerHTML = ''; return; }

            // Build calendar cells
            const firstDay = new Date(year, month - 1, 1);
            // ISO week starts Monday (1=Mon,...,7=Sun)
            let startDow = firstDay.getDay(); // 0=Sun
            startDow = startDow === 0 ? 6 : startDow - 1; // convert to 0=Mon
            const daysInMonth = new Date(year, month, 0).getDate();
            const today = new Date();
            const todayStr = today.getFullYear() + '-' +
                String(today.getMonth() + 1).padStart(2, '0') + '-' +
                String(today.getDate()).padStart(2, '0');

            let html = '';
            // Leading placeholders
            for (let i = 0; i < startDow; i++) {
                html += '<div class="sticker-placeholder"></div>';
            }
            for (let d = 1; d <= daysInMonth; d++) {
                const isoDate = year + '-' + String(month).padStart(2, '0') + '-' + String(d).padStart(2, '0');
                const isToday = isoDate === todayStr;
                const daySessions = sessions[isoDate] || [];
                html += '<div class="sticker-day-cell' + (isToday ? ' sticker-today' : '') + '" data-date="' + isoDate + '">';
                html += '<span class="sticker-day-number">' + d + '</span>';
                if (daySessions.length > 0) {
                    const MAX_VISIBLE = 3;
                    const visible = daySessions.slice(0, MAX_VISIBLE);
                    visible.forEach(function(name) {
                        const type = detectWorkoutType(name);
                        const icon = icons[type] || icons.default;
                        const cls = TYPE_CLASSES[type] || TYPE_CLASSES.default;
                        const safeTitle = window.OneToOneDom.escapeHtml(name);
                        html += '<button type="button" class="sticker-badge ' + cls + '" ' +
                            'tabindex="0" ' +
                            'aria-label="Completed workout: ' + safeTitle + '" ' +
                            'data-sticker-name="' + safeTitle + '" ' +
                            'data-sticker-date="' + isoDate + '">' +
                            icon + '</button>';
                    });
                    if (daySessions.length > MAX_VISIBLE) {
                        const extra = daySessions.length - MAX_VISIBLE;
                        html += '<span class="sticker-badge sticker-badge--extra" aria-label="+' + extra + ' more workouts">+' + extra + '</span>';
                    }
                }
                html += '</div>';
            }
            stickerGrid.innerHTML = html;
        }

        // ---- Tooltip logic ----
        function showTooltip(badge) {
            if (!tooltip) return;
            if (activeTooltipBadge && activeTooltipBadge !== badge) {
                activeTooltipBadge.removeAttribute('aria-describedby');
            }
            activeTooltipBadge = badge;
            badge.setAttribute('aria-describedby', 'sticker-tooltip');
            const name = window.OneToOneDom.escapeHtml(badge.dataset.stickerName || 'Workout');
            const date = badge.dataset.stickerDate || '';
            const formattedDate = date ? new Date(date + 'T00:00:00').toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric' }) : '';
            tooltip.innerHTML =
                '<div class="sticker-tooltip-title">\u2713 ' + name + '</div>' +
                (formattedDate ? '<div class="sticker-tooltip-meta">' + formattedDate + '</div>' : '') +
                '<div class="sticker-tooltip-meta" style="margin-top:0.25rem">Workout completed \uD83D\uDCAA</div>';

            tooltip.hidden = false;
            tooltip.setAttribute('aria-hidden', 'false');
            const rect = badge.getBoundingClientRect();
            const tw = 220;
            let left = rect.right + 10;
            if (left + tw > window.innerWidth - 8) left = rect.left - tw - 10;
            left = Math.max(8, left);
            let top = rect.top;
            const th2 = tooltip.offsetHeight || 80;
            if (top + th2 > window.innerHeight - 8) top = window.innerHeight - th2 - 8;
            top = Math.max(8, top);

            tooltip.style.left = left + 'px';
            tooltip.style.top = top + 'px';
            tooltip.classList.add('visible');
        }

        function hideTooltip() {
            activeTooltipBadge?.removeAttribute('aria-describedby');
            activeTooltipBadge = null;
            if (tooltip) {
                tooltip.classList.remove('visible');
                tooltip.setAttribute('aria-hidden', 'true');
                tooltip.hidden = true;
            }
        }

        // ---- Event delegation for sticker grid ----
        stickerGrid.addEventListener('mouseover', function(e) {
            const badge = e.target.closest('.sticker-badge[data-sticker-name]');
            if (badge) showTooltip(badge);
        });
        stickerGrid.addEventListener('mouseout', function(e) {
            if (!e.target.closest('.sticker-badge[data-sticker-name]')) return;
            hideTooltip();
        });
        stickerGrid.addEventListener('focusin', function(e) {
            const badge = e.target.closest('.sticker-badge[data-sticker-name]');
            if (badge) showTooltip(badge);
        });
        stickerGrid.addEventListener('focusout', function(e) {
            if (e.target.closest('.sticker-badge[data-sticker-name]')) hideTooltip();
        });
        // Touch/tap for mobile
        stickerGrid.addEventListener('click', function(e) {
            const badge = e.target.closest('.sticker-badge[data-sticker-name]');
            if (!badge) { hideTooltip(); return; }
            if (tooltip && tooltip.classList.contains('visible')) {
                hideTooltip();
            } else {
                showTooltip(badge);
            }
        });
        document.addEventListener('click', function(e) {
            if (!e.target.closest('#sticker-month-grid') && !e.target.closest('#sticker-tooltip')) {
                hideTooltip();
            }
        });

        // ---- Pack selector ----
        stickerPanel.querySelectorAll('.sticker-pack-btn').forEach(function(btn) {
            btn.addEventListener('click', function() {
                const pack = btn.dataset.pack;
                if (!pack) return;
                currentPack = pack;
                // Persist via form POST
                const form = document.createElement('form');
                form.method = 'POST';
                form.action = '/calendar/sticker-preferences';
                form.style.display = 'none';
                const csrfInput = document.createElement('input');
                csrfInput.type = 'hidden';
                csrfInput.name = (document.querySelector('meta[name="_csrf_param"]') || {}).content || '_csrf';
                csrfInput.value = (document.querySelector('meta[name="_csrf"]') || {}).content || '';
                form.appendChild(csrfInput);
                const packInput = document.createElement('input');
                packInput.type = 'hidden';
                packInput.name = 'stickerPack';
                packInput.value = pack;
                form.appendChild(packInput);
                // Do not redirect - submit via fetch so we stay on page
                fetch('/calendar/sticker-preferences', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded',
                        [(document.querySelector('meta[name="_csrf_header"]') || {}).content || 'X-CSRF-TOKEN']:
                            (document.querySelector('meta[name="_csrf"]') || {}).content || ''
                    },
                    body: 'stickerPack=' + encodeURIComponent(pack) + '&redirect=%2Fcalendar',
                    credentials: 'same-origin'
                }).catch(() => {});

                stickerPanel.querySelectorAll('.sticker-pack-btn').forEach(function(b) {
                    b.classList.remove('active');
                    b.setAttribute('aria-pressed', 'false');
                });
                btn.classList.add('active');
                btn.setAttribute('aria-pressed', 'true');
                buildStickerGrid();
            });
        });

        // ---- Mode toggle ----
        function switchToMotivation() {
            isStickerMode = true;
            planningPanel.style.display = 'none';
            stickerPanel.style.display = 'block';
            btnPlanning?.classList.remove('active');
            btnPlanning?.setAttribute('aria-pressed', 'false');
            btnMotivation?.classList.add('active');
            btnMotivation?.setAttribute('aria-pressed', 'true');
            buildStickerGrid();
            localStorage.setItem('calendarStickerMode', '1');
        }

        function switchToPlanning() {
            isStickerMode = false;
            planningPanel.style.display = '';
            stickerPanel.style.display = 'none';
            btnPlanning?.classList.add('active');
            btnPlanning?.setAttribute('aria-pressed', 'true');
            btnMotivation?.classList.remove('active');
            btnMotivation?.setAttribute('aria-pressed', 'false');
            hideTooltip();
            localStorage.setItem('calendarStickerMode', '0');
        }

        btnMotivation && btnMotivation.addEventListener('click', switchToMotivation);
        btnPlanning && btnPlanning.addEventListener('click', switchToPlanning);

        // Restore last mode from localStorage
        if (btnPlanning && btnMotivation && localStorage.getItem('calendarStickerMode') === '1') {
            switchToMotivation();
        }

        // Re-render sticker grid when carousel navigates to a new month
        const origSetSlot = typeof setSlot === 'function' ? setSlot : null;
        // Hook: re-render sticker grid when monthPane changes (carousel navigation)
        const carouselTrack2 = document.getElementById('month-carousel-track');
        if (carouselTrack2) {
            const observer = new MutationObserver(function() {
                if (isStickerMode) buildStickerGrid();
            });
            observer.observe(carouselTrack2, { childList: true, subtree: false });
        }
    }());
})();
