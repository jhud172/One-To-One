document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll(".product-card, [data-merch-media]").forEach(card => {
        if (card.dataset.mediaReady === "true") return;
        card.dataset.mediaReady = "true";
        const loadSecondary = () => {
            const image = card.querySelector("img[data-secondary-src]");
            if (image && !image.getAttribute("src")) image.setAttribute("src", image.dataset.secondarySrc);
        };
        card.addEventListener("pointerenter", loadSecondary, { once: true });
        card.addEventListener("focusin", loadSecondary, { once: true });
        card.querySelectorAll("img").forEach(image => {
            const secondary = image.classList.contains("img-back");
            const placeholder = secondary ? null : card.querySelector(".img-placeholder");
            const failed = () => {
                if (secondary) card.classList.remove("merch-secondary-ready");
                image.hidden = true;
                if (placeholder) placeholder.hidden = false;
            };
            const loaded = () => {
                if (image.naturalWidth === 0) { failed(); return; }
                image.hidden = false;
                if (placeholder) placeholder.hidden = true;
                if (secondary) card.classList.add("merch-secondary-ready");
            };
            image.addEventListener("error", failed);
            image.addEventListener("load", loaded);
            // An eager or cached image can finish before this script registers its listeners.
            if (image.complete && image.getAttribute("src")) loaded();
        });
    });
});
