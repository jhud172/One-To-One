document.addEventListener("DOMContentLoaded", () => {
    const forms = document.querySelectorAll(".gym-create-account form, .gym-notes-editor form");
    let dirty = false;
    forms.forEach(form => {
        form.addEventListener("input", () => { dirty = true; });
        form.addEventListener("submit", event => { if (!event.defaultPrevented) dirty = false; });
    });
    window.addEventListener("beforeunload", event => {
        if (dirty) { event.preventDefault(); event.returnValue = ""; }
    });
});
