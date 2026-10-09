document.addEventListener("DOMContentLoaded", () => {
    const search = document.getElementById("order-search");
    if (!search) return;
    search.addEventListener("input", () => {
        const query = search.value.trim().toLowerCase();
        document.querySelectorAll(".order-card").forEach(card => {
            card.hidden = !(card.textContent || "").toLowerCase().includes(query);
        });
    });
});
