function toggleEdit(id) {
    const panel = document.getElementById("edit-" + id);
    if (!panel) return;
    panel.classList.toggle("hidden");
}

document.addEventListener('click', (event) => {
    const trigger = event.target.closest('[data-toggle-edit-id]');
    if (!trigger) return;

    toggleEdit(trigger.getAttribute('data-toggle-edit-id'));
});

(function initAddTaskModal() {
    const openBtn = document.getElementById('open-add-task');
    const modal = document.getElementById('add-task-modal');
    const closeBtn = document.getElementById('close-add-task');
    if (!openBtn || !modal || !closeBtn) return;

    const backdrop = modal.querySelector('[data-modal-backdrop]');
    const titleInput = modal.querySelector('input[name="title"]');
    const notesInput = modal.querySelector('textarea[name="notes"]');
    const exerciseInput = modal.querySelector('input[name="exercise"]');
    const modalForms = Array.from(modal.querySelectorAll('form'));
    const dialog = modal.querySelector('[data-add-task-dialog]');
    let returnFocus = openBtn;

    function resetModalSubmitState() {
        modalForms.forEach((form) => {
            if (form.dataset.submitting) {
                delete form.dataset.submitting;
            }

            const submitButtons = Array.from(form.querySelectorAll('button[type="submit"], button:not([type])'));
            submitButtons.forEach((button) => {
                if (button.dataset.originalLabel) {
                    button.textContent = button.dataset.originalLabel;
                    delete button.dataset.originalLabel;
                }
                button.disabled = false;
            });
        });
    }

    function open(event) {
        if (event) {
            event.preventDefault();
            returnFocus = event.currentTarget;
        }
        resetModalSubmitState();
        modal.classList.remove('hidden');
        modal.setAttribute('aria-hidden', 'false');

        const firstInput = modal.querySelector('input[name="title"]');
        if (firstInput) firstInput.focus();
    }

    function close() {
        resetModalSubmitState();
        modal.classList.add('hidden');
        modal.setAttribute('aria-hidden', 'true');
        returnFocus.focus();
    }

    openBtn.addEventListener('click', open);
    document.querySelectorAll('[data-open-add-task-alias]').forEach((trigger) => {
        trigger.addEventListener('click', open);
    });
    document.querySelectorAll('#open-add-task, [data-open-add-task-alias]').forEach((trigger) => {
        trigger.setAttribute('role', 'button');
        trigger.addEventListener('keydown', (event) => {
            if (event.key === ' ') open(event);
        });
    });
    closeBtn.addEventListener('click', close);

    if (backdrop) {
        backdrop.addEventListener('click', close);
    }

    document.addEventListener('keydown', (e) => {
        if (modal.classList.contains('hidden')) return;
        if (e.key === 'Escape') {
            e.preventDefault();
            close();
        } else if (e.key === 'Tab') {
            const focusable = Array.from(modal.querySelectorAll(
                'a[href], button, input, select, textarea, [tabindex]'))
                .filter((element) => !element.disabled && element.tabIndex >= 0 && element.getClientRects().length);
            const first = focusable[0];
            const last = focusable[focusable.length - 1];
            if (e.shiftKey && (document.activeElement === first || !modal.contains(document.activeElement))) {
                e.preventDefault();
                if (last) last.focus();
            } else if (!e.shiftKey && (document.activeElement === last || !modal.contains(document.activeElement))) {
                e.preventDefault();
                if (first) first.focus();
            }
        }
    });

    modal.addEventListener('click', (e) => {
        const btn = e.target && e.target.closest ? e.target.closest('[data-template]') : null;
        if (!btn) return;

        const t = btn.getAttribute('data-title') || '';
        const n = btn.getAttribute('data-notes') || '';
        const ex = (btn.getAttribute('data-exercise') || '').toLowerCase() === 'true';

        if (titleInput) titleInput.value = t;
        if (notesInput) notesInput.value = n;
        if (exerciseInput) exerciseInput.checked = ex;

        if (titleInput) titleInput.focus();
    });

    modalForms.forEach((form) => {
        form.addEventListener('submit', (e) => {
            if (form.dataset.submitting === 'true') {
                e.preventDefault();
                return;
            }

            form.dataset.submitting = 'true';

            const submitButtons = Array.from(form.querySelectorAll('button[type="submit"], button:not([type])'));
            submitButtons.forEach((button) => {
                if (!button.dataset.originalLabel) {
                    button.dataset.originalLabel = button.textContent;
                }

                if (button.textContent.trim().length > 0) {
                    button.textContent = 'Submitting...';
                }

                button.disabled = true;
            });
        });
    });
    if (dialog) {
        dialog.setAttribute('role', 'dialog');
        dialog.setAttribute('aria-modal', 'true');
    }
    modal.classList.add('hidden');
    modal.setAttribute('aria-hidden', 'true');
    modal.dataset.taskModalEnhanced = 'true';
})();

(function initDashboardDeepLinks() {
    const params = new URLSearchParams(window.location.search);
    const focusTask = params.get('focusTask');
    const focusWorkout = params.get('focusWorkout');
    const focusTab = params.get('focusTab');

    if (!focusTask && !focusWorkout && !focusTab) return;

    function activateTab(tabKey) {
        const btn = document.querySelector(`[data-tab-target="${tabKey}"]`);
        if (btn) btn.click();
    }

    function smoothReveal(selector, triggerSelector) {
        const row = document.querySelector(selector);
        if (!row) return;
        row.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'nearest' });
        row.classList.add('ring-2', 'ring-emerald-400', 'ring-offset-2');
        window.setTimeout(() => {
            row.classList.remove('ring-2', 'ring-emerald-400', 'ring-offset-2');
        }, 1800);
        const trigger = row.querySelector(triggerSelector) || row.closest(triggerSelector);
        if (trigger && typeof trigger.click === 'function') {
            window.setTimeout(() => trigger.click(), 240);
        }
    }

    window.addEventListener('load', () => {
        if (focusTab) {
            activateTab(focusTab);
        }

        if (focusTask) {
            activateTab('tasks');
            window.setTimeout(() => {
                smoothReveal(`[data-task-item][data-task-id="${focusTask}"]`, `[data-open-task-drawer][data-task-id="${focusTask}"]`);
            }, 280);
        }

        if (focusWorkout) {
            activateTab('workouts');
            window.setTimeout(() => {
                smoothReveal(`[data-workout-card][data-workout-id="${focusWorkout}"], [data-open-workout-drawer][data-workout-id="${focusWorkout}"]`, `[data-open-workout-drawer][data-workout-id="${focusWorkout}"]`);
            }, 280);
        }
    });
})();

(function initTaskDrawer() {
    const drawer = document.getElementById('task-drawer');
    if (!drawer) return;

    const drawerBody = document.getElementById('task-drawer-body');
    const closeBtn = drawer.querySelector('[data-testid="task-drawer-close"]');
    const backdrop = drawer.querySelector('[data-testid="task-drawer-backdrop"]');

    function open(taskId) {
        if (!drawerBody) return;

        const content = document.getElementById('task-drawer-content-' + taskId);
        if (!content) return;

        drawerBody.innerHTML = content.innerHTML;
        drawerBody.setAttribute('data-open-task-id', String(taskId));
        drawer.classList.remove('hidden');
        drawer.setAttribute('aria-hidden', 'false');

        if (closeBtn) closeBtn.focus();
    }

    function close() {
        if (!drawerBody) return;
        drawer.classList.add('hidden');
        drawer.setAttribute('aria-hidden', 'true');
        drawerBody.removeAttribute('data-open-task-id');
        drawerBody.innerHTML = '<p class="text-sm text-slate-600 dark:text-slate-300">Select a task to view details.</p>';
    }

    document.addEventListener('click', (e) => {
        const trigger = e.target && e.target.closest ? e.target.closest('[data-open-task-drawer]') : null;
        if (!trigger) return;
        const taskId = trigger.getAttribute('data-task-id');
        if (!taskId) return;

        e.preventDefault();
        open(taskId);
    });

    if (closeBtn) closeBtn.addEventListener('click', close);
    if (backdrop) backdrop.addEventListener('click', close);

    drawer.addEventListener('click', (e) => {
        const toggleBtn = e.target && e.target.closest ? e.target.closest('[data-task-adjust-toggle]') : null;
        if (toggleBtn) {
            const wrap = drawerBody.querySelector('[data-task-time-wrap]');
            const display = wrap ? wrap.querySelector('[data-task-time-display]') : null;
            const form = wrap ? wrap.querySelector('[data-task-time-edit-form]') : null;
            if (display && form) {
                display.classList.add('hidden');
                form.classList.remove('hidden');
                const input = form.querySelector('input[name="time"]');
                if (input) {
                    input.dataset.originalTime = input.value || '';
                    input.focus();
                }
            }
            return;
        }

        const cancelBtn = e.target && e.target.closest ? e.target.closest('[data-task-adjust-cancel]') : null;
        if (cancelBtn) {
            const form = cancelBtn.closest('[data-task-time-edit-form]');
            const wrap = cancelBtn.closest('[data-task-time-wrap]');
            const display = wrap ? wrap.querySelector('[data-task-time-display]') : null;
            if (form && display) {
                const input = form.querySelector('input[name="time"]');
                if (input && typeof input.dataset.originalTime === 'string') {
                    input.value = input.dataset.originalTime;
                }
                form.classList.add('hidden');
                display.classList.remove('hidden');
            }
        }
    });

    drawer.addEventListener('submit', (e) => {
        const completeForm = e.target && e.target.closest ? e.target.closest('[data-testid="task-drawer-complete-form"]') : null;
        if (completeForm) {
            e.preventDefault();
            const taskIdInput = completeForm.querySelector('input[name="taskId"]');
            const taskId = taskIdInput ? taskIdInput.value : null;
            if (!taskId) return;
            const rowQuickButton = document.querySelector('[data-task-item][data-task-id="' + taskId + '"] [data-quick-complete]');
            if (rowQuickButton) {
                rowQuickButton.click();
            }
            return;
        }

        const form = e.target && e.target.closest ? e.target.closest('[data-task-time-edit-form]') : null;
        if (!form) return;
        e.preventDefault();

        const taskId = form.getAttribute('data-task-id');
        const timeInput = form.querySelector('input[name="time"]');
        const nextTime = timeInput ? timeInput.value : '';
        if (!taskId || !nextTime) return;

        const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
        const pageDate = document.getElementById('day-main-content')?.getAttribute('data-date') || '';

        fetch('/calendar/task/' + taskId + '/update-time', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                ...(csrfToken && csrfHeader ? { [csrfHeader]: csrfToken } : {}),
                'X-Requested-With': 'XMLHttpRequest'
            },
            body: new URLSearchParams({ date: pageDate, time: nextTime }).toString()
        })
            .then((res) => {
                if (!res.ok) throw new Error('Unable to save time');
                const display = form.closest('[data-task-time-wrap]')?.querySelector('[data-task-time-display]');
                const label = display ? display.querySelector('[data-testid="task-drawer-time"]') : null;
                if (label) label.textContent = nextTime;

                // Sync row and timeline data attributes.
                const row = document.querySelector('[data-task-item][data-task-id="' + taskId + '"]');
                if (row) {
                    row.setAttribute('data-task-time', nextTime);
                    const rowTime = row.querySelector('[data-testid="task-time"]');
                    if (rowTime) rowTime.textContent = nextTime;
                }

                const model = document.getElementById('task-drawer-content-' + taskId);
                if (model) {
                    const modelTime = model.querySelector('[data-testid="task-drawer-time"]');
                    if (modelTime) modelTime.textContent = nextTime;
                }

                form.classList.add('hidden');
                if (display) display.classList.remove('hidden');
            })
            .catch(() => {
                const status = document.createElement('p');
                status.className = 'mt-2 text-xs font-semibold text-red-600 dark:text-red-300';
                status.textContent = 'Could not save time. Please try again.';
                form.appendChild(status);
                window.setTimeout(() => status.remove(), 2000);
            });
    });

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && !drawer.classList.contains('hidden')) {
            close();
        }
    });
})();

(function initWorkoutDrawer() {
    const drawer = document.getElementById('workout-drawer');
    if (!drawer) return;

    const drawerBody = document.getElementById('workout-drawer-body');
    const closeBtn = drawer.querySelector('[data-testid="workout-drawer-close"]');
    const backdrop = drawer.querySelector('[data-testid="workout-drawer-backdrop"]');
    const dialog = drawer.querySelector('[data-workout-dialog]');
    const placeholder = drawerBody ? drawerBody.innerHTML : '';
    let opener = null;
    let previousOverflow = '';

    function open(trigger) {
        if (!drawerBody || !dialog || typeof dialog.showModal !== 'function') return false;
        const card = trigger.closest('[data-workout-card]');
        const kind = trigger.dataset.workoutKind || (card && card.dataset.workoutKind);
        if (kind !== 'session' && kind !== 'occurrence') return false;
        const content = document.getElementById('workout-drawer-content-' + kind + '-' + trigger.dataset.workoutId);
        if (!content) return false;

        opener = trigger;
        previousOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        drawerBody.innerHTML = content.innerHTML;
        drawer.classList.remove('hidden');
        drawer.setAttribute('aria-hidden', 'false');
        window.OneToOneOverlay?.open('calendar-workout');
        dialog.showModal();
        (closeBtn || dialog).focus();
        return true;
    }

    function close(restoreFocus = true) {
        if (drawer.classList.contains('hidden')) return;
        if (dialog && dialog.open) dialog.close();
        drawer.classList.add('hidden');
        drawer.setAttribute('aria-hidden', 'true');
        window.OneToOneOverlay?.release('calendar-workout');
        document.body.style.overflow = previousOverflow;
        if (drawerBody) drawerBody.innerHTML = placeholder;
        if (restoreFocus && opener && opener.isConnected) opener.focus();
        opener = null;
    }

    document.addEventListener('click', (e) => {
        const trigger = e.target && e.target.closest ? e.target.closest('[data-open-workout-drawer]') : null;
        if (!trigger || e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
        if (open(trigger)) e.preventDefault();
    });

    window.OneToOneOverlay?.register('calendar-workout', {
        group: 'modal', close: options => close(options.restoreFocus !== false)
    });
    if (closeBtn) closeBtn.addEventListener('click', () => close());
    if (backdrop) backdrop.addEventListener('click', () => close());
    if (dialog) {
        dialog.addEventListener('cancel', event => { event.preventDefault(); close(); });
        dialog.addEventListener('click', event => {
            if (event.target !== dialog) return;
            const rect = dialog.getBoundingClientRect();
            if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) close();
        });
    }
    document.addEventListener('keydown', (e) => {
        if (drawer.classList.contains('hidden')) return;
        if (e.key === 'Escape') {
            e.preventDefault();
            close();
        } else if (e.key === 'Tab' && dialog) {
            const controls = Array.from(dialog.querySelectorAll('a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex="0"]'))
                .filter(control => control.getClientRects().length && !control.closest('[hidden], [inert]'));
            const first = controls[0];
            const last = controls[controls.length - 1];
            if (!first) {
                e.preventDefault();
                dialog.focus();
            } else if (e.shiftKey && (document.activeElement === first || !dialog.contains(document.activeElement))) {
                e.preventDefault();
                last.focus();
            } else if (!e.shiftKey && (document.activeElement === last || !dialog.contains(document.activeElement))) {
                e.preventDefault();
                first.focus();
            }
        }
    });
})();

(function initFocusReturn() {
    const day = document.getElementById('day-main-content');
    const entry = document.querySelector('[data-enter-calendar-focus]');
    if (!day || !entry) return;
    const key = 'oneToOne.calendarFocusReturn';
    entry.addEventListener('click', event => {
        if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
        try {
            const active = document.querySelector('#day-section-navigation [data-tab-target].is-active');
            sessionStorage.setItem(key, JSON.stringify({ date: day.dataset.date, scrollY: window.scrollY, tab: active ? active.dataset.tabTarget : 'timeline' }));
        } catch (_) { /* The dated exit link remains usable when storage is unavailable. */ }
    });
    window.addEventListener('load', () => {
        if (new URLSearchParams(location.search).get('fromFocus') !== '1') return;
        try {
            const saved = JSON.parse(sessionStorage.getItem(key) || 'null');
            sessionStorage.removeItem(key);
            if (!saved || saved.date !== day.dataset.date || !Number.isFinite(saved.scrollY)) return;
            const tab = Array.from(document.querySelectorAll('#day-section-navigation [data-tab-target]'))
                .find(control => control.dataset.tabTarget === saved.tab);
            if (tab) tab.click();
            requestAnimationFrame(() => {
                entry.focus({ preventScroll: true });
                window.scrollTo({ top: Math.max(0, saved.scrollY), behavior: 'instant' });
            });
        } catch (_) { /* Native date/section navigation is the fallback. */ }
    }, { once: true });
})();

(function initTasksConfigureJump() {
    const btn = document.getElementById('tasks-configure');
    const details = document.getElementById('tasks-config-details');
    if (!btn || !details) return;

    btn.addEventListener('click', () => {
        window.requestAnimationFrame(() => {
            const firstSelect = details.querySelector('select');
            if (firstSelect && typeof firstSelect.focus === 'function') {
                firstSelect.focus();
            }
        });
    });
})();

(function initClickOffDetailsMenus() {
    const menus = Array.from(document.querySelectorAll('details[data-click-off-details]'));
    if (!menus.length) return;

    function closeAll(except) {
        menus.forEach((details) => {
            if (except && details === except) return;
            if (details.open) details.open = false;
        });
    }

    menus.forEach((details) => {
        details.addEventListener('toggle', () => {
            if (!details.open) return;
            closeAll(details);
        });
    });

    document.addEventListener('click', (e) => {
        const target = e.target;
        const clickedInsideAny = menus.some((details) => details.contains(target));
        if (clickedInsideAny) return;
        closeAll();
    });

    document.addEventListener('keydown', (e) => {
        if (e.key !== 'Escape') return;
        closeAll();
    });
})();

(function initFadeInOnView() {
    const items = Array.from(document.querySelectorAll('[data-fade-in]'));
    if (!items.length) return;

    const reduceMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduceMotion) return;

    items.forEach((el) => {
        el.classList.add('transition-all', 'duration-500', 'ease-out', 'will-change-transform');

        const rect = el.getBoundingClientRect();
        const belowFold = rect.top > window.innerHeight * 0.9;
        if (belowFold) {
            el.classList.add('opacity-0', 'translate-y-2');
        }
    });

    const observer = new IntersectionObserver(
        (entries) => {
            entries.forEach((entry) => {
                if (!entry.isIntersecting) return;
                const el = entry.target;
                el.classList.remove('opacity-0', 'translate-y-2');
                el.classList.add('opacity-100', 'translate-y-0');
                observer.unobserve(el);
            });
        },
        { threshold: 0.12, rootMargin: '0px 0px -10% 0px' }
    );

    items.forEach((el) => observer.observe(el));
})();

(function initDayViewAjaxPreferenceForms() {
    const forms = Array.from(document.querySelectorAll('form[data-ajax-form]'));
    if (!forms.length) return;

    function postFormWithoutReload(form) {
        const method = (form.getAttribute('method') || 'post').toUpperCase();
        const action = form.getAttribute('action');
        if (!action) return Promise.reject(new Error('Missing form action'));

        const formData = new FormData(form);
        const body = new URLSearchParams();
        for (const [key, value] of formData.entries()) {
            body.append(key, String(value));
        }

        return fetch(action, {
            method,
            headers: {
                'X-Requested-With': 'XMLHttpRequest',
                'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'
            },
            body: body.toString()
        });
    }

    function captureOriginalOrder(selector, attrName) {
        const items = Array.from(document.querySelectorAll(selector));
        items.forEach((el, idx) => {
            if (!el.dataset[attrName]) {
                el.dataset[attrName] = String(idx);
            }
        });
    }

    function normalizeText(s) {
        return (s || '').toString().trim().toLowerCase();
    }

    function sortByOrdering(items, ordering) {
        if (ordering === 'ALPHABETICAL') {
            return items.sort((a, b) => {
                const at = normalizeText(a.dataset.taskTitle);
                const bt = normalizeText(b.dataset.taskTitle);
                if (at < bt) return -1;
                if (at > bt) return 1;
                const atime = normalizeText(a.dataset.taskTime);
                const btime = normalizeText(b.dataset.taskTime);
                return atime.localeCompare(btime);
            });
        }

        // Chronological (time asc; empty time last; title tiebreak)
        return items.sort((a, b) => {
            const at = (a.dataset.taskTime || '').trim();
            const bt = (b.dataset.taskTime || '').trim();
            const aHas = at.length > 0;
            const bHas = bt.length > 0;
            if (aHas && bHas) {
                if (at < bt) return -1;
                if (at > bt) return 1;
            } else if (aHas && !bHas) {
                return -1;
            } else if (!aHas && bHas) {
                return 1;
            }
            const an = normalizeText(a.dataset.taskTitle);
            const bn = normalizeText(b.dataset.taskTitle);
            if (an < bn) return -1;
            if (an > bn) return 1;
            return 0;
        });
    }

    function applyTaskPreferences(form) {
        const tasksRoot = document.getElementById('tasks-list-root');
        if (!tasksRoot) return;

        const ordering = form.querySelector('select[name="ordering"]')?.value || 'CHRONOLOGICAL';
        const layout = form.querySelector('select[name="layout"]')?.value || 'COMBINED_LIST';

        const allTaskItems = Array.from(tasksRoot.querySelectorAll('[data-task-item]'));
        if (!allTaskItems.length) return;

        // Sort items based on ordering preference
        sortByOrdering(allTaskItems, ordering);

        const combinedScrollClass = 'mt-4 max-h-[32rem] overflow-x-hidden overflow-y-auto rounded-xl border border-slate-200 bg-white scrollbar-thin scrollbar-track-transparent scrollbar-thumb-slate-300 dark:border-slate-800 dark:bg-slate-950 dark:scrollbar-thumb-slate-700';
        const separatedScrollClass = 'mt-3 max-h-[24rem] overflow-x-hidden overflow-y-auto rounded-xl border border-slate-200 bg-white scrollbar-thin scrollbar-track-transparent scrollbar-thumb-slate-300 dark:border-slate-800 dark:bg-slate-950 dark:scrollbar-thumb-slate-700';
        const headingClass = 'mt-6 text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400';

        // Clear and rebuild task list
        tasksRoot.innerHTML = '';

        if (layout === 'SEPARATED_BY_CATEGORY') {
            // Create headers and containers for separated layout
            const otherHeading = document.createElement('h3');
            otherHeading.className = headingClass;
            otherHeading.textContent = 'Other tasks';

            const otherScroll = document.createElement('div');
            otherScroll.className = separatedScrollClass;
            otherScroll.setAttribute('data-task-list', 'other');

            const exerciseHeading = document.createElement('h3');
            exerciseHeading.className = headingClass;
            exerciseHeading.textContent = 'Exercise tasks';

            const exerciseScroll = document.createElement('div');
            exerciseScroll.className = separatedScrollClass;
            exerciseScroll.setAttribute('data-task-list', 'exercise');

            // Distribute tasks into exercise vs other
            allTaskItems.forEach((item) => {
                const isExercise = (item.dataset.taskExercise || '').toLowerCase() === 'true';
                (isExercise ? exerciseScroll : otherScroll).appendChild(item);
            });

            // Only show headers if they have content
            if (otherScroll.children.length > 0) {
                tasksRoot.appendChild(otherHeading);
                tasksRoot.appendChild(otherScroll);
            }
            if (exerciseScroll.children.length > 0) {
                tasksRoot.appendChild(exerciseHeading);
                tasksRoot.appendChild(exerciseScroll);
            }
        } else {
            // Combined list layout
            const combinedScroll = document.createElement('div');
            combinedScroll.className = combinedScrollClass;
            combinedScroll.setAttribute('data-task-list', 'combined');
            allTaskItems.forEach((item) => combinedScroll.appendChild(item));
            tasksRoot.appendChild(combinedScroll);
        }
    }

    function applyWorkoutPreferences(form) {
        const workoutsRoot = document.getElementById('workouts-list-root');
        if (!workoutsRoot) return;

        const ordering = form.querySelector('select[name="ordering"]')?.value || 'SCHEDULE_ORDER';
        const items = Array.from(workoutsRoot.querySelectorAll('[data-workout-card]'));
        if (!items.length) return;

        captureOriginalOrder('#workouts-list-root [data-workout-card]', 'originalIndex');

        if (ordering === 'ALPHABETICAL') {
            items.sort((a, b) => {
                const an = normalizeText(a.dataset.workoutName);
                const bn = normalizeText(b.dataset.workoutName);
                if (an < bn) return -1;
                if (an > bn) return 1;
                return 0;
            });
        } else {
            // Restore schedule order
            items.sort((a, b) => {
                const ai = parseInt(a.dataset.originalIndex || '0', 10);
                const bi = parseInt(b.dataset.originalIndex || '0', 10);
                return ai - bi;
            });
        }

        // Reorder items in DOM
        items.forEach((el) => workoutsRoot.appendChild(el));
    }

    // Capture initial order so we can restore schedule order.
    captureOriginalOrder('#workouts-list-root [data-workout-card]', 'originalIndex');

    forms.forEach((form) => {
        form.addEventListener('submit', (e) => {
            e.preventDefault();

            postFormWithoutReload(form)
                .then((res) => {
                    if (!res.ok) return;

                    const action = form.getAttribute('action') || '';
                    if (action.includes('/task-preferences')) {
                        applyTaskPreferences(form);
                    } else if (action.includes('/workout-preferences')) {
                        applyWorkoutPreferences(form);
                    }
                })
                .catch(() => {
                    // fail silently; non-js fallback still works on normal submits
                });
        });
    });

    // Initialize display on page load
    document.addEventListener('DOMContentLoaded', () => {
        const tasksConfig = document.querySelector('form[action*="/task-preferences"]');
        if (tasksConfig) {
            applyTaskPreferences(tasksConfig);
        }
        const workoutConfig = document.querySelector('form[action*="/workout-preferences"]');
        if (workoutConfig) {
            applyWorkoutPreferences(workoutConfig);
        }
    });
})();
