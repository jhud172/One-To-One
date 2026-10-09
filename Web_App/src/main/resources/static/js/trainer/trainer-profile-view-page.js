(function () {
    'use strict';
    const button = document.getElementById('copyTrainerCode');
    const code = document.getElementById('trainerCodeVal');
    const status = document.getElementById('trainerCodeCopyStatus');
    if (!button || !code || !status) return;
    button.addEventListener('click', async () => {
        if (button.disabled) return;
        button.disabled = true;
        try {
            if (!navigator.clipboard?.writeText) throw new Error('Clipboard unavailable');
            await navigator.clipboard.writeText(code.textContent.trim());
            status.textContent = button.dataset.copied;
        } catch {
            status.textContent = button.dataset.copyFailed;
        } finally {
            button.disabled = false;
        }
    });
}());
