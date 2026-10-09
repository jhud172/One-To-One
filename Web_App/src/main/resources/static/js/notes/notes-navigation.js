document.addEventListener("DOMContentLoaded", () => {
    const controls = document.getElementById("notesFolderControls");
    if (!controls || controls.dataset.initialised || !window.matchMedia) return;
    controls.dataset.initialised = "true";
    const wide = window.matchMedia("(min-width: 1024px)");
    let chosen = false;
    const adapt = () => {
        if (!chosen) controls.open = wide.matches || Boolean(document.querySelector(".notes-workspace-v2 [role=alert]"));
    };
    adapt();
    wide.addEventListener("change", adapt);
    controls.querySelector("summary").addEventListener("click", () => { chosen = true; });
});
