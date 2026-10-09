document.addEventListener("DOMContentLoaded", () => {
    const form = document.querySelector("[data-review-form]");
    if (!form || form.dataset.reviewInitialised === "true") return;
    form.dataset.reviewInitialised = "true";
    const status = form.querySelector(".review-draft-status");
    let dirty = form.dataset.retained === "true";
    form.addEventListener("input", () => {
        dirty = true;
        if (status) status.textContent = form.dataset.unsaved || "";
    });
    form.addEventListener("submit", () => { dirty = false; });
    window.addEventListener("beforeunload", event => {
        if (dirty) { event.preventDefault(); event.returnValue = ""; }
    });
});
