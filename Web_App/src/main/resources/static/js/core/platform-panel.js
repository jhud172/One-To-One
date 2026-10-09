document.addEventListener("DOMContentLoaded", () => {
    const root = document.getElementById("platformPanelRoot");
    if (!root) return;

    document.body.classList.add("has-platform-panel");

    const panel = root.querySelector(".platform-panel");
    const track = document.getElementById("platformPanelTrack");
    const prev = document.getElementById("platformPanelPrev");
    const next = document.getElementById("platformPanelNext");
    const settings = document.getElementById("platformPanelSettings");
    const customizer = document.getElementById("platformPanelCustomizer");
    const close = document.getElementById("platformPanelClose");
    const options = document.getElementById("platformPanelOptions");
    const status = document.getElementById("platformPanelStatus");
    const placementButtons = customizer ? Array.from(customizer.querySelectorAll("[data-placement]")) : [];
    const role = (root.dataset.role || "USER").toUpperCase();
    const storageKey = `oneToOne.platformPanel.v2.${role}`;
    const overlayManager = window.OneToOneOverlay;

    function syncPanelReservation() {
        if (!panel) return;
        const height = Math.ceil(panel.getBoundingClientRect().height);
        if (height > 0) {
            document.body.style.setProperty("--shell-platform-panel-height", `${height}px`);
        }
    }

    syncPanelReservation();
    window.requestAnimationFrame(syncPanelReservation);
    window.addEventListener("load", syncPanelReservation, { once: true });
    document.fonts?.ready.then(syncPanelReservation);
    if (panel && "ResizeObserver" in window) {
        new ResizeObserver(syncPanelReservation).observe(panel);
    } else {
        window.addEventListener("resize", syncPanelReservation, { passive: true });
    }

    const actions = [
        { key: "client-dashboard", label: "My dashboard", href: "/dashboard", icon: "grid", roles: ["USER", "CLIENT"] },
        { key: "trainer-dashboard", label: "Trainer dashboard", href: "/trainer/dashboard", icon: "grid", roles: ["TRAINER"] },
        { key: "gym-dashboard", label: "Gym dashboard", href: "/gym/dashboard", icon: "grid", roles: ["GYM_ADMIN"] },
        { key: "admin-dashboard", label: "Platform operations", href: "/admin/dashboard", icon: "grid", roles: ["PLATFORM_ADMIN", "SUPER_ADMIN"] },
        { key: "calendar", label: "Calendar", href: "/calendar", icon: "calendar", roles: ["USER", "CLIENT", "TRAINER"] },
        { key: "charlie", label: "Charlie", href: "#charlie", icon: "spark", roles: ["USER", "CLIENT", "TRAINER", "GYM_ADMIN", "PLATFORM_ADMIN", "SUPER_ADMIN"] },
        { key: "actions", label: "Action Hub", href: "#actions", icon: "bolt", roles: ["USER", "CLIENT", "TRAINER"] },
        { key: "client-workouts", label: "My workouts", href: "/workout-management", icon: "dumbbell", roles: ["USER", "CLIENT"] },
        { key: "trainer-workouts", label: "Workout studio", href: "/workouts", icon: "dumbbell", roles: ["TRAINER"] },
        { key: "client-goals", label: "My goals", href: "/goals", icon: "target", roles: ["USER", "CLIENT"] },
        { key: "trainer-goals", label: "Client goals", href: "/trainer/clients", icon: "target", roles: ["TRAINER"] },
        { key: "messages", label: "Coaching inbox", href: "/inbox", icon: "message", roles: ["USER", "CLIENT"] },
        { key: "client-messages", label: "Client inbox", href: "/inbox", icon: "message", roles: ["TRAINER"] },
        { key: "trainer", label: "My trainer", href: "/client/my-trainer", icon: "user", roles: ["USER", "CLIENT"] },
        { key: "clients", label: "Clients", href: "/trainer/clients", icon: "users", roles: ["TRAINER"] },
        { key: "gym-trainers", label: "Trainers", href: "/gym/admin/trainers", icon: "users", roles: ["GYM_ADMIN"] },
        { key: "gym-memberships", label: "Memberships", href: "/gym/admin/memberships", icon: "building", roles: ["GYM_ADMIN"] },
        { key: "gym-support", label: "Gym support", href: "/support", icon: "message", roles: ["GYM_ADMIN"] },
        { key: "support-inbox", label: "Support inbox", href: "/admin/feedback", icon: "message", roles: ["PLATFORM_ADMIN", "SUPER_ADMIN"] },
        { key: "gym-applications", label: "Gym applications", href: "/admin/gym-applications", icon: "building", roles: ["PLATFORM_ADMIN", "SUPER_ADMIN"] },
        { key: "merch-admin", label: "Merch operations", href: "/admin/merch", icon: "bag", roles: ["PLATFORM_ADMIN", "SUPER_ADMIN"] },
        { key: "shop", label: "Shop", href: "/merch", icon: "bag", roles: ["USER", "CLIENT", "TRAINER"] },
        { key: "profile", label: "Profile", href: "/profile", icon: "profile", roles: ["USER", "CLIENT", "TRAINER", "GYM_ADMIN", "PLATFORM_ADMIN", "SUPER_ADMIN"] }
    ];

    const iconPaths = {
        grid: "<path d='M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z' fill='none' stroke='currentColor' stroke-width='1.8' stroke-linejoin='round'/>",
        calendar: "<path d='M7 3v4M17 3v4M4 9h16M6 5h12a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Z' fill='none' stroke='currentColor' stroke-width='1.8' stroke-linecap='round'/>",
        spark: "<path d='M13 2 5 14h5l-1 8 8-12h-5z' fill='none' stroke='currentColor' stroke-width='1.8' stroke-linejoin='round'/>",
        bolt: "<path d='M12 3v18M3 12h18M5 5l14 14M19 5 5 19' fill='none' stroke='currentColor' stroke-width='1.5' stroke-linecap='round'/>",
        dumbbell: "<path d='M5 8v8M9 7v10M15 7v10M19 8v8M9 12h6' fill='none' stroke='currentColor' stroke-width='1.9' stroke-linecap='round'/>",
        target: "<path d='M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm0-4a5 5 0 1 0 0-10 5 5 0 0 0 0 10Zm0-3a2 2 0 1 0 0-4 2 2 0 0 0 0 4Z' fill='none' stroke='currentColor' stroke-width='1.6'/>",
        message: "<path d='M5 6h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2h-6l-4 4v-4H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2Z' fill='none' stroke='currentColor' stroke-width='1.8' stroke-linejoin='round'/>",
        user: "<path d='M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm7 8a7 7 0 0 0-14 0' fill='none' stroke='currentColor' stroke-width='1.8' stroke-linecap='round'/>",
        users: "<path d='M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm6 8a6 6 0 0 0-12 0M17 11a3 3 0 0 0 0-6M21 19a5 5 0 0 0-5-5' fill='none' stroke='currentColor' stroke-width='1.8' stroke-linecap='round'/>",
        building: "<path d='M4 21V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v16M16 9h2a2 2 0 0 1 2 2v10M8 7h4M8 11h4M8 15h4M3 21h18' fill='none' stroke='currentColor' stroke-width='1.8' stroke-linecap='round'/>",
        bag: "<path d='M6 8h12l-1 12H7L6 8Zm3 0a3 3 0 0 1 6 0' fill='none' stroke='currentColor' stroke-width='1.8' stroke-linejoin='round'/>",
        profile: "<path d='M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm8 8a8 8 0 0 0-16 0' fill='none' stroke='currentColor' stroke-width='1.8' stroke-linecap='round'/>"
    };

    const available = actions.filter(action => action.roles.includes(role));
    const defaultKeys = available.slice(0, 7).map(action => action.key);
    let state = loadState();

    function loadState() {
        try {
            const parsed = JSON.parse(localStorage.getItem(storageKey) || "{}");
            return normalizeState(parsed);
        } catch {
            return normalizeState({});
        }
    }

    function normalizeState(input) {
        const validKeys = new Set(available.map(action => action.key));
        const selected = Array.isArray(input.selected)
            ? input.selected.filter(key => validKeys.has(key)).slice(0, 7)
            : [];
        return {
            selected: selected.length ? selected : defaultKeys,
            placement: ["left", "center", "right"].includes(input.placement) ? input.placement : "center"
        };
    }

    function saveState() {
        localStorage.setItem(storageKey, JSON.stringify(state));
    }

    function renderIcon(action) {
        return `<svg viewBox="0 0 24 24" aria-hidden="true">${iconPaths[action.icon] || iconPaths.spark}</svg>`;
    }

    function renderTrack() {
        if (!track) return;
        root.dataset.placement = state.placement;
        track.innerHTML = "";
        state.selected
            .map(key => available.find(action => action.key === key))
            .filter(Boolean)
            .forEach(action => {
                const link = document.createElement("a");
                link.className = "platform-panel__action";
                link.href = action.href;
                link.dataset.action = action.key;
                if (action.key === "charlie" || action.key === "actions") {
                    link.setAttribute("aria-controls", action.key === "charlie" ? "chatPanel" : "quickActionsShelf");
                    link.setAttribute("aria-expanded", "false");
                }
                link.innerHTML = `${renderIcon(action)}<span>${action.label}</span>`;
                link.addEventListener("click", handleActionClick);
                track.appendChild(link);
            });
    }

    function renderOptions() {
        if (!options) return;
        options.innerHTML = "";
        const selected = new Set(state.selected);
        available.forEach(action => {
            const item = document.createElement("label");
            item.className = "platform-panel-option";
            item.innerHTML = `
                <input type="checkbox" value="${action.key}" ${selected.has(action.key) ? "checked" : ""}>
                <span class="platform-panel-option__icon">${renderIcon(action)}</span>
                <span class="platform-panel-option__label">${action.label}</span>
            `;
            const input = item.querySelector("input");
            input.addEventListener("change", () => toggleAction(action.key, input.checked));
            options.appendChild(item);
        });
        updateStatus();
    }

    function toggleAction(key, checked) {
        const next = state.selected.filter(item => item !== key);
        if (checked) {
            if (next.length >= 7) {
                renderOptions();
                flashStatus("Maximum 7 actions");
                return;
            }
            next.push(key);
        }
        if (next.length < 1) {
            renderOptions();
            flashStatus("Choose at least 1 action");
            return;
        }
        state.selected = next;
        saveState();
        renderTrack();
        renderOptions();
    }

    function updateStatus() {
        if (status) status.textContent = `${state.selected.length}/7 selected`;
        placementButtons.forEach(button => {
            button.classList.toggle("is-active", button.dataset.placement === state.placement);
        });
    }

    function flashStatus(text) {
        if (!status) return;
        status.textContent = text;
        status.classList.add("is-warning");
        setTimeout(() => {
            status.classList.remove("is-warning");
            updateStatus();
        }, 1600);
    }

    function handleActionClick(event) {
        const key = event.currentTarget.dataset.action;
        if (key === "charlie") {
            event.preventDefault();
            if (typeof window.toggleChatPanel === "function") {
                window.toggleChatPanel(event.currentTarget);
            } else {
                document.getElementById("chatFab")?.click();
            }
        }
        if (key === "actions") {
            event.preventDefault();
            if (typeof window.toggleQuickActionsShelf === "function") {
                window.toggleQuickActionsShelf(event.currentTarget);
            } else {
                document.getElementById("quickActionsToggle")?.click();
            }
        }
    }

    function setCustomizer(open, options = {}) {
        const focusWasInsideCustomizer = Boolean(customizer?.contains(document.activeElement));
        if (open && !options.fromOverlayManager) overlayManager?.open("platform-customizer");
        if (!open && !options.fromOverlayManager) overlayManager?.release("platform-customizer");
        customizer?.classList.toggle("is-open", open);
        customizer?.setAttribute("aria-hidden", open ? "false" : "true");
        customizer?.toggleAttribute("inert", !open);
        settings?.setAttribute("aria-expanded", open ? "true" : "false");
        if (!open && options.restoreFocus && focusWasInsideCustomizer) settings?.focus();
    }

    prev?.addEventListener("click", () => track?.scrollBy({ left: -260, behavior: "smooth" }));
    next?.addEventListener("click", () => track?.scrollBy({ left: 260, behavior: "smooth" }));
    settings?.addEventListener("click", () => setCustomizer(!customizer?.classList.contains("is-open")));
    close?.addEventListener("click", () => setCustomizer(false, { restoreFocus: true }));
    document.addEventListener("keydown", event => {
        if (event.key === "Escape" && customizer?.classList.contains("is-open")) {
            setCustomizer(false, { restoreFocus: true });
        }
    });
    placementButtons.forEach(button => {
        button.addEventListener("click", () => {
            state.placement = button.dataset.placement;
            saveState();
            renderTrack();
            updateStatus();
        });
    });

    overlayManager?.register("platform-customizer", {
        close: (options) => setCustomizer(false, { ...options, fromOverlayManager: true })
    });

    renderTrack();
    renderOptions();
    setCustomizer(false, { fromOverlayManager: true });
});
