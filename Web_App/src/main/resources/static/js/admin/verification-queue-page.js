(() => {
    const panel = document.getElementById('detailPanel');
    const empty = document.getElementById('detailEmpty');
    if (!panel || !empty) return;
    const fields = {detailTrainerName: 'trainerName', detailTrainerEmail: 'trainerEmail', detailStatus: 'status',
        detailSubmitted: 'submitted', detailReviewed: 'reviewed', detailNotes: 'notes', detailAdminNotes: 'adminNotes'};
    document.querySelectorAll('[data-select-request]').forEach(button => {
        button.disabled = false;
        button.addEventListener('click', () => {
            const row = button.closest('tr');
            if (!row) return;
            Object.entries(fields).forEach(([id, key]) => {
                const target = document.getElementById(id);
                if (target) target.textContent = row.dataset[key] || '—';
            });
            empty.hidden = true;
            panel.classList.remove('hidden');
            panel.setAttribute('tabindex', '-1');
            panel.focus({preventScroll: true});
        });
    });
})();
