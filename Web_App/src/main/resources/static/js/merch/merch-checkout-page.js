document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("merch-checkout-form");
    const quantity = document.getElementById("merch-quantity");
    const total = document.getElementById("merch-order-total");
    if (!form || !quantity || !total) return;
    if (form.dataset.checkoutReady === "true") return;
    form.dataset.checkoutReady = "true";
    const panel = document.getElementById("merch-total-panel");
    if (panel) panel.hidden = false;
    const updateTotal = () => {
        if (!quantity.validity.valid || !/^[0-9]+$/.test(quantity.value)) { total.textContent = "—"; return; }
        const minor = BigInt(form.dataset.unitMinor) * BigInt(quantity.value);
        total.textContent = `£${minor / 100n}.${String(minor % 100n).padStart(2, "0")}`;
    };
    quantity.addEventListener("input", updateTotal); updateTotal();
    let submitting = false;
    form.addEventListener("submit", event => {
        if (event.defaultPrevented) return;
        if (submitting) { event.preventDefault(); return; }
        submitting = true; form.setAttribute("aria-busy", "true");
        queueMicrotask(() => form.querySelectorAll('button[type="submit"]').forEach(button => { button.disabled = true; }));
    });
    window.addEventListener("pageshow", () => {
        submitting = false; form.removeAttribute("aria-busy");
        form.querySelectorAll('button[type="submit"]').forEach(button => { button.disabled = button.dataset.providerDisabled === "true"; });
    });
});
