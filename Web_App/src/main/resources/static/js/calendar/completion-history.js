(() => {
    const format = (copy, values) => (copy || '').replace(/\{(\d+)\}/g, (match, index) => values[Number(index)] ?? match);
    function updateHistory() {
        const root = document.getElementById('day-main-content');
        const history = document.querySelector('.calendar-history');
        const selected = history?.querySelector('.calendar-history__day[aria-current="date"]');
        if (!root || !selected) return;
        const tasks = [...document.querySelectorAll('[data-task-item]')];
        const completedTasks = tasks.filter(task => task.dataset.taskStatus === 'done'
            && (task.dataset.taskRequiresLog !== 'true' || task.dataset.taskHasLog === 'true')).length;
        const totalTasks = Number(selected.dataset.totalTasks);
        const totalWorkouts = Number(selected.dataset.totalWorkouts);
        const completedWorkouts = Number(selected.dataset.completedWorkouts);
        const total = totalTasks + totalWorkouts;
        const done = completedTasks + completedWorkouts;
        const percentage = total ? Math.floor(done * 100 / total) : 0;
        const tasksLeft = Math.max(0, totalTasks - completedTasks);
        const workoutsLeft = Math.max(0, totalWorkouts - completedWorkouts);
        const status = total === 0 ? 'empty' : done === total ? 'complete' : done > 0
            ? (root.dataset.date === root.dataset.today ? 'active' : 'partial')
            : root.dataset.date < root.dataset.today ? 'unfinished' : 'planned';
        const label = history.getAttribute('data-status-' + status);
        selected.dataset.status = status;
        selected.setAttribute('aria-label', format(history.dataset.dayCopy, [selected.dataset.dateLabel, label, done, total]));
        selected.querySelector('.calendar-history__count').textContent = `${done}/${total}`;
        const row = [...history.querySelectorAll('.calendar-history__rows li')].find(item => item.dataset.date === selected.dataset.date);
        for (const host of [selected, row].filter(Boolean)) {
            host.querySelector('[data-history-status]').textContent = label;
            host.querySelector('[data-history-summary]').textContent = format(history.dataset.summaryCopy, [done, total, percentage]);
        }
        selected.querySelector('[data-history-tasks]').textContent = format(history.dataset.tasksCopy, [tasksLeft]);
        selected.querySelector('[data-history-workouts]').textContent = format(history.dataset.workoutsCopy, [workoutsLeft]);
        if (row) row.querySelector('[data-history-breakdown]').textContent = format(history.dataset.breakdownCopy,
            [tasksLeft, workoutsLeft, selected.dataset.logsNeeded]);
    }
    document.addEventListener('calendar:completion-change', updateHistory);
    const history = document.querySelector('.calendar-history');
    history?.addEventListener('keydown', event => {
        const day = event.target.closest('.calendar-history__day');
        if (day && event.key === 'Escape') {
            day.setAttribute('data-tooltip-dismissed', '');
            event.stopPropagation();
        }
    });
    for (const eventName of ['focusin', 'pointerover']) {
        history?.addEventListener(eventName, event => {
            event.target.closest('.calendar-history__day')?.removeAttribute('data-tooltip-dismissed');
        });
    }
})();
