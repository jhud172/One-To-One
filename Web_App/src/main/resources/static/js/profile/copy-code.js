(function () {
    "use strict";
    function initCopyButton(buttonId, valueId, statusId) {
        const button = document.getElementById(buttonId);
        const value = document.getElementById(valueId);
        const status = document.getElementById(statusId);
        if (!button || !value) return;
        button.addEventListener("click", async () => {
            if (button.disabled) return;
            button.disabled = true;
            try {
                if (!navigator.clipboard?.writeText) throw new Error("Clipboard unavailable");
                await navigator.clipboard.writeText(value.textContent.trim());
                if (status) status.textContent = button.dataset.copied || "";
            } catch {
                if (status) status.textContent = button.dataset.copyFailed || "";
            } finally { button.disabled = false; }
        });
    }
    initCopyButton("copyTrainerCode", "trainerCodeVal", "trainerCodeCopyStatus");
    initCopyButton("copyGymCode", "gymCodeVal", "gymCodeCopyStatus");
}());
