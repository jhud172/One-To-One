(function () {
    "use strict";
    const form = document.getElementById("clientCheckinForm");
    const picker = document.getElementById("checkinTemplatePicker");
    const selection = document.getElementById("checkinTemplate");
    const submit = document.getElementById("clientCheckinSubmit");
    if (!form || !picker || !selection || !submit) return;

    const original = JSON.stringify([...new FormData(form)]);
    const loadedTemplate = form.querySelector('[name="templateId"]').value;
    const hasDraft = form.dataset.checkinDraft === "true";
    function updateDraftWarning() {
        const dirty = hasDraft || JSON.stringify([...new FormData(form)]) !== original;
        if (dirty) picker.setAttribute("data-confirm", picker.dataset.checkinChangeConfirm);
        else picker.removeAttribute("data-confirm");
    }
    form.addEventListener("input", updateDraftWarning);
    form.addEventListener("change", updateDraftWarning);
    selection.addEventListener("change", () => { submit.disabled = selection.value !== loadedTemplate; });
    updateDraftWarning();
}());
