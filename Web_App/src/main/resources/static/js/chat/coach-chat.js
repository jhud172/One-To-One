document.addEventListener("DOMContentLoaded", () => {
    const root = document.getElementById("coachChat");
    const page = document.getElementById("coachChatPage");
    if (!root) return;

    const listEl = document.getElementById("coachConversationList");
    const searchEl = document.getElementById("coachSearch");
    const newChatBtn = document.getElementById("coachNewChat");
    const messagesEl = document.getElementById("coachMessages");
    const inputEl = document.getElementById("coachInput");
    const sendBtn = document.getElementById("coachSend");
    const usageBadge = document.getElementById("coachUsageBadge");
    const limitModal = document.getElementById("limitModal");
    const limitModalClose = document.getElementById("limitModalClose");
    const focusModeBtn = document.getElementById("focusModeBtn");
    const metricsRefreshBtn = document.getElementById("metricsRefreshBtn");

    const csrfToken = document.getElementById("chat_csrf")?.value || "";
    const csrfHeader = document.getElementById("chat_csrf_header")?.value || "X-CSRF-TOKEN";

    const isPremium = root.dataset.premium === "true";
    let conversations = [];
    let activeId = null;
    let sending = false;
    let loading = false;
    let conversationRequest = 0;
    let modalOrigin = null;
    const copy = key => root.dataset[key] || key;

    function announce(text) {
        const status = document.getElementById("coachStatus");
        if (status) status.textContent = text;
    }

    function setBusy(busy) {
        sending = busy;
        sendBtn.disabled = busy;
        newChatBtn.disabled = busy;
        inputEl.readOnly = busy;
        root.setAttribute("aria-busy", String(busy));
    }

    // ── Time-of-day theme initialiser ─────────────────────────────────────
    function initTimeTheme() {
        if (!page) return;
        const hour = new Date().getHours();
        let theme;
        if (hour >= 5 && hour < 12) theme = "morning";
        else if (hour >= 12 && hour < 17) theme = "midday";
        else if (hour >= 17 && hour < 21) theme = "evening";
        else theme = "night";
        // The server already sets data-chat-time via Thymeleaf; JS refines using local time
        page.setAttribute("data-chat-time", theme);
    }

    initTimeTheme();

    // ── Focus mode ────────────────────────────────────────────────────────
    function initFocusMode() {
        if (!page || !focusModeBtn) return;
        let saved = false;
        try { saved = localStorage.getItem("chatFocusMode") === "true"; } catch { /* Optional preference. */ }
        focusModeBtn.setAttribute("aria-pressed", String(saved));
        if (saved) {
            page.classList.add("focus-mode");
            focusModeBtn.classList.add("active");
        }
        focusModeBtn.addEventListener("click", () => {
            const active = page.classList.toggle("focus-mode");
            focusModeBtn.classList.toggle("active", active);
            focusModeBtn.setAttribute("aria-pressed", String(active));
            try { localStorage.setItem("chatFocusMode", String(active)); } catch { /* Optional preference. */ }
        });
    }

    initFocusMode();

    // ── Action chips ──────────────────────────────────────────────────────
    document.querySelectorAll(".chat-action-chip").forEach(chip => {
        chip.addEventListener("click", () => {
            const text = chip.dataset.chipText;
            if (text && inputEl && !sending) {
                inputEl.value = text;
                inputEl.focus();
            }
        });
    });

    // ── Metrics refresh ───────────────────────────────────────────────────
    async function refreshMetrics() {
        try {
            const res = await fetch("/chat/context");
            if (!res.ok) return;
            const data = await res.json();
            updateMetricsPanel(data);
        } catch {
            // Silently fail — metrics will just stay at last known value
        }
    }

    function updateMetricsPanel(data) {
        if (!data) return;
        const set = (id, val) => { const el = document.getElementById(id); if (el) el.textContent = val; };

        const number = value => Math.max(0, Number.isFinite(Number(value)) ? Number(value) : 0);
        const pct = Math.min(100, number(data.completionPct));
        set("metricsCompletionPct", pct + "%");
        set("metricsRingPct", pct + "%");

        const ring = document.getElementById("metricsRingFill");
        if (ring) {
            const offset = 125.66 - pct * 1.2566;
            ring.setAttribute("stroke-dashoffset", offset.toFixed(2));
        }

        const tasksLeft = Math.max(0, number(data.tasksTotal) - number(data.tasksDone));
        const workoutsLeft = Math.max(0, number(data.workoutsTotal) - number(data.workoutsDone));
        set("metricsTasksLeft", tasksLeft);
        set("metricsWorkoutsLeft", workoutsLeft);
        set("metricsStreak", number(data.streakDays) > 0 ? number(data.streakDays) + " " + copy("days") : "—");

        const nextEl = document.getElementById("metricsNextWorkout");
        if (nextEl) {
            nextEl.textContent = data.nextWorkoutName
                ? (data.nextWorkoutName + (data.nextWorkoutDate ? " · " + data.nextWorkoutDate : ""))
                : "—";
        }

        // Context and conversation copy are untrusted: render them as text only.
        for (const [id, prefix] of [["insights7DayPanel", "sevenDay"], ["insights30DayPanel", "thirtyDay"]]) {
            const panel = document.getElementById(id);
            if (!panel || data[prefix + "TasksTotal"] == null) continue;
            panel.textContent = `${copy("tasks")} ${number(data[prefix + "TasksCompleted"])}/${number(data[prefix + "TasksTotal"])} ${copy("workouts")} ${number(data[prefix + "WorkoutsCompleted"])}/${number(data[prefix + "WorkoutsTotal"])} · ${copy("missed")} ${number(data[prefix + "MissedSessions"])} `;
            if (prefix === "sevenDay" && data.trendNote) {
                const trend = document.createElement("p");
                trend.className = "coach-trend-note";
                trend.textContent = data.trendNote;
                panel.appendChild(trend);
            }
        }
    }

    metricsRefreshBtn?.addEventListener("click", refreshMetrics);

    // ── Helpers ───────────────────────────────────────────────────────────
    function headers() {
        const out = { "Content-Type": "application/json" };
        if (csrfToken) out[csrfHeader] = csrfToken;
        return out;
    }

    function showModal() {
        if (!limitModal) return;
        modalOrigin = document.activeElement;
        window.OneToOneOverlay?.open("coach-limit");
        limitModal.showModal();
        limitModalClose.focus();
    }

    function hideModal(restoreFocus = true) {
        if (!limitModal) return;
        if (limitModal.open) limitModal.close();
        window.OneToOneOverlay?.release("coach-limit");
        if (restoreFocus && modalOrigin?.isConnected) modalOrigin.focus();
    }

    function updateUsage(usage) {
        if (isPremium || !usageBadge || !usage) return;
        const remaining = usage.remaining != null ? usage.remaining : null;
        if (remaining != null) {
            usageBadge.textContent = `${remaining} ${copy("leftToday")}`;
        }
    }

    function clearMessages() {
        messagesEl.replaceChildren();
    }

    function addMessage(role, text) {
        const wrap = document.createElement("div");
        wrap.className = role === "user"
            ? "flex justify-end"
            : "flex justify-start";

        const bubble = document.createElement("div");
        if (role === "user") {
            bubble.className = "max-w-[80%] whitespace-pre-wrap rounded-2xl bg-slate-900 px-4 py-3 text-sm text-white shadow-lg dark:bg-slate-100 dark:text-slate-900";
        } else {
            bubble.className = "max-w-[80%] whitespace-pre-wrap rounded-2xl border border-slate-200/70 bg-gradient-to-br from-white to-slate-50 px-4 py-3 text-sm text-slate-800 shadow-sm dark:border-slate-800/60 dark:from-slate-950 dark:to-slate-900 dark:text-slate-100";
        }
        bubble.classList.add("coach-message", role === "user" ? "coach-message-user" : "coach-message-assistant");
        bubble.textContent = text;
        wrap.appendChild(bubble);
        messagesEl.appendChild(wrap);
        messagesEl.scrollTop = messagesEl.scrollHeight;
        return wrap;
    }

    function addTyping() {
        const wrap = document.createElement("div");
        wrap.id = "coachTyping";
        wrap.className = "flex justify-start";
        const bubble = document.createElement("div");
        bubble.className = "max-w-[60%] rounded-2xl border border-slate-200/60 bg-slate-50 px-4 py-3 text-xs italic text-slate-500 dark:border-slate-800/60 dark:bg-slate-900/60 dark:text-slate-400";
        bubble.textContent = copy("loading");
        wrap.appendChild(bubble);
        messagesEl.appendChild(wrap);
        messagesEl.scrollTop = messagesEl.scrollHeight;
    }

    function removeTyping() {
        const t = document.getElementById("coachTyping");
        if (t) t.remove();
    }

    function renderConversations(list) {
        listEl.replaceChildren();
        if (!list.length) {
            const empty = document.createElement("div");
            empty.className = "rounded-xl border border-dashed border-slate-200 bg-slate-50 px-3 py-4 text-center text-xs text-slate-500 dark:border-slate-800 dark:bg-slate-900/40 dark:text-slate-400";
            empty.textContent = copy("noMessages");
            listEl.appendChild(empty);
            return;
        }
        list.forEach(conv => {
            const btn = document.createElement("button");
            btn.type = "button";
            btn.dataset.id = conv.id;
            btn.className = `w-full rounded-xl border px-3 py-3 text-left text-sm shadow-sm transition ${
                conv.id === activeId
                    ? "border-slate-900 bg-slate-900 text-white"
                    : "border-slate-200 bg-white text-slate-800 hover:bg-slate-50 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-100 dark:hover:bg-slate-900"
            }`;
            const title = document.createElement("div");
            title.className = "font-semibold";
            title.textContent = conv.title || copy("newChat");
            btn.appendChild(title);
            btn.setAttribute("aria-pressed", String(conv.id === activeId));
            btn.disabled = sending || loading;
            btn.addEventListener("click", () => openConversation(conv.id));
            listEl.appendChild(btn);
        });
    }

    async function loadConversations() {
        const res = await fetch("/chat/conversations");
        if (!res.ok) throw new Error("conversation-list");
        conversations = await res.json();
        const filtered = filterConversations(conversations);
        renderConversations(filtered);
        if (!activeId && conversations.length > 0) {
            await openConversation(conversations[0].id);
        }
    }

    function filterConversations(list) {
        const query = (searchEl?.value || "").trim().toLowerCase();
        if (!query) return list;
        return list.filter(c => (c.title || "").toLowerCase().includes(query));
    }

    async function openConversation(id) {
        if (sending || loading) return;
        const request = ++conversationRequest;
        loading = true;
        sendBtn.disabled = true;
        newChatBtn.disabled = true;
        renderConversations(filterConversations(conversations));
        announce(copy("loading"));
        try {
            const res = await fetch(`/chat/conversations/${id}/messages?limit=200`);
            if (!res.ok) throw new Error("conversation-load");
            const data = await res.json();
            if (request !== conversationRequest) return;
            activeId = id;
            clearMessages();
            if (!data.length) addMessage("assistant", copy("greeting"));
            data.forEach(m => addMessage(m.role === "user" ? "user" : "assistant", m.content));
            announce("");
        } catch {
            announce(copy("error"));
        } finally {
            loading = false;
            sendBtn.disabled = false;
            newChatBtn.disabled = false;
            renderConversations(filterConversations(conversations));
        }
    }

    async function createConversation() {
        const res = await fetch("/chat/conversations", { method: "POST", headers: headers() });
        if (!res.ok) throw new Error("conversation-create");
        const data = await res.json();
        if (data?.id) {
            activeId = data.id;
            conversations.unshift({id: data.id, title: data.title});
            clearMessages();
            renderConversations(filterConversations(conversations));
        }
        return data?.id || null;
    }

    async function sendMessage() {
        if (sending || loading) return;
        const text = (inputEl.value || "").trim();
        if (!text) return;
        setBusy(true);
        announce("");
        let userRow = null;
        try {
            if (!activeId) await createConversation();
            if (!activeId) throw new Error("conversation-missing");
            const greetingCard = document.getElementById("chatGreetingCard");
            if (greetingCard) greetingCard.hidden = true;
            userRow = addMessage("user", text);
            addTyping();
            const res = await fetch(`/chat/conversations/${activeId}/messages`, {
                method: "POST",
                headers: headers(),
                body: JSON.stringify({ message: text })
            });

            removeTyping();

            if (res.status === 429) {
                showModal();
                return;
            }

            const data = await res.json();
            if (!res.ok) {
                announce(copy("error"));
                return;
            }

            if (!data.reply) throw new Error("reply-missing");
            inputEl.value = "";
            userRow = null;
            addMessage("assistant", data.reply);
            updateUsage(data.usage);
            try { await loadConversations(); } catch { announce(copy("error")); }

            // Refresh metrics after each message (lightweight, throttled by natural send cadence)
            refreshMetrics();
        } catch {
            removeTyping();
            announce(copy("error"));
        } finally {
            removeTyping();
            userRow?.remove();
            setBusy(false);
            renderConversations(filterConversations(conversations));
        }
    }

    newChatBtn?.addEventListener("click", async () => {
        if (sending || loading) return;
        setBusy(true);
        try { await createConversation(); announce(""); }
        catch { announce(copy("error")); }
        finally { setBusy(false); renderConversations(filterConversations(conversations)); }
    });

    sendBtn?.addEventListener("click", sendMessage);

    inputEl?.addEventListener("keydown", (event) => {
        if (event.key === "Enter" && !event.shiftKey && !event.isComposing) {
            event.preventDefault();
            sendMessage();
        }
    });

    searchEl?.addEventListener("input", () => {
        renderConversations(filterConversations(conversations));
    });

    limitModalClose?.addEventListener("click", () => hideModal());
    limitModal?.addEventListener("cancel", event => { event.preventDefault(); hideModal(); });
    window.OneToOneOverlay?.register("coach-limit", {group: "modal", close: options => hideModal(options.restoreFocus)});
    limitModal?.addEventListener("click", (event) => {
        if (event.target === limitModal) hideModal();
    });

    async function reloadConversations() {
        if (sending || loading) return;
        const retry = document.getElementById("coachRetry");
        if (retry) retry.disabled = true;
        try { await loadConversations(); announce(""); }
        catch { announce(copy("error")); }
        finally { if (retry) retry.disabled = false; }
    }
    document.getElementById("coachRetry")?.addEventListener("click", reloadConversations);
    reloadConversations();
});
