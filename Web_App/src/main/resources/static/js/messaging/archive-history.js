document.addEventListener("DOMContentLoaded", () => {
    const page = document.querySelector(".assistant-history-v2");
    if (!page || page.dataset.historyInitialized === "true") return;
    page.dataset.historyInitialized = "true";
    const collections = page.querySelector(".assistant-history-collections");
    if (!collections) return;
    const narrow = window.matchMedia("(max-width: 899px)");
    let chosen = false;
    collections.querySelector("summary")?.addEventListener("click", () => { chosen = true; });
    const update = () => { if (!chosen) collections.open = !narrow.matches; };
    narrow.addEventListener("change", update);
    update();
});
