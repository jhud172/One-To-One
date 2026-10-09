// Day View Enhancements - Smart, Interactive Features

(function() {
    'use strict';
    
    // ==================== Smart Completion Animations ====================
    function animateCompletionDots() {
        const dots = document.querySelectorAll('.completion-dot');
        dots.forEach((dot, index) => {
            dot.style.animationDelay = `${index * 0.05}s`;
        });
    }
    
    // ==================== Live Digital Clock ====================
    function initLiveClock() {
        const clockElement = document.getElementById('live-clock');
        if (!clockElement) return;
        
        function updateClock() {
            const now = new Date();
            let hours = now.getHours();
            const minutes = now.getMinutes();
            const seconds = now.getSeconds();
            const ampm = hours >= 12 ? 'PM' : 'AM';
            
            // Convert to 12-hour format
            hours = hours % 12;
            hours = hours ? hours : 12; // 0 should be 12
            
            // Pad with zeros
            const hoursStr = String(hours).padStart(2, '0');
            const minutesStr = String(minutes).padStart(2, '0');
            const secondsStr = String(seconds).padStart(2, '0');
            
            clockElement.textContent = `${hoursStr}:${minutesStr}:${secondsStr} ${ampm}`;
        }
        
        // Update immediately
        updateClock();
        
        // Update every second
        setInterval(updateClock, 1000);
    }
    
    // ==================== Quick Stats Display ====================
    function initQuickStats() {
        const tasksPanel = document.querySelector('[data-testid="tasks-panel"]');
        const workoutsPanel = document.querySelector('[data-testid="workouts-panel"]');
        
        if (!tasksPanel && !workoutsPanel) return;
        
        // Count tasks
        const totalTasks = document.querySelectorAll('[data-task-item]').length;
        const completedTasks = document.querySelectorAll('[data-task-item][data-task-completed="true"]').length || 
                              document.querySelectorAll('[data-task-item].bg-green-50').length;
        const lateTasks = document.querySelectorAll('[data-task-item][data-task-late="true"]').length ||
                         document.querySelectorAll('[data-task-item].bg-red-50').length;
        
        // Add quick stats if there are tasks
        if (totalTasks > 0 && tasksPanel) {
            const header = tasksPanel.querySelector('header');
            if (header && !document.getElementById('task-quick-stats')) {
                const statsDiv = document.createElement('div');
                statsDiv.id = 'task-quick-stats';
                statsDiv.className = 'mt-2 flex gap-2 text-xs';
                statsDiv.innerHTML = `
                    <span class="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2 py-0.5 text-slate-700 dark:bg-slate-800 dark:text-slate-300">
                        <svg class="h-3 w-3" fill="currentColor" viewBox="0 0 20 20">
                            <path d="M9 2a1 1 0 000 2h2a1 1 0 100-2H9z"/>
                            <path fill-rule="evenodd" d="M4 5a2 2 0 012-2 3 3 0 003 3h2a3 3 0 003-3 2 2 0 012 2v11a2 2 0 01-2 2H6a2 2 0 01-2-2V5zm3 4a1 1 0 000 2h.01a1 1 0 100-2H7zm3 0a1 1 0 000 2h3a1 1 0 100-2h-3zm-3 4a1 1 0 100 2h.01a1 1 0 100-2H7zm3 0a1 1 0 100 2h3a1 1 0 100-2h-3z" clip-rule="evenodd"/>
                        </svg>
                        ${totalTasks} total
                    </span>
                    ${completedTasks > 0 ? `
                        <span class="inline-flex items-center gap-1 rounded-full bg-green-100 px-2 py-0.5 text-green-800 dark:bg-green-950/30 dark:text-green-200">
                            <svg class="h-3 w-3" fill="currentColor" viewBox="0 0 20 20">
                                <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/>
                            </svg>
                            ${completedTasks} done
                        </span>
                    ` : ''}
                    ${lateTasks > 0 ? `
                        <span class="inline-flex items-center gap-1 rounded-full bg-red-100 px-2 py-0.5 text-red-800 dark:bg-red-950/30 dark:text-red-200">
                            <svg class="h-3 w-3" fill="currentColor" viewBox="0 0 20 20">
                                <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd"/>
                            </svg>
                            ${lateTasks} late
                        </span>
                    ` : ''}
                `;
                header.appendChild(statsDiv);
            }
        }
    }
    
    // ==================== Time-of-Day Theme ====================
    var TIME_THEME_KEY = 'day-time-theme-enabled';

    function isTimeThemeEnabled() {
        var stored = localStorage.getItem(TIME_THEME_KEY);
        // Default on if not set
        return stored !== 'false';
    }

    function applyTimeOfDayTheme() {
        var wrapper = document.getElementById('day-main-content');
        if (wrapper) {
            wrapper.setAttribute('data-time-theme-enabled', isTimeThemeEnabled() ? 'true' : 'false');
        }

        if (!isTimeThemeEnabled()) {
            // Remove any existing time theme
            document.documentElement.removeAttribute('data-time-theme');
            var header = document.querySelector('[data-testid="day-hub-header"]');
            if (header) header.removeAttribute('data-time-theme');
            return;
        }
        var hour = new Date().getHours();
        var theme;
        if (hour >= 5 && hour < 12) {
            theme = 'morning';
        } else if (hour >= 12 && hour < 17) {
            theme = 'afternoon';
        } else if (hour >= 17 && hour < 21) {
            theme = 'evening';
        } else {
            theme = 'night';
        }
        document.documentElement.setAttribute('data-time-theme', theme);
        var header = document.querySelector('[data-testid="day-hub-header"]');
        if (header) header.setAttribute('data-time-theme', theme);
    }

    // ==================== Time-Theme Toggle Button ====================
    function initTimeThemeToggle() {
        var btn = document.getElementById('time-theme-toggle-btn');
        if (!btn) return;
        var enabled = isTimeThemeEnabled();
        btn.setAttribute('aria-pressed', String(enabled));
        btn.querySelector('.ttt-label').textContent = enabled ? 'Time theme: on' : 'Time theme: off';

        btn.addEventListener('click', function() {
            var next = !isTimeThemeEnabled();
            localStorage.setItem(TIME_THEME_KEY, String(next));
            btn.setAttribute('aria-pressed', String(next));
            btn.querySelector('.ttt-label').textContent = next ? 'Time theme: on' : 'Time theme: off';
            applyTimeOfDayTheme();
            updateTimeGreeting();
        });
    }

    // ==================== Smart Time Greetings ====================
    function getGreetingContext() {
        const wrapper = document.getElementById('day-main-content');
        const selectedDateRaw = wrapper ? wrapper.getAttribute('data-date') : null;
        const selectedDate = selectedDateRaw ? new Date(selectedDateRaw + 'T12:00:00') : new Date();
        const now = new Date();
        const hour = now.getHours();

        let period = 'afternoon';
        if (hour >= 5 && hour < 12) period = 'morning';
        else if (hour >= 12 && hour < 17) period = 'afternoon';
        else if (hour >= 17 && hour < 21) period = 'evening';
        else period = 'night';

        const month = selectedDate.getMonth() + 1;
        const seasonal = (month <= 2 || month === 12) ? 'cloudy' : (month >= 6 && month <= 8) ? 'clear' : 'partly-cloudy';
        const weather = window.__dayWeather || seasonal;

        return { period, weather, themeEnabled: isTimeThemeEnabled() };
    }

    function buildGreetingModel(ctx) {
        const weatherTone = {
            clear: { icon: '☀️', accent: 'from-amber-300/30 to-orange-300/20', sub: 'Clear conditions. Great window for high-energy work.' },
            sunny: { icon: '🌤️', accent: 'from-amber-300/30 to-orange-300/20', sub: 'Sunlight is on your side. Keep momentum steady.' },
            rainy: { icon: '🌧️', accent: 'from-sky-300/25 to-slate-300/15', sub: 'Rainy conditions. Ideal for focused indoor sessions.' },
            cloudy: { icon: '☁️', accent: 'from-slate-300/25 to-slate-400/15', sub: 'Cloud cover today. Keep your pace deliberate and calm.' },
            'partly-cloudy': { icon: '⛅', accent: 'from-sky-300/25 to-amber-300/20', sub: 'Mixed skies. A good day for balanced effort.' }
        };

        const periodTone = {
            morning: 'Good morning',
            afternoon: 'Good afternoon',
            evening: 'Good evening',
            night: 'Good night'
        };

        const weatherData = weatherTone[ctx.weather] || weatherTone['partly-cloudy'];
        return {
            title: periodTone[ctx.period] || periodTone.afternoon,
            subtitle: weatherData.sub,
            icon: weatherData.icon,
            accent: weatherData.accent,
            themeEnabled: ctx.themeEnabled
        };
    }

    function updateTimeGreeting() {
        const header = document.querySelector('[data-testid="day-hub-header"]');
        const slot = document.getElementById('time-greeting-slot');
        if (!header || !slot) return;

        const model = buildGreetingModel(getGreetingContext());

        let greetingDiv = document.getElementById('time-greeting');
        if (!greetingDiv) {
            greetingDiv = document.createElement('div');
            greetingDiv.id = 'time-greeting';
            slot.appendChild(greetingDiv);
        }

        greetingDiv.className = 'mb-4 rounded-xl border border-white/50 dark:border-white/10 bg-gradient-to-r ' + model.accent + ' px-3 py-2 animate-fade-in';
        greetingDiv.innerHTML =
            '<div class="flex items-center gap-3">'
            + '<span class="text-3xl ' + (model.themeEnabled ? 'animate-bounce-subtle' : '') + '" aria-hidden="true">' + model.icon + '</span>'
            + '<div>'
            + '<p class="text-lg font-bold tracking-tight text-slate-900 dark:text-slate-100">' + model.title + '</p>'
            + '<p class="text-xs font-medium text-slate-600 dark:text-slate-300">' + model.subtitle + '</p>'
            + '</div>'
            + '</div>';
    }
    
    // ==================== Motivational Messages ====================
    function addMotivationalMessage() {
        const completionSection = document.querySelector('[data-testid="day-hub-header"] section[aria-label="Day completion"]');
        if (!completionSection) return;
        
        const percentageText = completionSection.querySelector('p.text-sm.font-semibold span');
        if (!percentageText) return;
        
        const percentage = parseInt(percentageText.textContent) || 0;
        
        const messages = [
            { range: [0, 20], text: "Let's get started! 💪", color: "text-slate-600 dark:text-slate-400" },
            { range: [21, 40], text: "Making progress! 🎯", color: "text-blue-600 dark:text-blue-400" },
            { range: [41, 60], text: "Halfway there! 🚀", color: "text-indigo-600 dark:text-indigo-400" },
            { range: [61, 80], text: "Almost done! ⭐", color: "text-amber-600 dark:text-amber-400" },
            { range: [81, 99], text: "Final stretch! 🔥", color: "text-orange-600 dark:text-orange-400" },
            { range: [100, 100], text: "Perfect day! 🎉", color: "text-emerald-600 dark:text-emerald-400" }
        ];
        
        const message = messages.find(m => percentage >= m.range[0] && percentage <= m.range[1]);
        
        if (message && !document.getElementById('motivation-message')) {
            const msgDiv = document.createElement('p');
            msgDiv.id = 'motivation-message';
            msgDiv.className = `mt-2 text-xs font-medium ${message.color}`;
            msgDiv.textContent = message.text;
            completionSection.appendChild(msgDiv);
        }
    }

    function collectDayItems() {
        const taskItems = Array.from(document.querySelectorAll('[data-task-item]')).map((el) => {
            const status = el.getAttribute('data-task-status') || 'todo';
            return {
                title: el.getAttribute('data-task-title') || 'Task',
                time: el.getAttribute('data-task-time') || '',
                completed: status === 'done',
                type: 'task'
            };
        });

        const workoutItems = Array.from(document.querySelectorAll('[data-workout-item], [data-occurrence-item]')).map((el) => {
            const title = el.getAttribute('data-workout-name') || el.getAttribute('data-occurrence-title') || 'Workout';
            const completed = (el.getAttribute('data-workout-completed') || el.getAttribute('data-completed') || 'false') === 'true';
            const time = el.getAttribute('data-workout-time') || el.getAttribute('data-occurrence-time') || '';
            return { title, completed, time, type: 'workout' };
        });

        return taskItems.concat(workoutItems);
    }

    function updateTimedFocusCard() {
        const section = document.querySelector('[data-testid="timed-focus"]');
        if (!section) return;

        const valueEl = document.getElementById('timed-focus-value');
        const chipEl = document.getElementById('timed-focus-chip');
        const supportEl = document.getElementById('timed-focus-support');
        if (!valueEl || !chipEl || !supportEl) return;

        const wrapper = document.getElementById('day-main-content');
        const selectedDate = wrapper ? (wrapper.getAttribute('data-date') || '') : '';
        const todayDate = wrapper ? (wrapper.getAttribute('data-today') || '') : '';
        const isToday = selectedDate === todayDate;
        const now = new Date();
        const nowMinutes = now.getHours() * 60 + now.getMinutes();

        const items = collectDayItems();
        const hasAnyPlanned = items.length > 0;
        const incompleteItems = items.filter((item) => !item.completed);

        const timedCandidates = incompleteItems
            .filter((item) => item.time)
            .map((item) => {
                const parts = item.time.split(':').map(Number);
                const minutes = parts.length >= 2 ? parts[0] * 60 + parts[1] : -1;
                return Object.assign({}, item, { minutes });
            })
            .filter((item) => item.minutes >= 0)
            .filter((item) => !isToday || item.minutes >= nowMinutes)
            .sort((a, b) => a.minutes - b.minutes);

        const untimedCandidates = incompleteItems.filter((item) => !item.time);

        if (timedCandidates.length > 0) {
            const next = timedCandidates[0];
            valueEl.textContent = next.title;
            chipEl.textContent = 'Timed next';
            supportEl.textContent = (next.type === 'workout' ? 'Workout' : 'Task') + ' at ' + next.time;
            section.setAttribute('data-focus-state', 'timed-next');
            return;
        }

        if (untimedCandidates.length > 0) {
            const nextUntimed = untimedCandidates[0];
            valueEl.textContent = nextUntimed.title;
            chipEl.textContent = 'Next up';
            supportEl.textContent = 'No timed items left, showing next incomplete ' + nextUntimed.type + '.';
            section.setAttribute('data-focus-state', 'untimed-next');
            return;
        }

        const selected = selectedDate ? new Date(selectedDate + 'T12:00:00') : new Date();
        const today = todayDate ? new Date(todayDate + 'T12:00:00') : new Date();
        const isPastDay = selected < today;
        const allCompleted = hasAnyPlanned && incompleteItems.length === 0;
        const shouldCelebrate = allCompleted && (isPastDay || isToday);

        chipEl.textContent = 'Clock clear';
        section.setAttribute('data-focus-state', shouldCelebrate ? 'all-complete' : 'empty');
        if (shouldCelebrate) {
            valueEl.textContent = 'All clear';
            supportEl.textContent = 'Great work, everything planned for this day is complete.';
        } else if (hasAnyPlanned) {
            valueEl.textContent = 'No pending items';
            supportEl.textContent = 'There are no incomplete items left to focus on right now.';
        } else {
            valueEl.textContent = 'Nothing scheduled';
            supportEl.textContent = 'No tasks or workouts were planned for this day.';
        }
    }

    function updateCompletionFeedback(root, total, remaining, tasksLeft, workoutsLeft) {
        const format = (copy, values) => (copy || '').replace(/\{(\d+)\}/g,
            (match, index) => values[Number(index)] ?? match);
        const summary = root.querySelector('[data-progress-summary]');
        const breakdown = root.querySelector('[data-progress-breakdown]');
        if (summary) summary.textContent = total === 0 ? root.dataset.emptyCopy
            : remaining > 0 ? format(root.dataset.remainingCopy, [remaining]) : root.dataset.completeCopy;
        if (breakdown) breakdown.textContent = format(root.dataset.breakdownCopy, [tasksLeft, workoutsLeft]);
        const ring = document.querySelector('.day-progress-ring-wrap');
        if (ring) {
            const done = total - remaining;
            const percentage = total ? Math.floor(done * 100 / total) : 0;
            const day = document.getElementById('day-main-content');
            const status = total === 0 ? 'empty' : remaining === 0 ? 'complete' : done > 0
                ? (day.dataset.date === day.dataset.today ? 'active' : 'partial')
                : day.dataset.date < day.dataset.today ? 'unfinished' : 'planned';
            ring.setAttribute('aria-label', format(ring.dataset.summaryCopy, [done, total, percentage]));
            ring.querySelector('.ring-pct').textContent = `${percentage}%`;
            ring.querySelector('.ring-label').textContent = ring.getAttribute('data-status-' + status);
            const circle = ring.querySelector('.ring-progress');
            circle.setAttribute('stroke-dashoffset', String(314.159 * (1 - percentage / 100)));
            circle.classList.remove('ring-gold', 'ring-blue', 'ring-emerald', 'ring-red', 'ring-default');
            circle.classList.add(({complete: 'ring-gold', active: 'ring-blue', partial: 'ring-emerald',
                unfinished: 'ring-red'})[status] || 'ring-default');
        }
        document.dispatchEvent(new Event('calendar:completion-change'));
    }

    function refreshMainCompletionProgress() {
        const root = document.querySelector('[data-main-progress]');
        const fill = document.getElementById('day-main-progress-fill');
        if (!root || !fill) return;

        const tasks = Array.from(document.querySelectorAll('[data-task-item]'));
        const totalTasks = tasks.length;
        const doneTasks = tasks.filter((task) => (task.getAttribute('data-task-status') || '') === 'done').length;

        const workoutItems = Array.from(document.querySelectorAll('[data-workout-item], [data-occurrence-item]'));
        const totalWorkouts = workoutItems.length;
        const doneWorkouts = workoutItems.filter((item) => {
            const val = item.getAttribute('data-workout-completed') || item.getAttribute('data-completed') || 'false';
            return val === 'true';
        }).length;

        const total = totalTasks + totalWorkouts;
        const done = doneTasks + doneWorkouts;
        const pct = total > 0 ? Math.floor((done / total) * 100) : 0;
        const tasksLeft = Math.max(totalTasks - doneTasks, 0);
        const workoutsLeft = Math.max(totalWorkouts - doneWorkouts, 0);
        const remaining = tasksLeft + workoutsLeft;

        root.setAttribute('data-progress-pct', String(pct));
        root.setAttribute('data-progress-tasks-left', String(tasksLeft));
        root.setAttribute('data-progress-workouts-left', String(workoutsLeft));
        root.setAttribute('data-progress-remaining', String(remaining));
        fill.style.width = pct + '%';

        const track = root.querySelector('.day-main-progress-track');
        if (track) {
            track.setAttribute('aria-valuenow', String(pct));
        }

        const label = document.getElementById('day-main-progress-label');
        if (label) {
            label.textContent = done + '/' + total + ' completed (' + pct + '%)';
        }

        const summaryChip = document.querySelector('[data-testid="day-completion-summary"]');
        if (summaryChip) {
            summaryChip.textContent = done + '/' + total + ' completed (' + pct + '%)';
        }

        updateCompletionFeedback(root, total, remaining, tasksLeft, workoutsLeft);

        const overviewProgress = document.getElementById('overview-progress-copy');
        if (overviewProgress) {
            overviewProgress.textContent = done + '/' + total + ' completed (' + pct + '%)';
        }

        const overviewRemainingTotal = document.getElementById('overview-remaining-total');
        if (overviewRemainingTotal) {
            overviewRemainingTotal.textContent = remaining + ' left';
        }

        const overviewRemainingBreakdown = document.getElementById('overview-remaining-breakdown');
        if (overviewRemainingBreakdown) {
            overviewRemainingBreakdown.textContent = tasksLeft + ' tasks · ' + workoutsLeft + ' workouts';
        }

        const dayStatDone = document.getElementById('day-stat-done');
        if (dayStatDone) {
            dayStatDone.textContent = String(done);
        }

        const dayStatTotal = document.getElementById('day-stat-total');
        if (dayStatTotal) {
            dayStatTotal.textContent = String(total);
        }

        const dayStatTasksLeft = document.getElementById('day-stat-tasks-left');
        if (dayStatTasksLeft) {
            dayStatTasksLeft.textContent = String(tasksLeft);
        }

        const dayStatWorkoutsLeft = document.getElementById('day-stat-workouts-left');
        if (dayStatWorkoutsLeft) {
            dayStatWorkoutsLeft.textContent = String(workoutsLeft);
        }

        const overviewNext = document.getElementById('overview-next-priority');
        if (overviewNext) {
            if (tasksLeft > 0) {
                overviewNext.textContent = 'Clear your next task block.';
            } else if (workoutsLeft > 0) {
                overviewNext.textContent = 'Complete your next workout block.';
            } else {
                overviewNext.textContent = total === 0 ? root.dataset.emptyCopy : root.dataset.completeCopy;
            }
        }

        renderDayInsights();
    }
    
    // ==================== Smart Task Highlighting ====================
    function highlightUpcomingTasks() {
        const now = new Date();
        const currentHour = now.getHours();
        const currentMinute = now.getMinutes();
        const currentTime = currentHour * 60 + currentMinute;
        
        document.querySelectorAll('[data-task-item]').forEach(task => {
            const timeStr = task.getAttribute('data-task-time');
            if (!timeStr) return;
            
            const [hours, minutes] = timeStr.split(':').map(Number);
            const taskTime = hours * 60 + minutes;
            const timeDiff = taskTime - currentTime;
            
            // Highlight tasks within next 30 minutes
            if (timeDiff > 0 && timeDiff <= 30) {
                task.classList.add('ring-2', 'ring-blue-400/50', 'dark:ring-blue-500/50');
                
                if (!task.querySelector('.upcoming-badge')) {
                    const badge = document.createElement('span');
                    badge.className = 'upcoming-badge absolute top-2 right-2 inline-flex items-center gap-1 rounded-full bg-blue-100 px-2 py-0.5 text-xs font-semibold text-blue-800 dark:bg-blue-950/40 dark:text-blue-200';
                    badge.innerHTML = `
                        <svg class="h-3 w-3" fill="currentColor" viewBox="0 0 20 20">
                            <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z" clip-rule="evenodd"/>
                        </svg>
                        Soon
                    `;
                    const taskItem = task.querySelector('[data-testid="task-list-item"]');
                    if (taskItem) {
                        taskItem.style.position = 'relative';
                        taskItem.appendChild(badge);
                    }
                }
            }
        });
    }
    
    // ==================== Smooth Scroll to Current Time ====================
    function scrollToCurrentTime() {
        const now = new Date();
        const currentHour = now.getHours();
        
        // Only auto-scroll during waking hours
        if (currentHour < 6 || currentHour > 23) return;
        
        const tasks = Array.from(document.querySelectorAll('[data-task-item]'));
        const currentTimeTask = tasks.find(task => {
            const timeStr = task.getAttribute('data-task-time');
            if (!timeStr) return false;
            
            const [hours] = timeStr.split(':').map(Number);
            return hours === currentHour;
        });
        
        if (currentTimeTask) {
            setTimeout(() => {
                currentTimeTask.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }, 500);
        }
    }

    function initMainProgressFill() {
        const root = document.querySelector('[data-main-progress]');
        const fill = document.getElementById('day-main-progress-fill');
        if (!root || !fill) {
            return;
        }

        const pct = Number(root.getAttribute('data-progress-pct') || 0);
        fill.style.width = pct + '%';
    }
    
    // ==================== Keyboard Shortcuts ====================
    function initKeyboardShortcuts() {
        document.addEventListener('keydown', (e) => {
            // Don't trigger shortcuts when typing in inputs
            if (e.target.matches('input, textarea, select')) return;
            
            // Ctrl/Cmd + N = New Task
            if ((e.ctrlKey || e.metaKey) && e.key === 'n') {
                e.preventDefault();
                const addTaskBtn = document.getElementById('open-add-task');
                if (addTaskBtn) addTaskBtn.click();
            }
            
            // ? = Show keyboard shortcuts help
            if (e.key === '?') {
                e.preventDefault();
                showKeyboardHelp();
            }
        });
    }
    
    function showKeyboardHelp() {
        if (document.getElementById('keyboard-help-modal')) return;
        
        const modal = document.createElement('div');
        modal.id = 'keyboard-help-modal';
        modal.className = 'fixed inset-0 z-[70] flex items-center justify-center bg-slate-950/70 backdrop-blur-sm';
        modal.innerHTML = `
            <div class="mx-4 w-full max-w-md rounded-2xl border border-slate-200 bg-white p-6 shadow-xl dark:border-slate-800 dark:bg-slate-950">
                <div class="flex items-start justify-between">
                    <h3 class="text-lg font-semibold text-slate-900 dark:text-slate-100">Keyboard Shortcuts</h3>
                    <button class="close-help-btn rounded-lg bg-slate-100 px-3 py-1 text-sm font-medium text-slate-900 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-100 dark:hover:bg-slate-700">Close</button>
                </div>
                <div class="mt-4 space-y-2 text-sm">
                    <div class="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 dark:bg-slate-900">
                        <span class="text-slate-700 dark:text-slate-200">New Task</span>
                        <kbd class="rounded bg-white px-2 py-1 text-xs font-semibold text-slate-900 shadow dark:bg-slate-800 dark:text-slate-100">Ctrl+N</kbd>
                    </div>
                    <div class="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 dark:bg-slate-900">
                        <span class="text-slate-700 dark:text-slate-200">Previous Day</span>
                        <kbd class="rounded bg-white px-2 py-1 text-xs font-semibold text-slate-900 shadow dark:bg-slate-800 dark:text-slate-100">←</kbd>
                    </div>
                    <div class="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 dark:bg-slate-900">
                        <span class="text-slate-700 dark:text-slate-200">Next Day</span>
                        <kbd class="rounded bg-white px-2 py-1 text-xs font-semibold text-slate-900 shadow dark:bg-slate-800 dark:text-slate-100">→</kbd>
                    </div>
                    <div class="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 dark:bg-slate-900">
                        <span class="text-slate-700 dark:text-slate-200">Show Help</span>
                        <kbd class="rounded bg-white px-2 py-1 text-xs font-semibold text-slate-900 shadow dark:bg-slate-800 dark:text-slate-100">?</kbd>
                    </div>
                    <div class="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 dark:bg-slate-900">
                        <span class="text-slate-700 dark:text-slate-200">Switch tabs (Tasks/Workouts/Timeline/Overview)</span>
                        <div class="flex gap-1">
                            <kbd class="rounded bg-white px-2 py-1 text-xs font-semibold text-slate-900 shadow dark:bg-slate-800 dark:text-slate-100">1</kbd>
                            <kbd class="rounded bg-white px-2 py-1 text-xs font-semibold text-slate-900 shadow dark:bg-slate-800 dark:text-slate-100">2</kbd>
                            <kbd class="rounded bg-white px-2 py-1 text-xs font-semibold text-slate-900 shadow dark:bg-slate-800 dark:text-slate-100">3</kbd>
                            <kbd class="rounded bg-white px-2 py-1 text-xs font-semibold text-slate-900 shadow dark:bg-slate-800 dark:text-slate-100">4</kbd>
                        </div>
                    </div>
                </div>
            </div>
        `;
        
        document.body.appendChild(modal);
        
        function closeModal() {
            modal.remove();
            document.removeEventListener('keydown', handleEscape);
        }
        
        function handleEscape(e) {
            if (e.key === 'Escape') {
                closeModal();
            }
        }
        
        modal.addEventListener('click', (e) => {
            if (e.target === modal) closeModal();
        });
        
        const closeBtn = modal.querySelector('.close-help-btn');
        if (closeBtn) {
            closeBtn.addEventListener('click', closeModal);
        }
        
        document.addEventListener('keydown', handleEscape);
    }
    
    // ==================== Focus Mode ====================
    function initFocusMode() {
        const btn = document.getElementById('focus-mode-btn');
        if (!btn) return;

        const wrapper = document.getElementById('day-main-content');
        if (!wrapper) return;

        btn.addEventListener('click', () => {
            const isActive = wrapper.getAttribute('data-focus-mode') === 'true';
            const next = !isActive;
            wrapper.setAttribute('data-focus-mode', String(next));
            btn.setAttribute('aria-pressed', String(next));
            btn.querySelector('.focus-mode-label').textContent = next ? 'Exit focus' : 'Focus mode';

            if (next) {
                const timelineTab = document.getElementById('tab-btn-timeline');
                if (timelineTab) timelineTab.click();
            }
        });
    }

    // ==================== Section Tabs ====================
    function initTabs() {
        const tabNav = document.getElementById('day-section-navigation');
        if (!tabNav) return;

        const tabs = Array.from(tabNav.querySelectorAll('[data-tab-target]'));
        if (!tabs.length) return;
        const panels = tabs.map(t => document.getElementById('tab-panel-' + t.dataset.tabTarget));
        const pill = document.getElementById('day-tab-pill');

        function updatePill(activeTab) {
            if (!pill || !activeTab) return;
            const navRect = tabNav.getBoundingClientRect();
            const tabRect = activeTab.getBoundingClientRect();
            const scrollLeft = tabNav.scrollLeft || 0;
            const left = tabRect.left - navRect.left + scrollLeft;
            pill.style.width = tabRect.width + 'px';
            pill.style.transform = 'translateX(' + left + 'px)';
        }

        function activateTab(tab, pushHash) {
            const currentIndex = tabs.findIndex(t => t.classList.contains('is-active'));
            const newIndex = tabs.indexOf(tab);
            const direction = newIndex > currentIndex ? 'left' : 'right';
            const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
            tabs.forEach((t, i) => {
                const active = t === tab;
                t.setAttribute('role', 'tab');
                t.setAttribute('aria-selected', String(active));
                t.tabIndex = active ? 0 : -1;
                t.classList.toggle('is-active', active);
                const panel = panels[i];
                if (!panel) return;
                panel.setAttribute('role', 'tabpanel');
                panel.hidden = !active;
                panel.inert = !active;
                panel.setAttribute('aria-hidden', String(!active));
                panel.classList.toggle('hidden', !active);
                panel.classList.remove('tab-slide-in-left', 'tab-slide-in-right',
                    'tab-slide-out-left', 'tab-slide-out-right');
                if (active && !reduceMotion && pushHash !== false) {
                    panel.classList.add('tab-slide-in-' + direction);
                }
            });
            updatePill(tab);
            if (pushHash !== false) {
                try {
                    history.replaceState(null, '', '#' + tab.dataset.tabTarget);
                } catch (error) {
                    // Native destinations remain available if history is restricted.
                }
            }
        }

        tabs.forEach(tab => {
            tab.addEventListener('click', (event) => {
                event.preventDefault();
                activateTab(tab);
            });

            // Arrow key navigation
            tab.addEventListener('keydown', (e) => {
                let idx = tabs.indexOf(tab);
                if (e.key === 'ArrowRight') {
                    e.preventDefault();
                    idx = (idx + 1) % tabs.length;
                    tabs[idx].focus();
                    activateTab(tabs[idx]);
                } else if (e.key === 'ArrowLeft') {
                    e.preventDefault();
                    idx = (idx - 1 + tabs.length) % tabs.length;
                    tabs[idx].focus();
                    activateTab(tabs[idx]);
                } else if (e.key === 'Home') {
                    e.preventDefault();
                    tabs[0].focus();
                    activateTab(tabs[0]);
                } else if (e.key === 'End') {
                    e.preventDefault();
                    tabs[tabs.length - 1].focus();
                    activateTab(tabs[tabs.length - 1]);
                } else if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    activateTab(tab);
                }
            });
        });

        // Keyboard shortcut: 1-4 to switch tabs
        document.addEventListener('keydown', (e) => {
            if (e.target.matches('input, textarea, select, [contenteditable]')) return;
            const num = parseInt(e.key);
            if (num >= 1 && num <= tabs.length && !e.ctrlKey && !e.metaKey) {
                activateTab(tabs[num - 1]);
                tabs[num - 1].focus();
            }
        });

        // Shortcuts button
        const shortcutsBtn = document.getElementById('day-shortcuts-btn');
        if (shortcutsBtn) shortcutsBtn.addEventListener('click', showKeyboardHelp);

        // Stat pill navigation: clicking "Tasks left" / "Workouts left" switches to the relevant tab
        document.querySelectorAll('[data-stat-tab-target]').forEach((btn) => {
            const target = btn.getAttribute('data-stat-tab-target');
            const tab = target ? tabs.find(t => t.dataset.tabTarget === target) : null;
            if (!tab) return;
            btn.addEventListener('click', (event) => {
                event.preventDefault();
                activateTab(tab);
                tab.focus();
                tab.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            });
        });

        // Check URL hash for initial tab
        const hash = location.hash.replace('#', '').replace(/^tab-panel-/, '');
        const hashTab = hash ? tabs.find(t => t.dataset.tabTarget === hash) : null;
        activateTab(hashTab || tabs[0], false);
        tabNav.setAttribute('role', 'tablist');
        document.getElementById('day-main-content').dataset.dayTabsEnhanced = 'true';

        // Initialize pill position after paint
        requestAnimationFrame(() => {
            const activeTab = tabs.find(t => t.getAttribute('aria-selected') === 'true') || tabs[0];
            if (activeTab) updatePill(activeTab);
        });

        // Update pill on resize (debounced)
        let resizeTimer;
        window.addEventListener('resize', () => {
            clearTimeout(resizeTimer);
            resizeTimer = setTimeout(() => {
                const activeTab = tabs.find(t => t.getAttribute('aria-selected') === 'true') || tabs[0];
                if (activeTab) updatePill(activeTab);
            }, 100);
        });
    }

    // ==================== Task Filter ====================
    function initTaskFilter() {
        const filterInput = document.getElementById('task-filter-input');
        const filterChips = document.querySelectorAll('.task-filter-chip');
        let activeStatus = 'all';

        function applyFilter() {
            const query = filterInput ? filterInput.value.trim().toLowerCase() : '';
            const tasks = document.querySelectorAll('[data-task-item]');
            tasks.forEach(task => {
                const title = (task.getAttribute('data-task-title') || '').toLowerCase();
                const status = task.getAttribute('data-task-status') || 'todo';
                const matchesQuery = !query || title.includes(query);

                let matchesStatus = true;
                if (activeStatus === 'todo') {
                    matchesStatus = status === 'todo';
                } else if (activeStatus === 'done') {
                    matchesStatus = status === 'done';
                } else if (activeStatus === 'late') {
                    matchesStatus = status === 'late';
                }
                task.style.display = (matchesQuery && matchesStatus) ? '' : 'none';
            });
        }

        if (filterInput) {
            filterInput.addEventListener('input', applyFilter);
        }

        filterChips.forEach(chip => {
            chip.addEventListener('click', () => {
                activeStatus = chip.dataset.filter || 'all';
                filterChips.forEach(c => {
                    c.classList.toggle('is-active', c === chip);
                    c.setAttribute('aria-pressed', c === chip ? 'true' : 'false');
                });
                applyFilter();
            });
        });
        const controls = document.querySelector('[data-task-filter-controls]');
        if (controls) controls.dataset.enhanced = 'true';
    }

    function initTaskRowInteractions() {
        document.addEventListener('click', (e) => {
            const row = e.target && e.target.closest ? e.target.closest('[data-task-item]') : null;
            if (!row) return;

            const interactive = e.target && e.target.closest
                ? e.target.closest('button, a, input, select, textarea, [data-open-task-drawer], [data-quick-complete]')
                : null;
            if (interactive) return;

            const taskId = row.getAttribute('data-task-id');
            if (!taskId) return;

            const opener = row.querySelector('[data-open-task-drawer][data-task-id]');
            if (opener && typeof opener.click === 'function') {
                opener.click();
            }
        });
    }

    function showTimelineSaveStatus(message, tone) {
        const el = document.getElementById('timeline-save-status');
        if (!el) return;

        const toneMap = {
            info: 'text-slate-500 dark:text-slate-400',
            saving: 'text-blue-600 dark:text-blue-300',
            success: 'text-emerald-600 dark:text-emerald-300',
            error: 'text-red-600 dark:text-red-300'
        };

        el.className = 'mt-2 text-xs font-semibold ' + (toneMap[tone] || toneMap.info);
        el.textContent = message;
        el.classList.remove('hidden');

        if (tone !== 'saving') {
            window.clearTimeout(showTimelineSaveStatus._timer);
            showTimelineSaveStatus._timer = window.setTimeout(() => {
                el.classList.add('hidden');
                el.textContent = '';
            }, 1800);
        }
    }

    // ==================== Timeline View ====================
    function buildTimeline() {
        const container = document.getElementById('day-timeline');
        if (!container) return;

        const isToday = container.getAttribute('data-is-today') === 'true';
        const now = new Date();
        const dayMainContent = document.getElementById('day-main-content');
        const pageDate = dayMainContent ? (dayMainContent.getAttribute('data-date') || '') : '';

        // Collect tasks and workouts with timeline slot data.
        const taskItems = Array.from(document.querySelectorAll('[data-task-item]')).map((el) => ({
            id: el.getAttribute('data-task-id') || '',
            title: el.getAttribute('data-task-title') || 'Task',
            time: el.getAttribute('data-task-time') || '',
            completed: el.getAttribute('data-task-status') === 'done',
            type: 'task'
        }));

        const workoutItems = Array.from(document.querySelectorAll('#day-workout-source [data-workout-item]')).map((el) => ({
            id: el.getAttribute('data-workout-id') || '',
            title: el.getAttribute('data-workout-name') || 'Workout',
            time: el.getAttribute('data-workout-time') || '',
            completed: (el.getAttribute('data-workout-completed') || 'false') === 'true',
            type: 'workout',
            kind: el.getAttribute('data-workout-kind') || 'workout',
            url: el.getAttribute('data-workout-url') || ''
        }));

        const occurrenceItems = Array.from(document.querySelectorAll('#day-workout-source [data-occurrence-item]')).map((el) => ({
            id: el.getAttribute('data-occurrence-id') || '',
            title: el.getAttribute('data-occurrence-title') || 'Workout',
            time: el.getAttribute('data-occurrence-time') || '',
            completed: (el.getAttribute('data-completed') || 'false') === 'true',
            type: 'workout',
            kind: 'occurrence',
            url: el.getAttribute('data-occurrence-url') || ''
        }));

        const timelineWorkouts = workoutItems.concat(occurrenceItems);
        const timedTasks = taskItems.filter((t) => !!t.time);
        const untimedTasks = taskItems.filter((t) => !t.time);
        const timedWorkouts = timelineWorkouts.filter((w) => !!w.time);
        const untimedWorkouts = timelineWorkouts.filter((w) => !w.time);

        // Calculate timeline state message
        let stateMessage = '';
        let stateIcon = '';
        let stateClass = '';
        
        if (isToday && timedTasks.length > 0) {
            const currentHour = now.getHours();
            const currentMinute = now.getMinutes();
            const currentTimeInMinutes = currentHour * 60 + currentMinute;
            
            // Find next upcoming incomplete task
            const upcomingTasks = timedTasks.filter(t => !t.completed).map(t => {
                const [h, m] = t.time.split(':').map(Number);
                const taskTimeInMinutes = h * 60 + m;
                return { ...t, taskTimeInMinutes };
            }).filter(t => t.taskTimeInMinutes >= currentTimeInMinutes)
              .sort((a, b) => a.taskTimeInMinutes - b.taskTimeInMinutes);
            
            if (upcomingTasks.length > 0) {
                const nextTask = upcomingTasks[0];
                const minutesUntilNext = nextTask.taskTimeInMinutes - currentTimeInMinutes;
                
                if (minutesUntilNext === 0) {
                    stateMessage = `⏰ "${nextTask.title}" starts now!`;
                    stateIcon = '⏰';
                    stateClass = 'bg-amber-50 border-amber-200 text-amber-900 dark:bg-amber-950/30 dark:border-amber-900/40 dark:text-amber-200';
                } else if (minutesUntilNext <= 10) {
                    stateMessage = `🔔 Your next task "${nextTask.title}" starts in ${minutesUntilNext} minute${minutesUntilNext !== 1 ? 's' : ''}`;
                    stateIcon = '🔔';
                    stateClass = 'bg-blue-50 border-blue-200 text-blue-900 dark:bg-blue-950/30 dark:border-blue-900/40 dark:text-blue-200';
                } else if (minutesUntilNext <= 60) {
                    stateMessage = `📅 Next task in ${minutesUntilNext} minutes`;
                    stateIcon = '📅';
                    stateClass = 'bg-emerald-50 border-emerald-200 text-emerald-900 dark:bg-emerald-950/30 dark:border-emerald-900/40 dark:text-emerald-200';
                } else {
                    const hoursUntil = Math.floor(minutesUntilNext / 60);
                    const minsRemainder = minutesUntilNext % 60;
                    if (minsRemainder > 0) {
                        stateMessage = `📅 Next task in ${hoursUntil}h ${minsRemainder}m`;
                    } else {
                        stateMessage = `📅 Next task in ${hoursUntil} hour${hoursUntil !== 1 ? 's' : ''}`;
                    }
                    stateIcon = '📅';
                    stateClass = 'bg-slate-50 border-slate-200 text-slate-700 dark:bg-slate-900/50 dark:border-slate-700 dark:text-slate-300';
                }
            } else {
                // All upcoming tasks are done, check if any tasks exist at all
                const hasCompletedTasks = taskItems.some(t => t.completed);
                if (hasCompletedTasks) {
                    stateMessage = '✅ All scheduled tasks completed for today!';
                    stateIcon = '✅';
                    stateClass = 'bg-emerald-50 border-emerald-200 text-emerald-900 dark:bg-emerald-950/30 dark:border-emerald-900/40 dark:text-emerald-200';
                } else {
                    stateMessage = '📭 No more timed tasks for today';
                    stateIcon = '📭';
                    stateClass = 'bg-slate-50 border-slate-200 text-slate-600 dark:bg-slate-900/50 dark:border-slate-700 dark:text-slate-400';
                }
            }
        } else if (!isToday && timedTasks.length > 0) {
            stateMessage = `📅 ${timedTasks.length} task${timedTasks.length !== 1 ? 's' : ''} scheduled for this day`;
            stateIcon = '📅';
            stateClass = 'bg-blue-50 border-blue-200 text-blue-900 dark:bg-blue-950/30 dark:border-blue-900/40 dark:text-blue-200';
        } else if (timedTasks.length === 0 && timedWorkouts.length === 0 && untimedTasks.length === 0 && untimedWorkouts.length === 0) {
            stateMessage = '🌟 No timed tasks scheduled for this day';
            stateIcon = '🌟';
            stateClass = 'bg-slate-50 border-slate-200 text-slate-600 dark:bg-slate-900/50 dark:border-slate-700 dark:text-slate-400';
        }

        // Build hour grid 6-23
        let html = '';

        if (untimedTasks.length > 0 || untimedWorkouts.length > 0) {
            html += '<div class="timeline-untimed-lane" role="region" aria-label="Untimed items">';
            html += '<div class="timeline-untimed-head">No fixed time</div>';
            html += '<div class="timeline-untimed-items">';

            untimedTasks.forEach((t) => {
                const doneClass = t.completed ? ' is-done' : '';
                const checkIcon = '<svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M5 13l4 4L19 7" stroke-linecap="round" stroke-linejoin="round"/></svg>';
                const qcBtn = t.id ? `<button type="button" class="timeline-qc-btn${t.completed ? ' is-done' : ''}" data-quick-complete data-task-id="${escapeHtml(t.id)}" data-task-date="${escapeHtml(pageDate)}" aria-label="${t.completed ? 'Mark incomplete' : 'Mark complete'}" title="${t.completed ? 'Mark incomplete' : 'Mark complete'}">${checkIcon}</button>` : '';
                html += `<div class="timeline-event timeline-event--task timeline-event--untimed${doneClass}" data-open-task-drawer data-task-id="${escapeHtml(t.id)}" draggable="true" data-draggable-task data-task-id="${escapeHtml(t.id)}" data-task-time="" role="listitem" data-item-type="task" title="Drag to schedule">`
                    + `<span class="font-medium">${escapeHtml(t.title)}</span><span class="ml-auto text-[10px] opacity-70 mr-1">Untimed</span>${qcBtn}</div>`;
            });

            untimedWorkouts.forEach((w) => {
                const doneClass = w.completed ? ' is-done' : '';
                html += `<a href="${escapeHtml(w.url)}" class="timeline-event timeline-event--workout timeline-event--untimed${doneClass}" data-open-workout-drawer data-workout-kind="${w.kind === 'occurrence' ? 'occurrence' : 'session'}" data-workout-id="${escapeHtml(w.id)}" draggable="true" data-draggable-item data-item-type="${escapeHtml(w.kind || 'workout')}" data-item-id="${escapeHtml(w.id)}" data-item-time="" title="Drag to schedule">`
                    + `<svg class="h-3.5 w-3.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"/></svg>`
                    + `<span class="font-medium">${escapeHtml(w.title)}</span><span class="ml-auto text-[10px] opacity-70">Untimed</span></a>`;
            });

            html += '</div></div>';
        }

        html += '<div class="timeline-grid" role="list" aria-label="Day timeline">';

        const currentHour = now.getHours();
        const currentMinute = now.getMinutes();

        for (let h = 6; h <= 23; h++) {
            const isNowHour = isToday && h === currentHour;
            html += `<div class="timeline-hour-row${isNowHour ? ' timeline-now-indicator' : ''}" role="listitem">`;
            if (isNowHour) {
                html += `<div class="timeline-now-dot" aria-hidden="true"></div>`;
            }
            html += `<div class="timeline-hour-label" aria-hidden="true">${String(h).padStart(2, '0')}:00</div>`;
            html += `<div class="timeline-slot">`;

            // Tasks in this hour
            timedTasks.filter(t => {
                const [th] = t.time.split(':').map(Number);
                return th === h;
            }).forEach(t => {
                const doneClass = t.completed ? ' is-done' : '';
                const checkIcon = '<svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M5 13l4 4L19 7" stroke-linecap="round" stroke-linejoin="round"/></svg>';
                const qcBtn = t.id ? `<button type="button" class="timeline-qc-btn${t.completed ? ' is-done' : ''}" data-quick-complete data-task-id="${escapeHtml(t.id)}" data-task-date="${escapeHtml(pageDate)}" aria-label="${t.completed ? 'Mark incomplete' : 'Mark complete'}" title="${t.completed ? 'Mark incomplete' : 'Mark complete'}">${checkIcon}</button>` : '';
                // Make task clickable to open drawer and draggable
                const clickableAttr = t.id ? ` data-open-task-drawer data-task-id="${escapeHtml(t.id)}" style="cursor: grab;" title="Click to view details, drag to reschedule"` : '';
                const draggableAttr = t.id ? ` draggable="true" data-draggable-task data-task-id="${escapeHtml(t.id)}" data-task-time="${escapeHtml(t.time)}"` : '';
                html += `<div class="timeline-event timeline-event--task${doneClass}"${clickableAttr}${draggableAttr} role="listitem" data-item-type="task">
                    <span class="font-medium">${escapeHtml(t.title)}</span>
                    <span class="ml-auto text-[10px] opacity-70 mr-1">${t.time}</span>
                    ${qcBtn}
                </div>`;
            });

            // Workouts in assigned slot
            timedWorkouts.filter((w) => {
                const [wh] = (w.time || '').split(':').map(Number);
                return wh === h;
            }).forEach((w) => {
                const doneClass = w.completed ? ' is-done' : '';
                const clickable = ` href="${escapeHtml(w.url)}" data-open-workout-drawer data-workout-kind="${w.kind === 'occurrence' ? 'occurrence' : 'session'}" data-workout-id="${escapeHtml(w.id)}" title="Open workout details"`;
                const draggable = ` draggable="true" data-draggable-item data-item-type="${escapeHtml(w.kind || 'workout')}" data-item-id="${escapeHtml(w.id)}" data-item-time="${escapeHtml(w.time || '')}"`;
                html += `<a class="timeline-event timeline-event--workout${doneClass}"${clickable}${draggable}>
                    <svg class="h-3.5 w-3.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"/></svg>
                    <span class="font-medium">${escapeHtml(w.title)}</span>
                    <span class="ml-auto text-[10px] opacity-70">${escapeHtml((w.time || '').slice(0, 5))}</span>
                </a>`;
            });

            html += `</div></div>`;
        }
        html += '</div>';

        // Add state message banner and legend
        let bannerHtml = '';
        if (stateMessage) {
            bannerHtml = `<div class="mb-3 flex items-start gap-2 rounded-lg border p-3 ${stateClass}">
                <span class="text-xl flex-shrink-0" aria-hidden="true">${stateIcon}</span>
                <p class="text-sm font-medium leading-relaxed">${stateMessage}</p>
            </div>`;
        }
        
        html = bannerHtml + `<div class="mb-3 flex flex-wrap items-center gap-3 text-xs text-slate-500 dark:text-slate-400">
            <span class="flex items-center gap-1.5"><span class="h-3 w-3 rounded-sm border-l-2 border-l-blue-500 bg-blue-50"></span>Task</span>
            <span class="flex items-center gap-1.5"><span class="h-3 w-3 rounded-sm border-l-2 border-l-emerald-500 bg-emerald-50"></span>Workout</span>
            ${isToday ? '<span class="flex items-center gap-1.5"><span class="inline-block h-2 w-4 bg-red-500 opacity-70 rounded-full"></span>Now</span>' : ''}
        </div>` + html;

        container.innerHTML = html;

        // Auto-scroll to current hour if today
        if (isToday) {
            const nowRow = container.querySelector('.timeline-now-indicator');
            if (nowRow) {
                const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
                setTimeout(() => nowRow.scrollIntoView({
                    behavior: reducedMotion ? 'auto' : 'smooth',
                    block: 'center'
                }), 300);
            }
        }

        if (container.dataset.timelineBound !== 'true') {
            container.dataset.timelineBound = 'true';

        // Click-to-add: clicking an empty slot prefills the add-task modal with the hour
        container.addEventListener('click', (e) => {
            // Only trigger if clicking on an empty slot (not on an event or button)
            const slot = e.target.closest('.timeline-slot');
            const event = e.target.closest('.timeline-event');
            const qcBtn = e.target.closest('.timeline-qc-btn');
            if (!slot || event || qcBtn) return;

            // Get the hour from the parent row
            const row = e.target.closest('.timeline-hour-row');
            if (!row) return;
            const label = row.querySelector('.timeline-hour-label');
            if (!label) return;
            const hour = label.textContent.split(':')[0];
            const timeValue = String(hour).padStart(2, '0') + ':00';

            // Open add task modal and prefill time
            const addBtn = document.getElementById('open-add-task');
            if (addBtn) addBtn.click();

            // Wait for modal to open, then prefill time
            setTimeout(() => {
                const timeInput = document.querySelector('#add-task-modal input[name="time"]');
                if (timeInput) timeInput.value = timeValue;
            }, 50);
        });

        // ===== Drag and Drop Timeline Functionality =====
        let draggedElement = null;
        let draggedItemType = null;
        let draggedItemId = null;
        let draggedItemTime = null;

        // Dragstart - when user starts dragging a task
        container.addEventListener('dragstart', (e) => {
            const draggable = e.target.closest('[data-draggable-task], [data-draggable-item]');
            if (!draggable) return;

            draggedElement = draggable;
            draggedItemType = draggable.getAttribute('data-item-type') || 'task';
            draggedItemId = draggable.getAttribute('data-task-id') || draggable.getAttribute('data-item-id');
            draggedItemTime = draggable.getAttribute('data-task-time') || draggable.getAttribute('data-item-time');

            draggable.classList.add('is-dragging');
            e.dataTransfer.effectAllowed = 'move';
            e.dataTransfer.setData('text/html', draggable.innerHTML);
        });

        // Dragend - cleanup after drag completes or is cancelled
        container.addEventListener('dragend', (e) => {
            if (draggedElement) {
                draggedElement.classList.remove('is-dragging');
            }
            // Remove all drop zone indicators
            container.querySelectorAll('.timeline-slot').forEach(slot => {
                slot.classList.remove('timeline-drop-over');
            });
            draggedElement = null;
            draggedItemType = null;
            draggedItemId = null;
            draggedItemTime = null;
        });

        // Dragover - allow drop on timeline slots
        container.addEventListener('dragover', (e) => {
            const slot = e.target.closest('.timeline-slot');
            if (!slot) return;

            e.preventDefault(); // Allow drop
            e.dataTransfer.dropEffect = 'move';
            
            // Add visual indicator
            slot.classList.add('timeline-drop-over');
        });

        // Dragleave - remove visual indicator when leaving slot
        container.addEventListener('dragleave', (e) => {
            const slot = e.target.closest('.timeline-slot');
            if (!slot) return;
            
            // Only remove if we're actually leaving the slot (not entering a child)
            if (!slot.contains(e.relatedTarget)) {
                slot.classList.remove('timeline-drop-over');
            }
        });

        // Drop - handle task drop on new time slot
        container.addEventListener('drop', (e) => {
            const slot = e.target.closest('.timeline-slot');
            if (!slot || !draggedItemId) return;

            e.preventDefault();
            slot.classList.remove('timeline-drop-over');

            // Get the hour from the parent row
            const row = slot.closest('.timeline-hour-row');
            if (!row) return;
            const label = row.querySelector('.timeline-hour-label');
            if (!label) return;
            const newHour = parseInt(label.textContent.split(':')[0], 10);
            const slotRect = slot.getBoundingClientRect();
            const yRatio = slotRect.height > 0 ? (e.clientY - slotRect.top) / slotRect.height : 0;
            const quarter = Math.max(0, Math.min(3, Math.floor(yRatio * 4)));
            const minutes = String(quarter * 15).padStart(2, '0');
            const newTime = String(newHour).padStart(2, '0') + ':' + minutes;

            // Don't do anything if dropped on same time
            if (draggedItemTime && draggedItemTime === newTime) {
                return;
            }

            // Update timeline slot via AJAX with optimistic sync.
            updateTimelineSlot(draggedItemType, draggedItemId, pageDate, newTime, draggedElement);
        });
        }
    }

    // Function to update timeline slot via AJAX without reload.
    function updateTimelineSlot(itemType, itemId, date, newTime, draggedEl) {
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');

        showTimelineSaveStatus('Saving timeline change...', 'saving');

        // Optimistic local model update.
        if (itemType === 'task') {
            const row = document.querySelector('[data-task-item][data-task-id="' + itemId + '"]');
            if (row) {
                row.setAttribute('data-task-time', newTime);
                const timeEl = row.querySelector('[data-testid="task-time"]');
                if (timeEl) timeEl.textContent = newTime;
            }
        } else {
            const selector = itemType === 'occurrence'
                ? '#day-workout-source [data-occurrence-item][data-occurrence-id="' + itemId + '"]'
                : '#day-workout-source [data-workout-item][data-workout-id="' + itemId + '"]';
            const source = document.querySelector(selector);
            if (source) {
                const attr = itemType === 'occurrence' ? 'data-occurrence-time' : 'data-workout-time';
                source.setAttribute(attr, newTime);
            }

            const kind = itemType === 'occurrence' ? 'occurrence' : 'session';
            const card = document.querySelector('[data-workout-card][data-workout-kind="' + kind + '"][data-workout-id="' + itemId + '"]');
            if (card) {
                card.dataset.workoutTime = newTime;
                const sub = card.querySelector('p.text-xs');
                if (sub) sub.textContent = newTime;
            }
        }

        buildTimeline();

        fetch('/calendar/day/' + date + '/timeline-slot', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                ...(csrfHeader && csrfToken ? { [csrfHeader]: csrfToken } : {})
            },
            body: new URLSearchParams({
                itemType: itemType,
                itemId: itemId,
                time: newTime
            })
        })
        .then(response => {
            if (response.ok) {
                showTimelineSaveStatus('Saved', 'success');
                return;
            } else {
                throw new Error('Failed to reschedule item');
            }
        })
        .catch(error => {
            console.error('Error updating timeline slot:', error);
            const flash = document.createElement('div');
            flash.className = 'mb-3 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-800 dark:border-red-900/50 dark:bg-red-950/30 dark:text-red-200';
            flash.textContent = 'Could not save timeline change. The item was returned to its previous slot.';
            const host = document.getElementById('day-timeline');
            if (host) {
                host.prepend(flash);
                window.setTimeout(() => flash.remove(), 2600);
            }
            showTimelineSaveStatus('Could not save, reverted', 'error');

            // Revert to previous slot time when save fails.
            const prevTime = draggedEl?.getAttribute('data-task-time') || draggedEl?.getAttribute('data-item-time') || '';
            if (itemType === 'task') {
                const row = document.querySelector('[data-task-item][data-task-id="' + itemId + '"]');
                if (row) {
                    row.setAttribute('data-task-time', prevTime);
                    const timeEl = row.querySelector('[data-testid="task-time"]');
                    if (timeEl) timeEl.textContent = prevTime;
                }
            } else {
                const selector = itemType === 'occurrence'
                    ? '#day-workout-source [data-occurrence-item][data-occurrence-id="' + itemId + '"]'
                    : '#day-workout-source [data-workout-item][data-workout-id="' + itemId + '"]';
                const source = document.querySelector(selector);
                if (source) {
                    const attr = itemType === 'occurrence' ? 'data-occurrence-time' : 'data-workout-time';
                    source.setAttribute(attr, prevTime);
                }
                const kind = itemType === 'occurrence' ? 'occurrence' : 'session';
                const card = document.querySelector('[data-workout-card][data-workout-kind="' + kind + '"][data-workout-id="' + itemId + '"]');
                if (card) {
                    card.dataset.workoutTime = prevTime;
                    const sub = card.querySelector('p.text-xs');
                    if (sub) sub.textContent = prevTime || document.getElementById('day-main-content')?.dataset.noFixedTime || '';
                }
            }
            buildTimeline();
        });
    }

    function escapeHtml(str) {
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    // ==================== Timed Focus Countdown ====================
    function initTimedFocusCountdown() {
        const section = document.querySelector('[data-testid="timed-focus"]');
        if (!section) return;

        // Find nearest upcoming task with a time (from task items)
        const now = new Date();
        const currentMinutes = now.getHours() * 60 + now.getMinutes();

        let nearest = null;
        let nearestMinutes = Infinity;

        document.querySelectorAll('[data-task-item]').forEach(el => {
            const timeStr = el.getAttribute('data-task-time');
            const status = el.getAttribute('data-task-status');
            if (!timeStr || status === 'done') return;
            const [h, m] = timeStr.split(':').map(Number);
            const taskMinutes = h * 60 + m;
            const diff = taskMinutes - currentMinutes;
            if (diff >= 0 && diff < nearestMinutes) {
                nearestMinutes = diff;
                nearest = { timeStr, minutes: taskMinutes, diff };
            }
        });

        if (!nearest) return;

        function getNowMinutes() {
            const t = new Date();
            return t.getHours() * 60 + t.getMinutes();
        }

        // Check if currently active (within 30-min window)
        const isActive = currentMinutes >= nearest.minutes - 5 && currentMinutes <= nearest.minutes + 30;
        if (isActive) {
            section.setAttribute('data-timed-focus-active', 'true');
        }

        // Show countdown only if within 60 minutes
        if (nearest.diff > 60) return;

        // Create countdown element
        let countdownEl = section.querySelector('.timed-focus-countdown');
        if (!countdownEl) {
            countdownEl = document.createElement('span');
            countdownEl.className = 'timed-focus-countdown block mt-1';
            const valueEl = section.querySelector('[data-testid="timed-focus-value"]');
            if (valueEl) valueEl.after(countdownEl);
        }

        function updateCountdown() {
            const now = new Date();
            const diffSec = nearest.minutes * 60 - (now.getHours() * 3600 + now.getMinutes() * 60 + now.getSeconds());
            if (diffSec <= 0) {
                countdownEl.textContent = 'Now';
                return;
            }
            const m = Math.floor(diffSec / 60);
            const s = diffSec % 60;
            countdownEl.textContent = 'In ' + m + 'm ' + String(s).padStart(2, '0') + 's';
        }

        updateCountdown();
        const timer = setInterval(() => {
            updateCountdown();
            if (getNowMinutes() > nearest.minutes + 30) {
                clearInterval(timer);
                if (countdownEl) countdownEl.remove();
                section.removeAttribute('data-timed-focus-active');
            }
        }, 1000);
    }

    // ==================== AI Optimise Modal ====================
    function initAiOptimiseModal() {
        const btn = document.getElementById('ai-optimise-btn');
        const modal = document.getElementById('ai-optimise-modal');
        const cancelBtn = document.getElementById('ai-optimise-cancel');
        const confirmBtn = document.getElementById('ai-optimise-confirm');
        const badge = document.getElementById('ai-optimised-badge');
        const dontShowCheck = document.getElementById('ai-optimise-dont-show');

        if (!btn || !modal) return;

        const dateAttr = btn.getAttribute('data-date');
        // Server already sets disabled/badge for previously optimised dates.
        // JS only needs to handle the "skip warning" preference from localStorage.
        const storageKey = 'ai-optimise-skip-warning';

        // If already optimised from server, nothing more to do for init
        if (btn.getAttribute('data-optimised') === 'true') {
            return;
        }

        // Pre-populate "don't show" checkbox from server preference
        const serverHideWarning = btn.getAttribute('data-hide-warning') === 'true';
        if (serverHideWarning && dontShowCheck) {
            dontShowCheck.checked = true;
            localStorage.setItem(storageKey, '1');
        }

        function openModal() {
            modal.classList.remove('hidden');
            modal.setAttribute('aria-hidden', 'false');
            if (cancelBtn) cancelBtn.focus();
            document.addEventListener('keydown', handleEscape);
        }

        function closeModal() {
            modal.classList.add('hidden');
            modal.setAttribute('aria-hidden', 'true');
            document.removeEventListener('keydown', handleEscape);
            btn.focus();
        }

        function handleEscape(e) {
            if (e.key === 'Escape') closeModal();
        }

        function applyOptimisedState(dayTheme) {
            btn.disabled = true;
            btn.setAttribute('aria-disabled', 'true');
            btn.classList.add('opacity-50', 'cursor-not-allowed');
            if (badge) badge.classList.remove('hidden');
            // Lock the daily focus select
            const focusSelect = document.getElementById('daily-focus-select');
            if (focusSelect) {
                focusSelect.disabled = true;
                focusSelect.setAttribute('title', 'Locked by AI Optimise');
            }
            // Apply day theme CSS class to main container
            if (dayTheme) {
                applyDayThemeClass(dayTheme);
            }
        }

        btn.addEventListener('click', () => {
            if (localStorage.getItem(storageKey) === '1') {
                performOptimise(false);
            } else {
                openModal();
            }
        });

        if (cancelBtn) cancelBtn.addEventListener('click', closeModal);

        if (confirmBtn) {
            confirmBtn.addEventListener('click', () => {
                const dontShow = dontShowCheck && dontShowCheck.checked;
                if (dontShow) {
                    localStorage.setItem(storageKey, '1');
                }
                closeModal();
                performOptimise(dontShow);
            });
        }

        // Close on backdrop click
        modal.addEventListener('click', (e) => {
            if (e.target === modal) closeModal();
        });

        function performOptimise(dontShowAgain) {
            const csrf = document.querySelector('input[name="_csrf"]');
            const csrfVal = csrf ? csrf.value : null;

            const body = new URLSearchParams();
            body.set('dontShowAgain', dontShowAgain ? 'true' : 'false');
            if (csrfVal) body.set('_csrf', csrfVal);

            fetch('/calendar/day/' + dateAttr + '/optimise', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'X-Requested-With': 'XMLHttpRequest'
                },
                body: body.toString()
            })
            .then(res => {
                if (res.ok) return res.json();
                if (res.status === 409) {
                    // Already optimised (server-side check)
                    applyOptimisedState(null);
                }
                return null;
            })
            .then(data => {
                if (data && data.dayTheme) {
                    applyOptimisedState(data.dayTheme);
                }
            })
            .catch(err => {
                console.error('AI Optimise request failed:', err);
            });
        }
    }

    // ==================== Quick Complete Toggle ====================
    function initQuickComplete() {
        // Get CSRF token from any existing form in the page
        function getCsrf() {
            const inp = document.querySelector('input[name="_csrf"]');
            return inp ? inp.value : null;
        }

        function getCsrfParam() {
            const inp = document.querySelector('input[name="_csrf"]');
            const name = inp ? inp.getAttribute('name') : '_csrf';
            return name;
        }

        function chipLabel(isDone, status) {
            if (isDone) return 'Done';
            return status === 'late' ? 'Late' : 'To do';
        }

        document.addEventListener('click', (e) => {
            const btn = e.target && e.target.closest ? e.target.closest('[data-quick-complete]') : null;
            if (!btn) return;
            const taskId = btn.getAttribute('data-task-id');
            const date = btn.getAttribute('data-task-date');
            const csrf = getCsrf();
            if (!taskId || !date || !csrf) return;
            e.preventDefault();
            e.stopPropagation();

            const taskItem = document.querySelector('[data-task-item][data-task-id="' + taskId + '"]');
            if (!taskItem || taskItem.dataset.completionPending === 'true') return;
            const isDone = taskItem.dataset.taskCompleted === 'true';
            const originalStatus = taskItem.dataset.taskStatus || 'todo';
            const pendingStatus = originalStatus === 'late' ? 'late'
                : taskItem.dataset.taskPendingStatus || 'todo';
            taskItem.dataset.taskPendingStatus = pendingStatus;
            taskItem.dataset.completionPending = 'true';
            const newDone = !isDone;
            const feedback = document.getElementById('day-completion-feedback');
            if (feedback) feedback.classList.add('hidden');
            const matchingButtons = () => Array.from(document.querySelectorAll(
                '[data-quick-complete][data-task-id="' + taskId + '"]'));
            matchingButtons().forEach(button => { button.disabled = true; });

            function applyCompletionState(done, status) {
                const pending = status || pendingStatus;
                taskItem.dataset.taskStatus = done ? 'done' : pending;
                taskItem.dataset.taskCompleted = String(done);
                taskItem.classList.toggle('bg-green-50', done);
                taskItem.classList.toggle('dark:bg-green-950/20', done);
                const chip = taskItem.querySelector('[data-testid="task-status-chip"]');
                if (chip) chip.textContent = chipLabel(done, pending);
                matchingButtons().forEach(button => {
                    button.classList.toggle('is-done', done);
                    button.setAttribute('aria-pressed', String(done));
                    button.setAttribute('aria-label', 'Mark task ' + taskItem.dataset.taskTitle
                        + (done ? ' incomplete' : ' complete'));
                    button.title = done ? 'Mark incomplete' : 'Mark complete';
                });
                const hiddenTaskModel = document.getElementById('task-drawer-content-' + taskId);
                if (hiddenTaskModel) hiddenTaskModel.dataset.taskCompleted = String(done);
                refreshMainCompletionProgress();
                updateTimedFocusCard();
            }

            applyCompletionState(newDone);
            if (newDone) {
                taskItem.classList.remove('just-completed');
                void taskItem.offsetWidth;
                taskItem.classList.add('just-completed');
                taskItem.addEventListener('animationend', () => taskItem.classList.remove('just-completed'), { once: true });
            }

            const body = new URLSearchParams();
            body.append('taskId', taskId);
            body.append(getCsrfParam(), csrf);

            fetch('/calendar/day/' + date + '/toggle-complete', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                    'X-Requested-With': 'XMLHttpRequest'
                },
                body: body.toString()
            })
            .then((res) => {
                if (!res.ok) {
                    throw new Error('Toggle failed');
                }
                return res.json();
            })
            .then((payload) => {
                if (!payload || payload.success !== true) {
                    throw new Error('Toggle failed');
                }

                applyCompletionState(payload.completed === true);
                const openTaskId = document.getElementById('task-drawer-body')?.getAttribute('data-open-task-id');
                if (openTaskId && openTaskId === String(taskId)) {
                    const drawerBody = document.getElementById('task-drawer-body');
                    const completeBtn = drawerBody?.querySelector('[data-task-drawer-complete-btn]');
                    const completeChip = drawerBody?.querySelector('[data-task-drawer-completed-chip]');
                    const completionMessage = drawerBody?.querySelector('[data-task-drawer-completion-message]');
                    if (completeBtn && completeChip) {
                        if (payload.completed) {
                            completeBtn.classList.add('hidden');
                            completeChip.classList.remove('hidden');
                            if (completionMessage) completionMessage.textContent = 'This task is completed. Nice work.';
                        } else {
                            completeBtn.classList.remove('hidden');
                            completeChip.classList.add('hidden');
                            if (completionMessage) completionMessage.textContent = 'Mark complete to advance day progress and unlock insights faster.';
                        }
                    }
                }
            })
            .catch(() => {
                applyCompletionState(isDone, originalStatus);
                if (feedback) {
                    feedback.textContent = feedback.dataset.errorCopy;
                    feedback.classList.remove('hidden');
                }
            })
            .finally(() => {
                delete taskItem.dataset.completionPending;
                matchingButtons().forEach(button => { button.disabled = false; });
            });
        });
    }

    // ==================== Day Theme Helper ====================
    function applyDayThemeClass(theme) {
        const container = document.getElementById('day-main-content');
        if (container) {
            container.setAttribute('data-day-theme', theme);
        }
        document.body.classList.remove('day-theme-professional', 'day-theme-futuristic', 'day-theme-clean');
        document.body.classList.add('day-theme-' + theme);
    }

    // ==================== Apply Server-Side Day Theme ====================
    function applyServerDayTheme() {
        const container = document.getElementById('day-main-content');
        if (!container) return;
        const theme = container.getAttribute('data-day-theme');
        if (theme) {
            applyDayThemeClass(theme);
        }
    }

    function renderDayInsights() {
        const host = document.getElementById('day-insights-output');
        const root = document.querySelector('[data-main-progress]');
        if (!host || !root) return;

        const tasksLeft = parseInt(root.getAttribute('data-progress-tasks-left') || '0', 10);
        const workoutsLeft = parseInt(root.getAttribute('data-progress-workouts-left') || '0', 10);
        const remaining = parseInt(root.getAttribute('data-progress-remaining') || '0', 10);
        const pct = parseInt(root.getAttribute('data-progress-pct') || '0', 10);
        const nowHour = new Date().getHours();

        let type = 'priority';
        let title = 'Priority guidance';
        let body = 'Start with one high-friction item in the next 30 minutes to unlock momentum for this day.';

        if (remaining === 0 && pct > 0) {
            type = 'positive';
            title = 'Positive momentum';
            body = 'Today is complete. Capture one reflection now to reinforce what worked today.';
        } else if (remaining > 0 && nowHour >= 18 && tasksLeft > 0) {
            type = 'caution';
            title = 'Caution';
            body = 'Late-day task load is building. Clear one short task now to prevent today from rolling over.';
        } else if (workoutsLeft > 0 && nowHour >= 12 && nowHour <= 20) {
            type = 'reminder';
            title = 'Reminder';
            body = 'You still have a workout pending today. Reserve a concrete time block before evening closes.';
        }

        const classes = {
            positive: 'border-emerald-200 bg-emerald-50 text-emerald-900 dark:border-emerald-800/40 dark:bg-emerald-950/30 dark:text-emerald-200',
            caution: 'border-amber-200 bg-amber-50 text-amber-900 dark:border-amber-800/40 dark:bg-amber-950/30 dark:text-amber-200',
            reminder: 'border-blue-200 bg-blue-50 text-blue-900 dark:border-blue-800/40 dark:bg-blue-950/30 dark:text-blue-200',
            priority: 'border-slate-200 bg-slate-50 text-slate-800 dark:border-slate-700 dark:bg-slate-900/60 dark:text-slate-200'
        };

        host.innerHTML = '<article class="rounded-xl border p-3 ' + classes[type] + '">' +
            '<p class="text-xs font-semibold uppercase tracking-wide">' + title + '</p>' +
            '<p class="mt-1 text-sm font-medium">' + body + '</p>' +
            '<p class="mt-1 text-xs opacity-80">Remaining: ' + tasksLeft + ' tasks · ' + workoutsLeft + ' workouts</p>' +
            '</article>';
    }

    // ==================== Initialize Everything ====================
    function init() {
        // Wait for DOM to be ready
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', init);
            return;
        }

        // Apply page enter animation
        const mainContent = document.getElementById('day-main-content');
        if (mainContent) {
            mainContent.classList.add('day-page-enter');
        }
        
        initMainProgressFill();
        applyTimeOfDayTheme();
        initTimeThemeToggle();
        animateCompletionDots();
        initQuickStats();
        updateTimeGreeting();
        addMotivationalMessage();
        highlightUpcomingTasks();
        scrollToCurrentTime();
        initKeyboardShortcuts();
        initFocusMode();
        initTabs();
        initTaskFilter();
        initTaskRowInteractions();
        buildTimeline();
        initTimedFocusCountdown();
        initLiveClock();
        initAiOptimiseModal();
        initQuickComplete();
        applyServerDayTheme();
        updateTimedFocusCard();
        refreshMainCompletionProgress();
        initSseUpdates();
        renderDayInsights();
        
        // Refresh upcoming task highlights every minute
        setInterval(highlightUpcomingTasks, 60000);
    }

    // ==================== Live Clock in Timed Focus ====================
    function initLiveClock() {
        const section = document.querySelector('[data-testid="timed-focus"]');
        if (!section) return;

        // Add a live clock display
        let clockEl = section.querySelector('.timed-focus-clock');
        if (!clockEl) {
            clockEl = document.createElement('span');
            clockEl.className = 'timed-focus-clock block text-xs font-mono text-slate-500 dark:text-slate-400 mt-1';
            const valueEl = section.querySelector('[data-testid="timed-focus-value"]');
            if (valueEl) valueEl.after(clockEl);
        }

        // Show next upcoming event
        function getNextEvent() {
            const now = new Date();
            const currentMinutes = now.getHours() * 60 + now.getMinutes();
            let nearest = null;
            let nearestMinutes = Infinity;
            let nearestTitle = '';

            document.querySelectorAll('[data-task-item]').forEach(el => {
                const timeStr = el.getAttribute('data-task-time');
                const status = el.getAttribute('data-task-status');
                const title = el.getAttribute('data-task-title') || '';
                if (!timeStr || status === 'done') return;
                const [h, m] = timeStr.split(':').map(Number);
                const taskMinutes = h * 60 + m;
                const diff = taskMinutes - currentMinutes;
                if (diff >= 0 && diff < nearestMinutes) {
                    nearestMinutes = diff;
                    nearest = diff;
                    nearestTitle = title;
                }
            });

            return nearest !== null ? { diff: nearest, title: nearestTitle } : null;
        }

        function updateClock() {
            const now = new Date();
            const h = String(now.getHours()).padStart(2, '0');
            const m = String(now.getMinutes()).padStart(2, '0');
            const s = String(now.getSeconds()).padStart(2, '0');
            let text = h + ':' + m + ':' + s;

            const nextEvent = getNextEvent();
            if (nextEvent && nextEvent.diff <= 60) {
                const minsLeft = nextEvent.diff;
                text += ' · Next: ' + nextEvent.title.substring(0, 20) + (nextEvent.title.length > 20 ? '…' : '') + ' in ' + minsLeft + 'm';
            }
            clockEl.textContent = text;
        }

        updateClock();
        setInterval(updateClock, 1000);
    }

    // ==================== Real-Time SSE Updates ====================
    function initSseUpdates() {
        let eventSource = null;
        const MAX_SSE_RETRIES = 5;
        let sseRetryCount = 0;

        function connectSse() {
            if (eventSource) {
                if (eventSource.readyState === 0 || eventSource.readyState === 1) return;
                eventSource.close();
            }

            eventSource = new EventSource("/api/notifications/stream");

            eventSource.addEventListener("open", () => {
                sseRetryCount = 0;
            });

            eventSource.addEventListener("day-completion-update", (event) => {
                try {
                    const data = JSON.parse(event.data);
                    updateDayCompletion(data);
                } catch (e) {
                    console.warn("Failed to parse day-completion-update event", e);
                }
            });

            eventSource.onerror = () => {
                eventSource.close();
                eventSource = null;
                if (sseRetryCount < MAX_SSE_RETRIES) {
                    const delay = Math.pow(2, sseRetryCount) * 2000;
                    sseRetryCount++;
                    setTimeout(connectSse, delay);
                }
            };
        }
        
        function updateDayCompletion(data) {
            // Update percentage display
            const percentageElement = document.getElementById('day-completion-percentage');
            if (percentageElement) {
                percentageElement.textContent = `${data.percentage}%`;
            }
            
            // Update completion dots
            const dotsContainer = document.querySelector('.completion-dots-container');
            if (dotsContainer && data.completedCount !== undefined && data.totalCount !== undefined) {
                const completedCount = data.completedCount || 0;
                const totalCount = data.totalCount || 0;
                
                dotsContainer.innerHTML = '';
                for (let i = 0; i < totalCount; i++) {
                    const dot = document.createElement('div');
                    dot.className = i < completedCount ? 'completion-dot completed' : 'completion-dot';
                    dotsContainer.appendChild(dot);
                }
            }
            
            // Update status badge
            const statusBadge = document.querySelector('.day-status-badge');
            if (statusBadge && data.status) {
                const statusClasses = {
                    'COMPLETE': 'status-complete',
                    'AHEAD': 'status-ahead',
                    'ON_TRACK': 'status-on-track',
                    'BEHIND': 'status-behind',
                    'NOT_STARTED': 'status-not-started'
                };
                
                // Remove all status classes
                Object.values(statusClasses).forEach(cls => statusBadge.classList.remove(cls));
                
                // Add new status class
                const statusClass = statusClasses[data.status] || statusClasses.NOT_STARTED;
                statusBadge.classList.add(statusClass);
                
                // Update badge text
                const statusText = {
                    'COMPLETE': 'Complete',
                    'AHEAD': 'Ahead',
                    'ON_TRACK': 'On Track',
                    'BEHIND': 'Behind',
                    'NOT_STARTED': 'Not Started'
                };
                statusBadge.textContent = statusText[data.status] || 'Not Started';
            }
            
            // Update progress bar
            const progressBar = document.querySelector('.day-progress-bar-fill');
            if (progressBar) {
                progressBar.style.width = `${data.percentage}%`;
            }

            if (typeof data.percentage === 'number') {
                const fill = document.getElementById('day-main-progress-fill');
                if (fill) {
                    fill.style.width = data.percentage + '%';
                }
            }

            refreshMainCompletionProgress();
            updateTimedFocusCard();
        }
        
        // Initialize connection
        connectSse();
    }
    
    function stripLeadingGarble(text) {
        return String(text || '').replace(/^[^A-Za-z0-9"']+\s*/u, '');
    }

    function normalizeCalendarCopy(text) {
        return String(text || '')
            .replace(/\u00C2\u00B7/g, '|')
            .replace(/\u00E2\u20AC\u00A6/g, '...')
            .replace(/\u00E2\u20AC\u201D/g, '--')
            .replace(/\s+\|\s+/g, ' | ')
            .trim();
    }

    function sanitizeTimelineBanner() {
        const timeline = document.getElementById('day-timeline');
        if (!timeline) return;

        const banner = timeline.querySelector('.mb-3.flex.items-start.gap-2');
        if (!banner) return;

        const icon = banner.querySelector('span[aria-hidden="true"]');
        const copy = banner.querySelector('p');
        if (!copy) return;

        const normalizedText = normalizeCalendarCopy(stripLeadingGarble(copy.textContent));
        copy.textContent = normalizedText;

        if (!icon) return;
        if (normalizedText.includes('starts now')) {
            icon.textContent = '\u23F0';
        } else if (normalizedText.includes('starts in')) {
            icon.textContent = '\uD83D\uDD14';
        } else if (normalizedText.includes('Next task in') || normalizedText.includes('scheduled for this day')) {
            icon.textContent = '\uD83D\uDCC5';
        } else if (normalizedText.includes('All scheduled tasks completed')) {
            icon.textContent = '\u2705';
        } else if (normalizedText.includes('No more timed tasks')) {
            icon.textContent = '\uD83D\uDDED';
        } else if (normalizedText.includes('No timed tasks scheduled')) {
            icon.textContent = '\uD83C\uDF1F';
        }
    }

    function sanitizeLiveClockCopy() {
        const clockEl = document.getElementById('live-clock');
        if (!clockEl) return;
        clockEl.textContent = normalizeCalendarCopy(clockEl.textContent);
    }

    const buildGreetingModelOriginal = buildGreetingModel;
    buildGreetingModel = function buildGreetingModelPatched(ctx) {
        const periodTone = {
            morning: 'Good morning',
            afternoon: 'Good afternoon',
            evening: 'Good evening',
            night: 'Good night'
        };

        const weatherTone = {
            clear: { icon: '\u2600\uFE0F', accent: 'from-amber-300/30 to-orange-300/20', sub: 'Clear conditions. Great window for high-energy work.' },
            sunny: { icon: '\uD83C\uDF24\uFE0F', accent: 'from-amber-300/30 to-orange-300/20', sub: 'Sunlight is on your side. Keep momentum steady.' },
            rainy: { icon: '\uD83C\uDF27\uFE0F', accent: 'from-sky-300/25 to-slate-300/15', sub: 'Rainy conditions. Ideal for focused indoor sessions.' },
            cloudy: { icon: '\u2601\uFE0F', accent: 'from-slate-300/25 to-slate-400/15', sub: 'Cloud cover today. Keep your pace deliberate and calm.' },
            'partly-cloudy': { icon: '\u26C5', accent: 'from-sky-300/25 to-amber-300/20', sub: 'Mixed skies. A good day for balanced effort.' }
        };

        const weatherData = weatherTone[ctx.weather] || weatherTone['partly-cloudy'];
        return {
            title: periodTone[ctx.period] || periodTone.afternoon,
            subtitle: weatherData.sub,
            icon: weatherData.icon,
            accent: weatherData.accent,
            themeEnabled: ctx.themeEnabled
        };
    };

    addMotivationalMessage = function addMotivationalMessagePatched() {
        const completionSection = document.querySelector('[data-testid="day-hub-header"] section[aria-label="Day completion"]');
        if (!completionSection) return;

        const percentageText = completionSection.querySelector('p.text-sm.font-semibold span');
        if (!percentageText) return;

        const percentage = parseInt(percentageText.textContent, 10) || 0;
        const messages = [
            { range: [0, 20], text: "Let's get started! \uD83D\uDCAA", color: 'text-slate-600 dark:text-slate-400' },
            { range: [21, 40], text: 'Making progress! \uD83C\uDFAF', color: 'text-blue-600 dark:text-blue-400' },
            { range: [41, 60], text: 'Halfway there! \uD83D\uDE80', color: 'text-indigo-600 dark:text-indigo-400' },
            { range: [61, 80], text: 'Almost done! \u2B50', color: 'text-amber-600 dark:text-amber-400' },
            { range: [81, 99], text: 'Final stretch! \uD83D\uDD25', color: 'text-orange-600 dark:text-orange-400' },
            { range: [100, 100], text: 'Perfect day! \uD83C\uDF89', color: 'text-emerald-600 dark:text-emerald-400' }
        ];

        const message = messages.find((entry) => percentage >= entry.range[0] && percentage <= entry.range[1]);
        const existing = document.getElementById('motivation-message');
        if (!message) {
            existing?.remove();
            return;
        }

        const msgDiv = existing || document.createElement('p');
        msgDiv.id = 'motivation-message';
        msgDiv.className = `mt-2 text-xs font-medium ${message.color}`;
        msgDiv.textContent = message.text;
        if (!existing) {
            completionSection.appendChild(msgDiv);
        }
    };

    refreshMainCompletionProgress = function refreshMainCompletionProgressPatched() {
        const root = document.querySelector('[data-main-progress]');
        const fill = document.getElementById('day-main-progress-fill');
        if (!root || !fill) return;

        const tasks = Array.from(document.querySelectorAll('[data-task-item]'));
        const totalTasks = tasks.length;
        const doneTasks = tasks.filter((task) => (task.getAttribute('data-task-status') || '') === 'done').length;

        const workoutItems = Array.from(document.querySelectorAll('[data-workout-item], [data-occurrence-item]'));
        const totalWorkouts = workoutItems.length;
        const doneWorkouts = workoutItems.filter((item) => {
            const value = item.getAttribute('data-workout-completed') || item.getAttribute('data-completed') || 'false';
            return value === 'true';
        }).length;

        const total = totalTasks + totalWorkouts;
        const done = doneTasks + doneWorkouts;
        const pct = total > 0 ? Math.floor((done / total) * 100) : 0;
        const tasksLeft = Math.max(totalTasks - doneTasks, 0);
        const workoutsLeft = Math.max(totalWorkouts - doneWorkouts, 0);
        const remaining = tasksLeft + workoutsLeft;

        root.setAttribute('data-progress-pct', String(pct));
        root.setAttribute('data-progress-tasks-left', String(tasksLeft));
        root.setAttribute('data-progress-workouts-left', String(workoutsLeft));
        root.setAttribute('data-progress-remaining', String(remaining));
        fill.style.width = pct + '%';

        const track = root.querySelector('.day-main-progress-track');
        if (track) {
            track.setAttribute('aria-valuenow', String(pct));
        }

        const labelText = done + '/' + total + ' completed (' + pct + '%)';
        const label = document.getElementById('day-main-progress-label');
        if (label) {
            label.textContent = labelText;
        }

        const summaryChip = document.querySelector('[data-testid="day-completion-summary"]');
        if (summaryChip) {
            summaryChip.textContent = labelText;
        }

        updateCompletionFeedback(root, total, remaining, tasksLeft, workoutsLeft);

        const overviewProgress = document.getElementById('overview-progress-copy');
        if (overviewProgress) {
            overviewProgress.textContent = labelText;
        }

        const overviewRemainingTotal = document.getElementById('overview-remaining-total');
        if (overviewRemainingTotal) {
            overviewRemainingTotal.textContent = remaining + ' left';
        }

        const overviewRemainingBreakdown = document.getElementById('overview-remaining-breakdown');
        if (overviewRemainingBreakdown) {
            overviewRemainingBreakdown.textContent = tasksLeft + ' tasks | ' + workoutsLeft + ' workouts';
        }

        const dayStatDone = document.getElementById('day-stat-done');
        if (dayStatDone) {
            dayStatDone.textContent = String(done);
        }

        const dayStatTotal = document.getElementById('day-stat-total');
        if (dayStatTotal) {
            dayStatTotal.textContent = String(total);
        }

        const dayStatTasksLeft = document.getElementById('day-stat-tasks-left');
        if (dayStatTasksLeft) {
            dayStatTasksLeft.textContent = String(tasksLeft);
        }

        const dayStatWorkoutsLeft = document.getElementById('day-stat-workouts-left');
        if (dayStatWorkoutsLeft) {
            dayStatWorkoutsLeft.textContent = String(workoutsLeft);
        }

        const overviewNext = document.getElementById('overview-next-priority');
        if (overviewNext) {
            if (tasksLeft > 0) {
                overviewNext.textContent = 'Clear your next task block.';
            } else if (workoutsLeft > 0) {
                overviewNext.textContent = 'Complete your next workout block.';
            } else {
                overviewNext.textContent = total === 0 ? root.dataset.emptyCopy : root.dataset.completeCopy;
            }
        }

        renderDayInsights();
        sanitizeTimelineBanner();
        sanitizeLiveClockCopy();
    };

    showKeyboardHelp = function showKeyboardHelpPatched() {
        if (document.getElementById('keyboard-help-modal')) return;

        const modal = document.createElement('div');
        modal.id = 'keyboard-help-modal';
        modal.className = 'fixed inset-0 z-[70] flex items-center justify-center bg-slate-950/70 backdrop-blur-sm';
        modal.innerHTML = `
            <div class="mx-4 w-full max-w-md rounded-2xl border border-slate-200 bg-white p-6 shadow-xl dark:border-slate-800 dark:bg-slate-950">
                <div class="flex items-start justify-between">
                    <h3 class="text-lg font-semibold text-slate-900 dark:text-slate-100">Keyboard Shortcuts</h3>
                    <button class="close-help-btn rounded-lg bg-slate-100 px-3 py-1 text-sm font-medium text-slate-900 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-100 dark:hover:bg-slate-700">Close</button>
                </div>
                <div class="mt-4 space-y-2 text-sm">
                    <div class="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 dark:bg-slate-900">
                        <span class="text-slate-700 dark:text-slate-200">New Task</span>
                        <kbd class="rounded bg-white px-2 py-1 text-xs font-semibold text-slate-900 shadow dark:bg-slate-800 dark:text-slate-100">Ctrl+N</kbd>
                    </div>
                    <div class="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 dark:bg-slate-900">
                        <span class="text-slate-700 dark:text-slate-200">Previous Day</span>
                        <kbd class="rounded bg-white px-2 py-1 text-xs font-semibold text-slate-900 shadow dark:bg-slate-800 dark:text-slate-100">\u2190</kbd>
                    </div>
                    <div class="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 dark:bg-slate-900">
                        <span class="text-slate-700 dark:text-slate-200">Next Day</span>
                        <kbd class="rounded bg-white px-2 py-1 text-xs font-semibold text-slate-900 shadow dark:bg-slate-800 dark:text-slate-100">\u2192</kbd>
                    </div>
                    <div class="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 dark:bg-slate-900">
                        <span class="text-slate-700 dark:text-slate-200">Show Help</span>
                        <kbd class="rounded bg-white px-2 py-1 text-xs font-semibold text-slate-900 shadow dark:bg-slate-800 dark:text-slate-100">?</kbd>
                    </div>
                    <div class="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 dark:bg-slate-900">
                        <span class="text-slate-700 dark:text-slate-200">Switch tabs (Tasks/Workouts/Timeline/Overview)</span>
                        <div class="flex gap-1">
                            <kbd class="rounded bg-white px-2 py-1 text-xs font-semibold text-slate-900 shadow dark:bg-slate-800 dark:text-slate-100">1</kbd>
                            <kbd class="rounded bg-white px-2 py-1 text-xs font-semibold text-slate-900 shadow dark:bg-slate-800 dark:text-slate-100">2</kbd>
                            <kbd class="rounded bg-white px-2 py-1 text-xs font-semibold text-slate-900 shadow dark:bg-slate-800 dark:text-slate-100">3</kbd>
                            <kbd class="rounded bg-white px-2 py-1 text-xs font-semibold text-slate-900 shadow dark:bg-slate-800 dark:text-slate-100">4</kbd>
                        </div>
                    </div>
                </div>
            </div>
        `;

        document.body.appendChild(modal);

        function closeModal() {
            modal.remove();
            document.removeEventListener('keydown', handleEscape);
        }

        function handleEscape(event) {
            if (event.key === 'Escape') {
                closeModal();
            }
        }

        modal.addEventListener('click', (event) => {
            if (event.target === modal) closeModal();
        });

        const closeBtn = modal.querySelector('.close-help-btn');
        if (closeBtn) {
            closeBtn.addEventListener('click', closeModal);
        }

        document.addEventListener('keydown', handleEscape);
    };

    renderDayInsights = function renderDayInsightsPatched() {
        const host = document.getElementById('day-insights-output');
        const root = document.querySelector('[data-main-progress]');
        if (!host || !root) return;

        const tasksLeft = parseInt(root.getAttribute('data-progress-tasks-left') || '0', 10);
        const workoutsLeft = parseInt(root.getAttribute('data-progress-workouts-left') || '0', 10);
        const remaining = parseInt(root.getAttribute('data-progress-remaining') || '0', 10);
        const pct = parseInt(root.getAttribute('data-progress-pct') || '0', 10);
        const nowHour = new Date().getHours();

        let type = 'priority';
        let title = 'Priority guidance';
        let body = 'Start with one high-friction item in the next 30 minutes to unlock momentum for this day.';

        if (remaining === 0 && pct > 0) {
            type = 'positive';
            title = 'Positive momentum';
            body = 'Today is complete. Capture one reflection now to reinforce what worked today.';
        } else if (remaining > 0 && nowHour >= 18 && tasksLeft > 0) {
            type = 'caution';
            title = 'Caution';
            body = 'Late-day task load is building. Clear one short task now to prevent today from rolling over.';
        } else if (workoutsLeft > 0 && nowHour >= 12 && nowHour <= 20) {
            type = 'reminder';
            title = 'Reminder';
            body = 'You still have a workout pending today. Reserve a concrete time block before evening closes.';
        }

        const classes = {
            positive: 'border-emerald-200 bg-emerald-50 text-emerald-900 dark:border-emerald-800/40 dark:bg-emerald-950/30 dark:text-emerald-200',
            caution: 'border-amber-200 bg-amber-50 text-amber-900 dark:border-amber-800/40 dark:bg-amber-950/30 dark:text-amber-200',
            reminder: 'border-blue-200 bg-blue-50 text-blue-900 dark:border-blue-800/40 dark:bg-blue-950/30 dark:text-blue-200',
            priority: 'border-slate-200 bg-slate-50 text-slate-800 dark:border-slate-700 dark:bg-slate-900/60 dark:text-slate-200'
        };

        host.innerHTML = '<article class="rounded-xl border p-3 ' + classes[type] + '">'
            + '<p class="text-xs font-semibold uppercase tracking-wide">' + title + '</p>'
            + '<p class="mt-1 text-sm font-medium">' + body + '</p>'
            + '<p class="mt-1 text-xs opacity-80">Remaining: ' + tasksLeft + ' tasks | ' + workoutsLeft + ' workouts</p>'
            + '</article>';
    };

    const buildTimelineOriginal = buildTimeline;
    buildTimeline = function buildTimelinePatched() {
        buildTimelineOriginal();
        sanitizeTimelineBanner();
        sanitizeLiveClockCopy();
    };

    const initOriginal = init;
    init = function initPatched() {
        initOriginal();
        sanitizeTimelineBanner();
        sanitizeLiveClockCopy();
        window.setInterval(() => {
            sanitizeTimelineBanner();
            sanitizeLiveClockCopy();
        }, 1000);
    };

    init();
})();
