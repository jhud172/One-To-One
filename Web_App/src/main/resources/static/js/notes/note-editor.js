document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("noteForm"), editor = document.getElementById("editor");
    const content = document.getElementById("contentHtml"), plain = document.getElementById("plainContent");
    if (!form || !editor || !content || !plain || form.dataset.editorInitialised || typeof document.execCommand !== "function") return;
    form.dataset.editorInitialised = "true";
    editor.setAttribute("dir", "auto");
    form.querySelectorAll("#noteFormattingToolbar button, #noteFormattingToolbar select").forEach(control => {
        if (control.title) control.setAttribute("aria-label", control.title);
    });
    plain.disabled = true; document.getElementById("notesNativeContent").hidden = true;
    document.getElementById("editorShell").hidden = false; document.getElementById("noteFormattingToolbar").hidden = false;
    let dirty = false, submitting = false;
    const changed = () => { dirty = true; };
    editor.addEventListener("input", changed);
    form.addEventListener("input", changed);
    const command = (name, value = null) => { editor.focus(); document.execCommand(name, false, value); changed(); };
    document.querySelectorAll(".tool[data-cmd]").forEach(button => {
        button.addEventListener("click", () => command(button.dataset.cmd));
    });
    document.querySelectorAll(".tool[data-align]").forEach(button => {
        const commands = { left: "justifyLeft", center: "justifyCenter", right: "justifyRight" };
        button.addEventListener("click", () => command(commands[button.dataset.align]));
    });
    document.getElementById("headingSelect")?.addEventListener("change", event => command("formatBlock", event.target.value));
    document.getElementById("fontSizeSelect")?.addEventListener("change", event => command("fontSize", event.target.value));
    document.getElementById("linkBtn")?.addEventListener("click", () => {
        const value = window.prompt(form.dataset.linkPrompt);
        if (!value) return;
        try {
            const url = new URL(value.trim());
            if (!["https:", "http:", "mailto:"].includes(url.protocol) || url.username || url.password) return;
            command("createLink", url.href);
        } catch { /* Invalid links leave the current text and selection unchanged. */ }
    });
    document.getElementById("highlightBtn")?.addEventListener("click", () => command("hiliteColor", "rgba(34,211,238,0.25)"));
    document.getElementById("clearBtn")?.addEventListener("click", () => command("removeFormat"));
    form.addEventListener("submit", event => {
        content.value = editor.innerHTML.trim();
        if (content.value.length > 20000) {
            event.preventDefault();
            let feedback = document.getElementById("notesEditorError");
            if (!feedback) {
                feedback = document.createElement("p"); feedback.id = "notesEditorError";
                feedback.className = "notes-save-error"; feedback.setAttribute("role", "alert"); form.prepend(feedback);
            }
            feedback.textContent = form.dataset.invalid;
            editor.focus(); return;
        }
        submitting = true;
    });
    editor.addEventListener("keydown", event => {
        if (!(event.ctrlKey || event.metaKey)) return;
        const name = { b: "bold", i: "italic", u: "underline" }[event.key.toLowerCase()];
        if (name) { event.preventDefault(); command(name); }
    });
    window.addEventListener("beforeunload", event => {
        if (dirty && !submitting) { event.preventDefault(); event.returnValue = ""; }
    });
});
