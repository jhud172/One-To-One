document.addEventListener("DOMContentLoaded", () => {
    const root = document.getElementById("quickActionsRoot");
    if (!root) return;

    const isPremium = root.dataset.premium === "true";
    const isAuthenticated = root.dataset.auth === "true";

    const shelf = document.getElementById("quickActionsShelf");
    const toggleBtn = document.getElementById("quickActionsToggle");
    const closeBtn = document.getElementById("quickActionsClose");
    const customizeToggle = document.getElementById("quickActionsCustomizeToggle");
    const viewPane = document.getElementById("quickActionsView");
    const customizePane = document.getElementById("quickActionsCustomize");
    const listEl = document.getElementById("quickActionsList");
    const emptyEl = document.getElementById("quickActionsEmpty");
    const messageEl = document.getElementById("quickActionsMessage");
    const searchInput = document.getElementById("quickActionsSearch");
    const customizeList = document.getElementById("quickActionsCustomizeList");
    const customPrompt = document.getElementById("quickActionsCustomPrompt");
    const customCreate = document.getElementById("quickActionsCustomCreate");
    const customLimit = document.getElementById("quickActionsCustomLimit");
    const customLock = document.getElementById("quickActionsCustomLock");
    const overlayManager = window.OneToOneOverlay;

    const csrfToken = document.getElementById("quick_actions_csrf")?.value || null;
    const csrfHeader = document.getElementById("quick_actions_csrf_header")?.value || "X-CSRF-TOKEN";

    let actions = [];
    let customizeOpen = false;
    let returnFocus = toggleBtn;

    const PREMIUM_BADGE_CLASS = "text-xs font-medium text-amber-600 bg-amber-50 border border-amber-200/60 rounded-md px-1.5 py-0.5 dark:bg-amber-950/40 dark:border-amber-800/60 dark:text-amber-400";

    const ACTION_META = {
        OPEN_CALENDAR: { icon: "🗓️", desc: "View schedule" },
        LOG_NUTRITION: { icon: "🥗", desc: "Log today" },
        START_WORKOUT: { icon: "🏋️", desc: "Start session" },
        PROGRESS_CHECK: { icon: "📈", desc: "Levels + trends" },
        OPEN_NOTES: { icon: "📝", desc: "Capture a note" },
        OPEN_INBOX: { icon: "📥", desc: "See messages" },
        CREATE_TASK: { icon: "✅", desc: "New task" }
    };

    function getActionMeta(action) {
        if (action.type === "CUSTOM_AI") {
            return { icon: "✨", desc: "Custom AI" };
        }
        return ACTION_META[action.actionKey] || { icon: "⚡", desc: action.actionKey || "" };
    }

    function showMessage(text) {
        if (!messageEl) return;
        messageEl.textContent = text;
        messageEl.classList.remove("hidden");
        setTimeout(() => messageEl.classList.add("hidden"), 4000);
    }

    function syncToggleState(isOpen) {
        shelf?.setAttribute("aria-hidden", isOpen ? "false" : "true");
        shelf?.toggleAttribute("inert", !isOpen);
        toggleBtn?.setAttribute("aria-expanded", isOpen ? "true" : "false");
        document.querySelector('#platformPanelTrack [data-action="actions"]')?.setAttribute("aria-expanded", isOpen ? "true" : "false");
        toggleBtn?.setAttribute("aria-label", isOpen ? "Close quick actions" : "Open quick actions");
        toggleBtn?.setAttribute("title", isOpen ? "Close quick actions" : "Open quick actions");
    }

    function openShelf(trigger = document.activeElement) {
        returnFocus = trigger instanceof HTMLElement ? trigger : toggleBtn;
        overlayManager?.open("quick-actions");
        shelf?.classList.add("open");
        syncToggleState(true);
        if (customizeOpen) setCustomizeMode(false);
        window.requestAnimationFrame(() => closeBtn?.focus());
    }

    function closeShelf(options = {}) {
        const focusWasInsideShelf = Boolean(shelf?.contains(document.activeElement));
        shelf?.classList.remove("open");
        syncToggleState(false);
        if (!options.fromOverlayManager) overlayManager?.release("quick-actions");
        if (options.restoreFocus && focusWasInsideShelf) {
            const target = returnFocus?.isConnected ? returnFocus : toggleBtn;
            target?.focus();
        }
    }

    function toggleShelf(trigger = document.activeElement) {
        if (shelf?.classList.contains("open")) {
            closeShelf();
        } else {
            openShelf(trigger);
        }
    }

    window.toggleQuickActionsShelf = toggleShelf;
    toggleBtn?.addEventListener("click", () => toggleShelf(toggleBtn));
    closeBtn?.addEventListener("click", () => closeShelf({ restoreFocus: true }));
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && shelf?.classList.contains("open")) {
            closeShelf({ restoreFocus: true });
        }
    });
    document.addEventListener("click", (event) => {
        if (!shelf?.classList.contains("open")) return;
        if (shelf.contains(event.target) || toggleBtn?.contains(event.target)
            || event.target.closest?.('#platformPanelTrack [data-action="actions"]')) return;
        closeShelf();
    });

    function setCustomizeMode(enabled) {
        customizeOpen = enabled;
        shelf?.setAttribute("data-view", enabled ? "customize" : "main");
        viewPane?.classList.toggle("hidden", enabled);
        customizePane?.classList.toggle("hidden", !enabled);
        if (!enabled) renderActive();
    }

    customizeToggle?.addEventListener("click", () => {
        setCustomizeMode(!customizeOpen);
    });

    overlayManager?.register("quick-actions", {
        close: (options) => closeShelf({ ...options, fromOverlayManager: true })
    });

    setCustomizeMode(false);
    closeShelf({ fromOverlayManager: true });

    function authHeaders() {
        const headers = { "Content-Type": "application/json" };
        if (csrfToken) headers[csrfHeader] = csrfToken;
        return headers;
    }

    async function loadActions() {
        if (!isAuthenticated) {
            actions = [];
            render();
            return;
        }
        try {
            const res = await fetch("/api/quick-actions", { credentials: "same-origin" });
            if (!res.ok) throw new Error("Failed to load actions");
            actions = await res.json();
            render();
        } catch (err) {
            showMessage("Quick actions unavailable.");
        }
    }

    function sortActions(list) {
        return list.slice().sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0));
    }

    function render() {
        renderActive();
        renderCustomize();
    }

    function renderActive() {
        if (!listEl) return;
        listEl.innerHTML = "";
        const active = sortActions(actions).filter(a => a.isActive).slice(0, 10);
        if (emptyEl) emptyEl.classList.toggle("hidden", active.length > 0);

        active.forEach(action => {
            const meta = getActionMeta(action);
            const button = document.createElement("button");
            button.type = "button";
            button.className = "group flex items-center gap-3 rounded-2xl border border-slate-200 bg-white px-3 py-2 text-left text-sm font-semibold text-slate-900 shadow-sm transition hover:border-slate-300 hover:bg-slate-50 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-100 dark:hover:bg-slate-800";
            button.dataset.actionId = action.id;
            button.dataset.actionType = action.type;
            button.dataset.actionKey = action.actionKey || "";
            button.dataset.prompt = action.prompt || "";

            const top = document.createElement("div");
            top.className = "flex items-center justify-center";

            const icon = document.createElement("span");
            icon.className = "grid h-8 w-8 place-items-center rounded-xl border border-slate-200 bg-white text-base shadow-sm dark:border-slate-800 dark:bg-slate-950";
            icon.textContent = meta.icon;
            top.appendChild(icon);

            const badge = document.createElement("span");
            badge.className = action.type === "CUSTOM_AI" && !isPremium
                ? PREMIUM_BADGE_CLASS
                : "text-[10px] text-slate-400";
            badge.textContent = action.type === "CUSTOM_AI" ? (isPremium ? "AI" : "🔒 Premium") : "";

            const content = document.createElement("div");
            content.className = "min-w-0 flex-1";

            const label = document.createElement("div");
            label.className = "truncate text-sm font-semibold text-slate-900 dark:text-slate-100";
            label.textContent = action.name;

            const desc = document.createElement("div");
            desc.className = "mt-0.5 truncate text-xs text-slate-500";
            desc.textContent = meta.desc;

            content.appendChild(label);
            content.appendChild(desc);

            button.appendChild(top);
            button.appendChild(content);
            button.appendChild(badge);

            button.addEventListener("click", () => handleActionClick(action));
            if (action.type === "CUSTOM_AI" && !isPremium) {
                button.classList.add("cursor-not-allowed", "opacity-60");
                button.title = "Premium required";
                button.setAttribute("aria-disabled", "true");
                button.setAttribute("aria-label", `${action.name} — Premium required`);
            }
            listEl.appendChild(button);
        });
    }

    function renderCustomize() {
        if (!customizeList) return;
        const list = sortActions(actions);
        customizeList.innerHTML = "";

        const customCount = list.filter(a => a.type === "CUSTOM_AI").length;
        if (customLimit) customLimit.textContent = `${customCount}/2`;
        if (customLock) customLock.classList.toggle("hidden", isPremium);
        if (customCreate) {
            customCreate.disabled = !isPremium || customCount >= 2;
        }

        list.forEach(action => {
            const item = document.createElement("li");
            item.className = "quick-actions-item flex items-center justify-between gap-3 rounded-2xl border border-slate-200 bg-white px-3 py-2 text-xs text-slate-700 shadow-sm dark:border-slate-800 dark:bg-slate-900 dark:text-slate-200";
            item.dataset.actionId = action.id;
            item.draggable = true;

            const left = document.createElement("div");
            left.className = "flex items-center gap-2";

            const handle = document.createElement("span");
            handle.className = "quick-actions-handle text-slate-400";
            handle.textContent = "⋮⋮";
            left.appendChild(handle);

            const checkbox = document.createElement("input");
            checkbox.type = "checkbox";
            checkbox.checked = action.isActive;
            checkbox.disabled = action.type === "CUSTOM_AI" && !isPremium;
            checkbox.className = "h-4 w-4 rounded border-slate-300 accent-emerald-500";
            checkbox.addEventListener("change", () => toggleActive(action, checkbox));
            left.appendChild(checkbox);

            const meta = getActionMeta(action);
            const label = document.createElement("div");
            label.innerHTML = `<div class="font-semibold">${action.name}</div><div class="text-[10px] text-slate-400">${meta.desc || (action.type === "CUSTOM_AI" ? "Custom AI" : action.actionKey)}</div>`;
            left.appendChild(label);

            item.appendChild(left);
            if (action.type === "CUSTOM_AI" && !isPremium) {
                item.classList.add("cursor-not-allowed");
                const lock = document.createElement("span");
                lock.className = PREMIUM_BADGE_CLASS;
                lock.textContent = "🔒 Premium";
                item.appendChild(lock);
            }
            customizeList.appendChild(item);
        });

        attachDragAndDrop();
        applySearchFilter();
    }

    function applySearchFilter() {
        const term = (searchInput?.value || "").trim().toLowerCase();
        if (!customizeList) return;
        Array.from(customizeList.children).forEach((item) => {
            const id = item.dataset.actionId;
            const action = actions.find(a => String(a.id) === String(id));
            if (!action) return;
            const meta = getActionMeta(action);
            const haystack = `${action.name} ${action.actionKey || ""} ${meta.desc || ""}`.toLowerCase();
            item.classList.toggle("hidden", term && !haystack.includes(term));
        });
    }

    searchInput?.addEventListener("input", applySearchFilter);

    async function toggleActive(action, checkbox) {
        if (action.type === "CUSTOM_AI" && !isPremium) {
            checkbox.checked = false;
            showMessage("Premium required for custom AI actions.");
            return;
        }

        try {
            const res = await fetch(`/api/quick-actions/${action.id}/active`, {
                method: "POST",
                headers: authHeaders(),
                body: JSON.stringify({ active: checkbox.checked })
            });
            if (!res.ok) {
                checkbox.checked = !checkbox.checked;
                if (res.status === 403) {
                    showMessage("Premium required for custom AI actions.");
                } else if (res.status === 400) {
                    showMessage("You can only activate up to 10 actions.");
                }
                return;
            }
            const updated = await res.json();
            const idx = actions.findIndex(a => a.id === updated.id);
            if (idx >= 0) actions[idx] = updated;
            renderActive();
        } catch {
            checkbox.checked = !checkbox.checked;
            showMessage("Unable to update action.");
        }
    }

    async function createCustomAction() {
        if (!customPrompt || !customCreate) return;
        const prompt = customPrompt.value.trim();
        if (!prompt) {
            showMessage("Prompt is required.");
            return;
        }

        const name = await generateActionName(prompt);

        try {
            const res = await fetch("/api/quick-actions/custom", {
                method: "POST",
                headers: authHeaders(),
                body: JSON.stringify({ name, prompt })
            });
            if (!res.ok) {
                if (res.status === 403) {
                    showMessage("Premium required for custom actions.");
                } else {
                    showMessage("Unable to create custom action.");
                }
                return;
            }
            const created = await res.json();
            actions.push(created);
            customPrompt.value = "";
            render();
        } catch {
            showMessage("Unable to create custom action.");
        }
    }

    function fallbackActionName(prompt) {
        const cleaned = (prompt || "")
            .replace(/\s+/g, " ")
            .replace(/[^\w\s-]/g, "")
            .trim();
        if (!cleaned) return "Quick AI action";

        const firstChunk = cleaned.split(/[.!?]/)[0] || cleaned;
        const words = firstChunk.split(" ").filter(Boolean).slice(0, 5);
        const titled = words.map((w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()).join(" ");
        const result = titled || "Quick AI action";
        return result.length > 60 ? `${result.slice(0, 57)}...` : result;
    }

    async function generateActionName(prompt) {
        const fallback = fallbackActionName(prompt);

        try {
            const res = await fetch("/chat/api", {
                method: "POST",
                headers: authHeaders(),
                body: JSON.stringify({
                    message: `Create a concise title (max 5 words) for this quick action prompt. Return ONLY the title text. Prompt: ${prompt}`,
                    skipHistory: true
                })
            });

            if (!res.ok) return fallback;
            const data = await res.json();
            const aiName = (data?.reply || "").replace(/[\n\r"']/g, " ").replace(/\s+/g, " ").trim();
            if (!aiName) return fallback;
            return aiName.length > 60 ? `${aiName.slice(0, 57)}...` : aiName;
        } catch {
            return fallback;
        }
    }

    customCreate?.addEventListener("click", createCustomAction);

    function attachDragAndDrop() {
        if (!customizeList) return;
        let dragItem = null;

        customizeList.querySelectorAll(".quick-actions-item").forEach((item) => {
            item.addEventListener("dragstart", (event) => {
                dragItem = item;
                item.classList.add("dragging");
                event.dataTransfer.effectAllowed = "move";
            });
            item.addEventListener("dragend", () => {
                item.classList.remove("dragging");
            });
        });

        customizeList.addEventListener("dragover", (event) => {
            event.preventDefault();
            const target = event.target.closest(".quick-actions-item");
            if (!target || target === dragItem) return;
            const rect = target.getBoundingClientRect();
            const after = event.clientY > rect.top + rect.height / 2;
            if (after) {
                target.after(dragItem);
            } else {
                target.before(dragItem);
            }
        });

        customizeList.addEventListener("drop", async () => {
            if (!customizeList) return;
            const ids = Array.from(customizeList.querySelectorAll(".quick-actions-item"))
                .map(item => Number(item.dataset.actionId))
                .filter(id => Number.isFinite(id));
            if (ids.length === 0) return;

            try {
                const res = await fetch("/api/quick-actions/reorder", {
                    method: "POST",
                    headers: authHeaders(),
                    body: JSON.stringify({ ids })
                });
                if (!res.ok) {
                    showMessage("Unable to reorder actions.");
                    return;
                }
                actions = await res.json();
                renderActive();
            } catch {
                showMessage("Unable to reorder actions.");
            }
        });
    }

    function buildContext() {
        const selection = window.getSelection?.().toString() || "";
        return {
            title: document.title,
            url: window.location.pathname + window.location.search,
            selection: selection.slice(0, 200)
        };
    }

    async function handleActionClick(action) {
        if (action.type === "CUSTOM_AI") {
            if (!isPremium) {
                showMessage("Premium required for custom AI actions.");
                return;
            }
            await runCustomAction(action);
            return;
        }

        const navigateTo = (url) => {
            closeShelf();
            window.location.href = url;
        };

        switch (action.actionKey) {
            case "OPEN_CALENDAR":
                navigateTo("/calendar");
                break;
            case "OPEN_NOTES":
                navigateTo("/notes");
                break;
            case "LOG_NUTRITION":
                navigateTo("/nutrition/daily-log");
                break;
            case "START_WORKOUT":
                navigateTo("/workout-management");
                break;
            case "PROGRESS_CHECK":
                navigateTo("/levels/me");
                break;
            case "OPEN_INBOX":
                navigateTo("/inbox");
                break;
            case "CREATE_TASK":
                navigateTo("/calendar");
                break;
            default:
                showMessage("Action not available.");
        }
    }

    async function runCustomAction(action) {
        const prompt = action.prompt || "";
        if (!prompt) {
            showMessage("Action prompt missing.");
            return;
        }

        const context = buildContext();
        const message = `You are a quick action assistant. Respond ONLY with JSON in the exact format shown below.\n\nAllowed actions:\n\n1. Create a note:\n{"action": "create_note", "title": "Note title", "content": "Note content"}\n\n2. Navigate to a page:\n{"action": "navigate", "url": "/profile"}\n\n3. Fill a form field (input/textarea/select only, NOT buttons):\n{"action": "fill_form", "selector": "#fieldId", "value": "text to insert"}\n\n4. No action (just inform user):\n{"action": "none", "message": "Explanation why no action was taken"}\n\nRules:\n- url must be a relative path starting with '/'.\n- selector must target a visible input, textarea, or select element on the current page.\n- To change theme/preferences, navigate to /profile instead of using fill_form.\n- Keep responses concise.\n\nUser prompt: ${prompt}\n\nPage context: title='${context.title}', url='${context.url}', selection='${context.selection}'`;

        try {
            const res = await fetch("/chat/api", {
                method: "POST",
                headers: authHeaders(),
                body: JSON.stringify({ message, skipHistory: true })
            });
            const data = await res.json();
            const reply = data?.reply || "";
            const actionObj = parseActionJson(reply);
            if (!actionObj) {
                console.warn("AI response could not be parsed as JSON:", reply);
                showMessage("AI response was not usable. Please try rephrasing your request.");
                return;
            }
            await executeAction(actionObj);
        } catch (err) {
            console.error("AI request failed:", err);
            showMessage("AI request failed. Please try again.");
        }
    }

    function parseActionJson(text) {
        if (!text) return null;
        const match = text.match(/\{[\s\S]*\}/);
        if (!match) return null;
        try {
            return JSON.parse(match[0]);
        } catch {
            return null;
        }
    }

    async function executeAction(actionObj) {
        const action = actionObj.action;
        if (!action) {
            showMessage("AI returned no action.");
            return;
        }

        if (action === "create_note") {
            const title = actionObj.title || "Quick action note";
            const content = actionObj.content || "";
            await createNote(title, content);
            return;
        }

        if (action === "navigate") {
            const url = actionObj.url || "";
            if (url.startsWith("/")) {
                window.location.href = url;
            } else {
                showMessage("Navigation blocked.");
            }
            return;
        }

        if (action === "fill_form") {
            const selector = actionObj.selector || "";
            const value = actionObj.value || "";
            if (!selector) {
                showMessage("No field selector provided.");
                return;
            }
            const field = document.querySelector(selector);
            if (!field) {
                showMessage(`Field not found: ${selector}`);
                return;
            }
            if (!["INPUT", "TEXTAREA", "SELECT"].includes(field.tagName)) {
                showMessage(`Field ${selector} is not a form input.`);
                return;
            }
            field.value = value;
            field.dispatchEvent(new Event("input", { bubbles: true }));
            field.focus();
            showMessage("Field updated.");
            return;
        }

        if (action === "none") {
            showMessage(actionObj.message || "No action taken.");
            return;
        }

        showMessage("Action not permitted.");
    }

    async function createNote(title, content) {
        try {
            const res = await fetch("/notes/api/notes", {
                method: "POST",
                headers: authHeaders(),
                body: JSON.stringify({ title, content })
            });
            if (!res.ok) {
                showMessage("Unable to create note.");
                return;
            }
            const note = await res.json();
            if (note?.id) {
                window.location.href = `/notes/${note.id}`;
            } else {
                showMessage("Note created.");
            }
        } catch {
            showMessage("Unable to create note.");
        }
    }

    loadActions();
});
