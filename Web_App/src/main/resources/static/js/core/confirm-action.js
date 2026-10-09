(function () {
    "use strict";

    const dialog = document.getElementById("confirmationDialog");
    const messageElement = dialog?.querySelector("#confirmationMessage");
    const cancelButton = dialog?.querySelector("[data-confirm-cancel]");
    const approveButton = dialog?.querySelector("[data-confirm-approve]");
    const supportsDialog = dialog && messageElement && cancelButton && approveButton
        && typeof dialog.showModal === "function";
    const approvedTriggers = new WeakSet();
    const approvedForms = new WeakSet();
    let pending = null;

    function finish(confirmed, restoreFocus = true) {
        const action = pending;
        pending = null;
        if (dialog?.open) dialog.close();
        window.OneToOneOverlay?.release("confirmation");
        if (!action) return;
        if (restoreFocus && action.focus?.isConnected) action.focus.focus();
        if (confirmed) action.run();
    }

    function requestConfirmation(event, message, run, focus) {
        if (!message) return;
        if (!supportsDialog) {
            if (!window.confirm(message)) {
                event.preventDefault();
                event.stopImmediatePropagation();
            }
            return;
        }
        event.preventDefault();
        event.stopImmediatePropagation();
        if (pending) return;
        pending = { run, focus };
        messageElement.textContent = message;
        window.OneToOneOverlay?.open("confirmation");
        dialog.showModal();
        cancelButton.focus();
    }

    if (supportsDialog) {
        window.OneToOneOverlay?.register("confirmation", {
            group: "modal",
            close: options => finish(false, options.restoreFocus)
        });
        cancelButton.addEventListener("click", () => finish(false));
        approveButton.addEventListener("click", () => finish(true));
        dialog.addEventListener("cancel", event => {
            event.preventDefault();
            finish(false);
        });
        dialog.addEventListener("close", () => { if (pending) finish(false); });
    }

    document.addEventListener("click", event => {
        const trigger = event.target?.closest?.("a[data-confirm], button[data-confirm], input[data-confirm]");
        if (!trigger || approvedTriggers.has(trigger)) return;
        requestConfirmation(event, trigger.getAttribute("data-confirm"), () => {
            if (!trigger.isConnected) return;
            approvedTriggers.add(trigger);
            if (trigger.form) approvedForms.add(trigger.form);
            try { trigger.click(); }
            finally {
                approvedTriggers.delete(trigger);
                if (trigger.form) approvedForms.delete(trigger.form);
            }
        }, trigger);
    }, true);

    document.addEventListener("submit", event => {
        const form = event.target;
        if (!(form instanceof HTMLFormElement) || approvedForms.has(form)) return;
        const submitter = event.submitter;
        requestConfirmation(event, form.getAttribute("data-confirm"), () => {
            if (!form.isConnected || (submitter && (!submitter.isConnected || submitter.form !== form))) return;
            approvedForms.add(form);
            try { submitter ? form.requestSubmit(submitter) : form.requestSubmit(); }
            finally { approvedForms.delete(form); }
        }, submitter || document.activeElement);
    }, true);
}());
