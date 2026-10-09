document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("priceChangeForm");
    const price = document.getElementById("newPriceDollars");
    const date = document.getElementById("effectiveDate");
    const preview = document.getElementById("gymPricePreview");
    if (!form || !price || !date || !preview) return;
    const updatePreview = () => {
        const valid = price.value !== "" && price.validity.valid && date.value !== "" && date.validity.valid;
        preview.hidden = !valid;
        if (!valid) return;
        document.getElementById("gymPricePreviewValue").textContent = new Intl.NumberFormat(document.documentElement.lang || "en-GB", { style: "currency", currency: "USD" }).format(Number(price.value));
        document.getElementById("gymPricePreviewDate").textContent = date.value;
    };
    form.addEventListener("input", updatePreview);
    updatePreview();
});
