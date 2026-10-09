(function () {
    document.querySelectorAll('[data-history-back][hidden]').forEach((trigger) => { trigger.hidden = false; });
    document.addEventListener("click", (event) => {
        const trigger = event.target && event.target.closest
            ? event.target.closest("[data-history-back]")
            : null;

        if (!trigger) return;

        event.preventDefault();

        const fallback = trigger.getAttribute("data-history-fallback") || "/";
        if (window.history.length > 1 && document.referrer) {
            window.history.back();
            return;
        }

        window.location.assign(fallback);
    });
}());
