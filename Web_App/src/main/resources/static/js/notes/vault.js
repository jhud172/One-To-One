document.addEventListener("DOMContentLoaded", () => {
    const workspace = document.querySelector(".vault-workspace-v2");
    if (!workspace || workspace.dataset.vaultInitialised === "true") return;
    workspace.dataset.vaultInitialised = "true";
    const selection = document.getElementById("vaultSelection");
    const count = document.getElementById("vaultSelectionCount");
    const choices = [...workspace.querySelectorAll('input[name="noteIds"][form="vaultSelection"]')];
    const unavailable = new Set(choices.filter(input => input.disabled));
    const updateSelection = () => {
        const selected = choices.filter(input => input.checked).length;
        if (count) count.textContent = `${selected} / 20`;
        choices.forEach(input => { input.disabled = unavailable.has(input) || (selected >= 20 && !input.checked); });
    };
    choices.forEach(input => input.addEventListener("change", updateSelection));
    updateSelection();
    if (selection) selection.addEventListener("submit", event => {
        if (!choices.some(input => input.checked)) {
            event.preventDefault();
            choices[0]?.focus();
        }
    });
    const editor = workspace.querySelector("[data-vault-editor]");
    if (!editor) return;
    const status = editor.querySelector(".vault-editor-status");
    let dirty = editor.dataset.retained === "true";
    editor.addEventListener("input", () => {
        dirty = true;
        if (status) status.textContent = editor.dataset.unsaved || "";
    });
    editor.addEventListener("submit", () => { dirty = false; });
    window.addEventListener("beforeunload", event => {
        if (dirty) { event.preventDefault(); event.returnValue = ""; }
    });
});
