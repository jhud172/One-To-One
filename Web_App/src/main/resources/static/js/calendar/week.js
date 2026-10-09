document.addEventListener("DOMContentLoaded", () => {
    const csrfTokenMeta = document.querySelector('meta[name="_csrf"]');
    const csrfParamMeta = document.querySelector('meta[name="_csrf_param"]');
    const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');

    const csrfToken = csrfTokenMeta ? csrfTokenMeta.content : null;
    const csrfParam = csrfParamMeta ? csrfParamMeta.content : null;
    const csrfHeader = csrfHeaderMeta ? csrfHeaderMeta.content : 'X-CSRF-TOKEN';

    const preview = document.getElementById("preview-card");
    if (!preview) return;

    let hoverTimer = null;
    let closeTimer = null;
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

        preview.style.left = left + "px";
        preview.style.top = top + "px";
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

    function showPreview(item) {
        preview.classList.remove("hidden");
        preview.classList.remove("opacity-0");
        requestAnimationFrame(() => {
            positionPreviewAtItem(item);
            preview.classList.add("opacity-100");
        });
    }

    function hidePreview() {
        preview.classList.remove("opacity-100");
        preview.classList.add("opacity-0");

        setTimeout(() => {
            preview.classList.add("hidden");
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
        const title = window.OneToOneDom.escapeHtml(item.dataset.title || "Unknown");
        const time = window.OneToOneDom.escapeHtml(item.dataset.time || "\u2014");
        const notes = window.OneToOneDom.escapeHtml(item.dataset.notes || "No notes");
        const completed = item.dataset.completed === "true";
        const type = item.dataset.type;
        const isWorkout = type === "occurrence" || type === "workout";
        let html = `
            <p class="mb-1 text-lg font-semibold text-slate-900 dark:text-slate-100">${title}</p>
            <p class="text-xs uppercase tracking-widest text-slate-500 dark:text-slate-400">${isWorkout ? "Workout" : "Task"}</p>
            <p class="mt-2 text-sm text-slate-700 dark:text-slate-300">Time: ${time}</p>
            <p class="mb-4 text-sm text-slate-500 dark:text-slate-400">${isWorkout ? `From: ${notes || 'Schedule'}` : notes}</p>
        `;

        if (completed) {
            html += `
                <div class="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm font-semibold text-emerald-700 dark:border-emerald-900/40 dark:bg-emerald-950/30 dark:text-emerald-200">
                    &#10003; Completed
                </div>
                <p class="mt-2 text-xs text-slate-500 dark:text-slate-400">${getCompletedMessage(item)}</p>
            `;
        }

        const date = /^\d{4}-\d{2}-\d{2}$/.test(item.dataset.date || '') ? item.dataset.date : '';
        if (type === "task") {
            const dayLink = date ? `/calendar/day/${date}#tasks` : '/calendar';
            if (!completed) {
                html += `
                    <div class="calendar-preview-actions">
                        <button type="button" class="calendar-preview-action" data-preview-action="complete">Mark completed</button>
                        <button type="button" class="calendar-preview-action calendar-preview-action--ghost" data-preview-action="log">Log completion</button>
                    </div>
                `;
            }
            html += `<a href="${dayLink}" class="mt-3 block text-center text-xs text-slate-400 underline hover:text-slate-600 dark:text-slate-500 dark:hover:text-slate-300">View full day &#8594;</a>`;
        } else if (type === "workout" || type === "occurrence") {
            const dayLink = date ? `/calendar/day/${date}#workouts` : '/calendar';
            const actionLabel = completed ? 'Review session' : 'Complete workout';
            const actionType = completed ? 'workout-review' : 'workout-log';
            html += `
                <div class="calendar-preview-actions">
                    <button type="button" class="calendar-preview-action" data-preview-action="${actionType}">${actionLabel}</button>
                </div>
                <a href="${dayLink}" class="mt-3 block text-center text-xs text-slate-400 underline hover:text-slate-600 dark:text-slate-500 dark:hover:text-slate-300">View full day &#8594;</a>
            `;
        }

        return html;
    }

    function renderPreview(item) {
        preview.innerHTML = buildPreviewHtml(item);
    }

    let previewDelegated = false;
    let longPressTimer = null;
    function bindPreviewDelegation() {
        if (previewDelegated) return;
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
                renderPreview(item);
                showPreview(item);
            }, 500);
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
                renderPreview(item);
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

    preview.addEventListener("mouseenter", () => clearTimeout(closeTimer));
    preview.addEventListener("mouseleave", () => {
        closeTimer = setTimeout(() => hidePreview(), 250);
    });

    preview.addEventListener('click', (event) => {
        const actionEl = event.target.closest('[data-preview-action]');
        if (!actionEl || !activeItem) return;
        handlePreviewAction(actionEl, event);
    });

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
                    check.textContent = '✓';
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
        if (preview.classList.contains('hidden') || !activeItem) return;
        positionPreviewAtItem(activeItem);
    });

    const slider = document.getElementById("week-slider");
    const track = document.getElementById("week-slider-track");
    const prevLink = document.getElementById("week-prev");
    const nextLink = document.getElementById("week-next");
    const weekRangeEl = document.getElementById("week-range");
    const weekRangeYearEl = document.getElementById("week-range-year");
    const weekRedirectInput = document.getElementById("week-redirect");
    const jumpTodayBtn = document.getElementById("week-jump-today");
    const jumpDateInput = document.getElementById("week-jump-date");
    const jumpDateBtn = document.getElementById("week-jump-date-go");
    const jumpNextWorkoutBtn = document.getElementById("week-jump-next-workout");
    const jumpTaskInput = document.getElementById("week-jump-task");
    const jumpTaskBtn = document.getElementById("week-jump-task-go");
    const jumpStatusEl = document.getElementById("week-jump-status");
    const jumpControls = [jumpTodayBtn, jumpDateInput, jumpDateBtn, jumpNextWorkoutBtn, jumpTaskInput, jumpTaskBtn].filter(Boolean);
    let jumpActionInProgress = false;
    const currentSlot = track?.querySelector('[data-week-pane-slot="current"]');
    const prevSlot = track?.querySelector('[data-week-pane-slot="prev"]');
    const nextSlot = track?.querySelector('[data-week-pane-slot="next"]');

    const hasSlider = slider && track && prevLink && nextLink && currentSlot && prevSlot && nextSlot;
    if (!hasSlider) {
        attachPreviewHandlers(document);
        attachDayCardNavigation(document);
        return;
    }

    // Adjacent weeks are visual animation buffers, not additional tab stops.
    [prevSlot, nextSlot].forEach(slot => {
        slot.setAttribute('inert', '');
        slot.setAttribute('aria-hidden', 'true');
    });

    const prefersReducedMotion = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

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
        const mm = String(date.getMonth() + 1).padStart(2, "0");
        const dd = String(date.getDate()).padStart(2, "0");
        return `${yyyy}-${mm}-${dd}`;
    }

    function parseIsoDateInput(value) {
        if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
        const [year, month, day] = value.split("-").map((part) => parseInt(part, 10));
        if (!Number.isFinite(year) || !Number.isFinite(month) || !Number.isFinite(day)) return null;
        return new Date(year, month - 1, day);
    }

    function getCurrentPane() {
        return currentSlot.querySelector('[data-week-pane]');
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

    function sortedCardsInPane(pane) {
        return Array.from(pane.querySelectorAll('.calendar-day-card[data-date]')).sort((a, b) => {
            return (a.dataset.date || '').localeCompare(b.dataset.date || '');
        });
    }

    function findDateCardInCurrentPane(dateIso) {
        const pane = getCurrentPane();
        if (!pane) return null;
        return pane.querySelector(`.calendar-day-card[data-date="${dateIso}"]`);
    }

    function weekDifference(fromWeekYear, fromWeek, toWeekYear, toWeek) {
        const fromStart = getIsoWeekStart(fromWeekYear, fromWeek);
        const toStart = getIsoWeekStart(toWeekYear, toWeek);
        return Math.round((toStart.getTime() - fromStart.getTime()) / (7 * 24 * 60 * 60 * 1000));
    }

    async function jumpToWeek(targetWeekYear, targetWeek) {
        let safety = 0;
        while ((currentWeekYear !== targetWeekYear || currentWeek !== targetWeek) && safety < 260) {
            const diff = weekDifference(currentWeekYear, currentWeek, targetWeekYear, targetWeek);
            await go(diff > 0 ? 1 : -1);
            safety += 1;
        }
        return currentWeekYear === targetWeekYear && currentWeek === targetWeek;
    }

    function getCurrentWeekState() {
        return { weekYear: currentWeekYear, week: currentWeek };
    }

    async function restoreWeekState(state) {
        if (!state) return;
        if (currentWeekYear === state.weekYear && currentWeek === state.week) return;
        await jumpToWeek(state.weekYear, state.week);
    }

    function findNextWorkoutCard(pane, fromDateIso) {
        const cards = sortedCardsInPane(pane);
        return cards.find((card) => {
            const date = card.dataset.date || '';
            if (date < fromDateIso) return false;
            return !!card.querySelector('.calendar-item[data-type="workout"], .calendar-item[data-type="occurrence"], .calendar-grouped-item[data-type="occurrence"]');
        }) || null;
    }

    function findTaskCardByQuery(pane, query, fromDateIso) {
        const cards = sortedCardsInPane(pane);
        return cards.find((card) => {
            const date = card.dataset.date || '';
            if (date < fromDateIso) return false;
            return Array.from(card.querySelectorAll('.calendar-item[data-type="task"], .calendar-grouped-item[data-type="task"]')).some((item) => {
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
        const target = getIsoWeekFromDate(parsed);
        const reached = await jumpToWeek(target.weekYear, target.week);
        if (!reached) {
            setJumpStatus('Could not navigate to that date.', 'error');
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
        const fromDateIso = toLocalIsoDate(new Date());
        const initialState = getCurrentWeekState();
        let safety = 0;
        while (safety < 52) {
            const pane = getCurrentPane();
            if (pane) {
                const targetCard = findNextWorkoutCard(pane, fromDateIso);
                if (targetCard) {
                    highlightJumpCard(targetCard);
                    setJumpStatus('Jumped to next workout.', 'success');
                    return;
                }
            }
            await go(1);
            safety += 1;
        }
        await restoreWeekState(initialState);
        setJumpStatus('No upcoming workout found.', 'error');
    }

    async function jumpToTask() {
        const query = (jumpTaskInput?.value || '').trim().toLowerCase();
        if (!query) {
            setJumpStatus('Please enter a task name to search.', 'error');
            return;
        }
        const fromDateIso = toLocalIsoDate(new Date());
        const initialState = getCurrentWeekState();
        let safety = 0;
        while (safety < 52) {
            const pane = getCurrentPane();
            if (pane) {
                const targetCard = findTaskCardByQuery(pane, query, fromDateIso);
                if (targetCard) {
                    highlightJumpCard(targetCard);
                    setJumpStatus('Jumped to matching task.', 'success');
                    return;
                }
            }
            await go(1);
            safety += 1;
        }
        await restoreWeekState(initialState);
        setJumpStatus('No matching task found.', 'error');
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

    const heatmapCache = new Map();

    function heatClassFor(loadScore) {
        if (loadScore <= 0) return "heat-none";
        if (loadScore <= 3) return "heat-low";
        if (loadScore <= 7) return "heat-med";
        return "heat-high";
    }

    const weekStatsBar = document.getElementById("week-stats-bar");

    function updateWeekStatsBar(summaries) {
        if (!weekStatsBar) return;
        const workoutDays = summaries.filter((s) => s.hasWorkout).length;
        const daysWithNutrition = summaries.filter((s) => s.hasNutrition).length;
        const activeDays = summaries.filter((s) => s.loadScore > 0).length;
        const maxLoad = summaries.reduce((max, s) => Math.max(max, s.loadScore), 0);

        if (workoutDays === 0 && daysWithNutrition === 0 && activeDays === 0) {
            weekStatsBar.classList.add("hidden");
            return;
        }

        const bars = summaries.map((s) => {
            const pct = maxLoad > 0 ? Math.round((s.loadScore / maxLoad) * 100) : 0;
            const heatClass = heatClassFor(s.loadScore);
            return `<div class="calendar-week-stats-bar__day" aria-label="${s.date}: load ${s.loadScore}">
                <div class="calendar-week-stats-bar__fill ${heatClass}" style="height:${pct}%"></div>
            </div>`;
        }).join('');

        weekStatsBar.innerHTML = `
            <div class="calendar-week-stats-bar__meta">
                ${workoutDays > 0 ? `<span class="calendar-week-stat"><span class="calendar-icon-dot"></span> ${workoutDays} workout${workoutDays !== 1 ? 's' : ''}</span>` : ''}
                ${daysWithNutrition > 0 ? `<span class="calendar-week-stat"><svg class="calendar-week-stat__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 3v7"/><path d="M9 3v7"/><path d="M5 10h4"/><path d="M14 3c0 4 0 7 4 7v11"/></svg> ${daysWithNutrition} day${daysWithNutrition !== 1 ? 's' : ''} logged</span>` : ''}
                ${activeDays > 0 ? `<span class="calendar-week-stat"><span style="font-size:0.75rem">⚡</span> ${activeDays} active day${activeDays !== 1 ? 's' : ''}</span>` : ''}
            </div>
            <div class="calendar-week-stats-bar__chart" aria-hidden="true">${bars}</div>
        `;
        weekStatsBar.classList.remove("hidden");
    }

    function applySummaryToPane(pane, summaries, isCurrentPane) {
        if (!pane) return;
        const summaryMap = new Map(summaries.map((item) => [item.date, item]));

        pane.querySelectorAll(".calendar-day-card[data-date]").forEach((card) => {
            const date = card.getAttribute("data-date");
            const summary = summaryMap.get(date);
            const loadScore = summary ? summary.loadScore : 0;
            card.setAttribute("data-load", String(loadScore));
            card.classList.remove("heat-none", "heat-low", "heat-med", "heat-high");
            card.classList.add(heatClassFor(loadScore));

            const workoutIcon = card.querySelector(".calendar-icon-workout");
            const nutritionIcon = card.querySelector(".calendar-icon-nutrition");
            const prIcon = card.querySelector(".calendar-icon-pr");
            const prCount = card.querySelector("[data-pr-count]");

            if (workoutIcon) workoutIcon.classList.toggle("hidden", !(summary && summary.hasWorkout));
            if (nutritionIcon) nutritionIcon.classList.toggle("hidden", !(summary && summary.hasNutrition));

            if (prIcon) {
                const count = summary ? summary.prHitCount : 0;
                prIcon.classList.toggle("hidden", count <= 0);
                if (prCount) {
                    prCount.textContent = count > 1 ? String(count) : "";
                    prCount.classList.toggle("hidden", count <= 1);
                }
            }
        });

        if (isCurrentPane) {
            updateWeekStatsBar(summaries);
        }
    }

    async function fetchHeatmapSummary(start, end) {
        const cacheKey = `${start}|${end}`;
        if (heatmapCache.has(cacheKey)) return heatmapCache.get(cacheKey);

        const url = new URL("/api/calendar/summary", window.location.origin);
        url.searchParams.set("start", start);
        url.searchParams.set("end", end);
        const res = await fetch(url, { credentials: "same-origin" });
        if (!res.ok) throw new Error(`Failed to load calendar summary: ${res.status}`);
        const data = await res.json();
        heatmapCache.set(cacheKey, data);
        return data;
    }

    async function updateHeatmapForPane(pane) {
        if (!pane) return;
        const week = parseInt(pane.getAttribute("data-week") || "", 10);
        const weekYear = parseInt(pane.getAttribute("data-week-year") || "", 10);
        if (!Number.isFinite(week) || !Number.isFinite(weekYear)) return;

        const isCurrentPane = week === currentWeek && weekYear === currentWeekYear;

        const startDate = getIsoWeekStart(weekYear, week);
        const endDate = new Date(startDate);
        endDate.setDate(startDate.getDate() + 6);
        const start = formatIsoDate(startDate);
        const end = formatIsoDate(endDate);

        try {
            const summary = await fetchHeatmapSummary(start, end);
            applySummaryToPane(pane, summary, isCurrentPane);
        } catch (err) {
            console.error(err);
        }
    }

    function refreshHeatmaps() {
        const panes = [
            prevSlot.querySelector("[data-week-pane]"),
            currentSlot.querySelector("[data-week-pane]"),
            nextSlot.querySelector("[data-week-pane]")
        ].filter(Boolean);
        panes.forEach((pane) => {
            updateHeatmapForPane(pane);
        });
    }

    function keyFor(weekYear, week) {
        return `${weekYear}-W${String(week).padStart(2, "0")}`;
    }

    function getIsoWeekStart(weekYear, week) {
        const jan4 = new Date(weekYear, 0, 4);
        const day = jan4.getDay() === 0 ? 7 : jan4.getDay();
        const monday = new Date(jan4);
        monday.setDate(jan4.getDate() - (day - 1));
        const target = new Date(monday);
        target.setDate(monday.getDate() + (week - 1) * 7);
        return target;
    }

    function formatIsoDate(date) {
        return date.toISOString().slice(0, 10);
    }

    function ordinalDay(day) {
        if (day % 10 === 1 && day % 100 !== 11) return `${day}st`;
        if (day % 10 === 2 && day % 100 !== 12) return `${day}nd`;
        if (day % 10 === 3 && day % 100 !== 13) return `${day}rd`;
        return `${day}th`;
    }

    function formatWeekRange(start, end) {
        const startMonth = start.toLocaleDateString('en-GB', { month: 'short' });
        const endMonth = end.toLocaleDateString('en-GB', { month: 'short' });
        if (startMonth === endMonth) {
            return `${startMonth} ${ordinalDay(start.getDate())} – ${ordinalDay(end.getDate())}`;
        }
        return `${startMonth} ${ordinalDay(start.getDate())} – ${endMonth} ${ordinalDay(end.getDate())}`;
    }

    function updateWeekHeaderAndStrip() {
        const start = getIsoWeekStart(currentWeekYear, currentWeek);
        const end = new Date(start);
        end.setDate(start.getDate() + 6);

        if (weekRangeEl) weekRangeEl.textContent = formatWeekRange(start, end);
        if (weekRangeYearEl) weekRangeYearEl.textContent = String(start.getFullYear());
        if (weekRedirectInput) {
            weekRedirectInput.value = `/calendar?view=week&week=${currentWeek}&weekYear=${currentWeekYear}`;
        }
    }

    function getIsoWeekFromDate(date) {
        const d = new Date(date);
        d.setHours(0, 0, 0, 0);
        d.setDate(d.getDate() + 3 - ((d.getDay() + 6) % 7));
        const weekYear = d.getFullYear();
        const week1 = new Date(weekYear, 0, 4);
        const week = 1 + Math.round(((d.getTime() - week1.getTime()) / 86400000 - 3 + ((week1.getDay() + 6) % 7)) / 7);
        return { weekYear, week };
    }

    function addWeeks(weekYear, week, delta) {
        const start = getIsoWeekStart(weekYear, week);
        start.setDate(start.getDate() + delta * 7);
        return getIsoWeekFromDate(start);
    }

    function weekPaneUrl(weekYear, week) {
        const url = new URL("/calendar/week-fragment", window.location.origin);
        url.searchParams.set("week", String(week));
        url.searchParams.set("weekYear", String(weekYear));
        return url.toString();
    }

    function parsePaneHtml(html) {
        const template = document.createElement("template");
        template.innerHTML = html.trim();
        return template.content.firstElementChild;
    }

    const cache = new Map();
    let currentWeek = parseInt(slider.dataset.week || "", 10);
    let currentWeekYear = parseInt(slider.dataset.weekYear || "", 10);
    if (!Number.isFinite(currentWeek) || !Number.isFinite(currentWeekYear)) {
        const now = getIsoWeekFromDate(new Date());
        currentWeek = now.week;
        currentWeekYear = now.weekYear;
    }

    let isAnimating = false;

    async function fetchPane(weekYear, week) {
        const url = weekPaneUrl(weekYear, week);
        const res = await fetch(url, {
            headers: { "X-Requested-With": "XMLHttpRequest" },
            credentials: "same-origin"
        });
        if (!res.ok) throw new Error(`Failed to load week pane: ${res.status}`);
        return await res.text();
    }

    async function ensureCached(weekYear, week) {
        const key = keyFor(weekYear, week);
        if (cache.has(key)) return;
        const html = await fetchPane(weekYear, week);
        cache.set(key, html);
    }

    function evictOutsideWindow(centerYear, centerWeek) {
        const keep = new Set();
        for (let d = -2; d <= 2; d++) {
            const t = addWeeks(centerYear, centerWeek, d);
            keep.add(keyFor(t.weekYear, t.week));
        }
        for (const key of Array.from(cache.keys())) {
            if (!keep.has(key)) cache.delete(key);
        }
    }

    function setSlotFromCache(slot, weekYear, week) {
        const key = keyFor(weekYear, week);
        const html = cache.get(key);
        if (!html) return false;
        const paneEl = parsePaneHtml(html);
        if (!paneEl) return false;
        slot.replaceChildren(paneEl);
        attachPreviewHandlers(slot);
        attachDayCardNavigation(slot);
        return true;
    }

    function updateNavHrefs() {
        const prev = addWeeks(currentWeekYear, currentWeek, -1);
        const next = addWeeks(currentWeekYear, currentWeek, 1);
        prevLink.href = `/calendar?view=week&week=${prev.week}&weekYear=${prev.weekYear}`;
        nextLink.href = `/calendar?view=week&week=${next.week}&weekYear=${next.weekYear}`;
    }

    async function warmCache(centerYear, centerWeek) {
        const targets = [];
        for (let d = -2; d <= 2; d++) {
            const t = addWeeks(centerYear, centerWeek, d);
            targets.push(ensureCached(t.weekYear, t.week));
        }
        await Promise.allSettled(targets);
        evictOutsideWindow(centerYear, centerWeek);
    }

    async function renderAdjacentPanes() {
        const prev = addWeeks(currentWeekYear, currentWeek, -1);
        const next = addWeeks(currentWeekYear, currentWeek, 1);
        setSlotFromCache(prevSlot, prev.weekYear, prev.week);
        setSlotFromCache(currentSlot, currentWeekYear, currentWeek);
        setSlotFromCache(nextSlot, next.weekYear, next.week);
        updateNavHrefs();
        updateWeekHeaderAndStrip();
        slider.dataset.week = String(currentWeek);
        slider.dataset.weekYear = String(currentWeekYear);
        refreshHeatmaps();
    }

    function animateTo(offsetPercent) {
        return new Promise((resolve) => {
            if (prefersReducedMotion) {
                track.style.transition = "";
                track.style.transform = `translateX(${offsetPercent}%)`;
                resolve();
                return;
            }

            track.style.transition = "transform 360ms cubic-bezier(0.22, 0.61, 0.36, 1)";
            const onEnd = (e) => {
                if (e.propertyName !== "transform") return;
                track.removeEventListener("transitionend", onEnd);
                resolve();
            };
            track.addEventListener("transitionend", onEnd);
            requestAnimationFrame(() => {
                track.style.transform = `translateX(${offsetPercent}%)`;
            });
        });
    }

    function snapToCenter() {
        track.style.transition = "";
        track.style.transform = "translateX(-33.333333%)";
    }

    async function go(delta) {
        if (isAnimating) return;
        isAnimating = true;
        prevLink.setAttribute("aria-disabled", "true");
        nextLink.setAttribute("aria-disabled", "true");
        prevLink.classList.add("pointer-events-none", "opacity-60");
        nextLink.classList.add("pointer-events-none", "opacity-60");

        const target = addWeeks(currentWeekYear, currentWeek, delta);
        await ensureCached(target.weekYear, target.week);
        await renderAdjacentPanes();

        await animateTo(delta > 0 ? -66.666666 : 0);

        currentWeekYear = target.weekYear;
        currentWeek = target.week;
        await warmCache(currentWeekYear, currentWeek);
        await renderAdjacentPanes();
        snapToCenter();

        const url = new URL(window.location.href);
        url.searchParams.set("view", "week");
        url.searchParams.set("week", String(currentWeek));
        url.searchParams.set("weekYear", String(currentWeekYear));
        window.history.pushState({ week: currentWeek, weekYear: currentWeekYear }, "", url.toString());

        prevLink.removeAttribute("aria-disabled");
        nextLink.removeAttribute("aria-disabled");
        prevLink.classList.remove("pointer-events-none", "opacity-60");
        nextLink.classList.remove("pointer-events-none", "opacity-60");
        isAnimating = false;
    }

    prevLink.addEventListener("click", (e) => {
        e.preventDefault();
        go(-1);
    });

    nextLink.addEventListener("click", (e) => {
        e.preventDefault();
        go(1);
    });

    let pointerStartX = null;
    let pointerActive = false;
    slider.addEventListener("pointerdown", (e) => {
        if (e.target.closest("a, button, input, textarea, select")) return;
        pointerStartX = e.clientX;
        pointerActive = true;
        slider.setPointerCapture?.(e.pointerId);
    });
    slider.addEventListener("pointerup", (e) => {
        if (!pointerActive || pointerStartX == null) return;
        const delta = e.clientX - pointerStartX;
        pointerActive = false;
        pointerStartX = null;
        if (Math.abs(delta) > 60) {
            go(delta < 0 ? 1 : -1);
        }
    });
    slider.addEventListener("pointercancel", () => {
        pointerActive = false;
        pointerStartX = null;
    });

    slider.addEventListener("keydown", (e) => {
        if (e.key === "ArrowLeft") {
            e.preventDefault();
            go(-1);
        } else if (e.key === "ArrowRight") {
            e.preventDefault();
            go(1);
        }
    });

    window.addEventListener("popstate", async () => {
        const params = new URLSearchParams(window.location.search);
        const view = params.get("view") || "week";
        if (view !== "week") return;
        const week = parseInt(params.get("week") || "", 10);
        const weekYear = parseInt(params.get("weekYear") || "", 10);
        if (!Number.isFinite(week) || !Number.isFinite(weekYear)) return;
        currentWeek = week;
        currentWeekYear = weekYear;
        await warmCache(currentWeekYear, currentWeek);
        await renderAdjacentPanes();
        snapToCenter();
    });

    const initialPane = currentSlot.querySelector('[data-week-pane]');
    if (initialPane) {
        const key = keyFor(currentWeekYear, currentWeek);
        cache.set(key, initialPane.outerHTML);
        attachPreviewHandlers(currentSlot);
        attachDayCardNavigation(currentSlot);
    }

    if (track) {
        track.style.willChange = "transform";
    }
    updateWeekHeaderAndStrip();
    warmCache(currentWeekYear, currentWeek).then(() => {
        renderAdjacentPanes();
        snapToCenter();
    });
});
