document.addEventListener("DOMContentLoaded", () => {
    const threadRoot = document.getElementById("inboxThreadRoot");
    const listRoot = document.getElementById("inboxThreadList");
    const emptyState = document.getElementById("inboxEmptyState");
    const notificationList = document.getElementById("notificationInboxList");
    const notificationEmpty = document.getElementById("notificationInboxEmpty");
    const notificationReadAll = document.getElementById("notificationInboxReadAll");
    const page = threadRoot || document.getElementById("inboxPage");
    if (!page || page.dataset.inboxInitialized === "true") return;
    page.dataset.inboxInitialized = "true";
    const copy = key => page?.dataset[key] || key;
    const statusElement = document.getElementById("inboxStatus");
    const announce = text => { if (statusElement) statusElement.textContent = text; };
    let threadRequestPending = false;
    let sending = false;
    let messagesSignature = null;
    let threadListVersion = 0;
    let notificationVersion = 0;

    function safeLink(value, internalOnly = false) {
        try {
            const url = new URL(value, location.origin);
            if (!["http:", "https:"].includes(url.protocol) || url.username || url.password
                    || (internalOnly && url.origin !== location.origin)) return null;
            return url.href;
        } catch { return null; }
    }

    const csrfToken = document.getElementById("inbox_csrf")?.value || null;
    const csrfHeader = document.getElementById("inbox_csrf_header")?.value || "X-CSRF-TOKEN";

    const headers = { "Content-Type": "application/json" };
    if (csrfToken) headers[csrfHeader] = csrfToken;
    let notificationSyncChannel = null;
    try {
        if ("BroadcastChannel" in window) notificationSyncChannel = new BroadcastChannel("one-to-one-notifications");
    } catch { /* Cross-tab updates are optional. */ }

    function broadcastNotificationSync(detail) {
        const payload = detail || {};
        window.dispatchEvent(new CustomEvent("one-to-one:notifications-updated", { detail: payload }));
        try {
            notificationSyncChannel?.postMessage(payload);
        } catch (_) {
            // ignore
        }
    }

    function formatDate(value) {
        if (!value) return "";
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return "";
        return date.toLocaleString(document.documentElement.lang || undefined);
    }

    async function fetchThreads() {
        const version = ++threadListVersion;
        try {
            const res = await fetch("/api/inbox/threads", { method: "GET" });
            if (!res.ok) throw new Error("threads-load");
            const data = await res.json();
            if (!Array.isArray(data)) throw new Error("threads-payload");
            if (version === threadListVersion) renderThreads(data);
        } catch { if (version === threadListVersion) announce(copy("loadError")); }
    }

    async function fetchNotifications() {
        if (!notificationList || !notificationEmpty) return;
        const version = ++notificationVersion;
        try {
            const res = await fetch("/api/notifications?limit=50", { method: "GET" });
            if (!res.ok) throw new Error("notifications-load");
            const data = await res.json();
            if (!Array.isArray(data)) throw new Error("notifications-payload");
            if (version === notificationVersion) renderNotifications(data);
        } catch { if (version === notificationVersion) announce(copy("loadError")); }
    }

    function renderThreads(threads) {
        if (!listRoot || !emptyState) return;
        if (!threads.length) {
            listRoot.classList.add("hidden");
            emptyState.classList.remove("hidden");
            return;
        }

        listRoot.classList.remove("hidden");
        emptyState.classList.add("hidden");

        const list = document.createElement("ul");
        list.className = "divide-y divide-slate-200/70 dark:divide-slate-800";

        threads.forEach(thread => {
            const item = document.createElement("li");
            const link = document.createElement("a");
            link.href = `/inbox/${thread.threadId}`;
            link.className = "inbox-thread-link";

            const left = document.createElement("div");
            left.className = "flex min-w-0 items-start gap-3";

            const avatar = document.createElement("div");
            avatar.className = "mt-0.5 inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-slate-100 text-sm font-semibold text-slate-900 ring-1 ring-slate-200/70 dark:bg-slate-900/60 dark:text-slate-100 dark:ring-slate-800";
            avatar.textContent = (thread.title || "•").substring(0, 1).toUpperCase();

            const main = document.createElement("div");
            main.className = "min-w-0";

            const titleRow = document.createElement("div");
            titleRow.className = "flex items-center gap-2";

            const title = document.createElement("p");
            title.className = "truncate text-sm font-semibold text-slate-900 dark:text-slate-100";
            title.textContent = thread.title || copy("conversation");

            titleRow.appendChild(title);

            if (thread.unreadCount && thread.unreadCount > 0) {
                const badge = document.createElement("span");
                badge.className = "inbox-unread-badge inline-flex items-center";
                badge.textContent = thread.unreadCount;
                badge.setAttribute("aria-label", `${thread.unreadCount} ${copy("unreadLabel")}`);
                titleRow.appendChild(badge);
            }

            const snippet = document.createElement("p");
            snippet.className = "mt-1 truncate text-sm text-slate-600 dark:text-slate-300";
            snippet.textContent = thread.lastMessageSnippet || "";

            main.appendChild(titleRow);
            main.appendChild(snippet);

            left.appendChild(avatar);
            left.appendChild(main);

            const right = document.createElement("div");
            right.className = "flex shrink-0 flex-col items-end gap-2";

            const date = document.createElement("span");
            date.className = "text-xs text-slate-500";
            date.textContent = formatDate(thread.lastMessageAt);
            date.dir = "ltr";

            const open = document.createElement("span");
            open.className = "text-xs font-semibold text-slate-400 group-hover:text-slate-600 dark:group-hover:text-slate-300";
            open.textContent = copy("open");

            right.appendChild(date);
            right.appendChild(open);

            link.appendChild(left);
            link.appendChild(right);
            item.appendChild(link);
            list.appendChild(item);
        });

        listRoot.replaceChildren();
        listRoot.appendChild(list);
    }

    function renderNotifications(notifications) {
        if (!notificationList || !notificationEmpty) return;
        notificationList.replaceChildren();
        const unread = notifications.filter(item => !item.readAt && !item.dismissedAt).length;
        const badge = document.getElementById("notificationInboxUnread");
        if (badge) { badge.hidden = unread === 0; badge.textContent = `${unread} ${copy("unreadLabel")}`; }
        if (notificationReadAll) notificationReadAll.disabled = unread === 0;

        if (!notifications.length) {
            notificationEmpty.classList.remove("hidden");
            return;
        }

        notificationEmpty.classList.add("hidden");
        notifications.forEach((notification) => {
            notificationList.appendChild(buildNotificationRow(notification));
        });
    }

    function buildNotificationRow(notification) {
        const row = document.createElement("div");
        const isUnread = !notification.readAt && !notification.dismissedAt;
        row.className = `flex flex-col gap-2 px-6 py-4 transition ${
            isUnread
                ? "bg-emerald-500/5"
                : "bg-white dark:bg-slate-950/10"
        }`;

        const header = document.createElement("div");
        header.className = "flex items-start justify-between gap-3";

        const titleWrap = document.createElement("div");
        titleWrap.className = "flex items-center gap-2 text-sm font-semibold text-slate-900 dark:text-slate-100";

        if (isUnread) {
            const dot = document.createElement("span");
            dot.className = "h-2 w-2 rounded-full bg-emerald-500";
            titleWrap.appendChild(dot);
        }

        const title = document.createElement("span");
        title.textContent = notification.title || copy("notification");
        titleWrap.appendChild(title);

        const actions = document.createElement("div");
        actions.className = "flex items-center gap-2";

        if (isUnread) {
            const markRead = document.createElement("button");
            markRead.type = "button";
            markRead.className = "inline-flex min-h-11 items-center rounded-lg px-2 text-xs font-semibold text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200";
            markRead.textContent = copy("markRead");
            markRead.addEventListener("click", async (e) => {
                e.stopPropagation();
                markRead.disabled = true;
                try { await markNotificationRead(notification.id); await fetchNotifications(); }
                finally { markRead.disabled = false; }
            });
            actions.appendChild(markRead);
        }

        header.appendChild(titleWrap);
        header.appendChild(actions);

        const message = document.createElement("p");
        message.className = "text-sm text-slate-600 dark:text-slate-300";
        message.textContent = notification.message || "";

        const meta = document.createElement("div");
        meta.className = "inbox-notification-meta";
        meta.textContent = formatDate(notification.createdAt);

        row.appendChild(header);
        row.appendChild(message);

        const safeCtaUrl = notification.ctaUrl ? safeLink(notification.ctaUrl, true) : null;
        if (safeCtaUrl) {
            const cta = document.createElement("a");
            cta.href = safeCtaUrl;
            cta.className = "inline-flex min-h-11 w-fit items-center rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 shadow-sm hover:bg-slate-50 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-200";
            cta.textContent = copy("open");
            cta.addEventListener("click", async (e) => {
                e.stopPropagation();
                if (isUnread) {
                    await markNotificationRead(notification.id);
                }
            });
            row.appendChild(cta);
        }

        row.appendChild(meta);

        if (isUnread) {
            row.addEventListener("click", async () => {
                await markNotificationRead(notification.id);
                await fetchNotifications();
            });
            row.classList.add("inbox-notification-unread");
        }

        return row;
    }

    async function fetchThread(threadId) {
        try {
            const res = await fetch(`/api/inbox/threads/${threadId}`, { method: "GET" });
            if (!res.ok) return null;
            return await res.json();
        } catch {
            return null;
        }
    }

    function renderMessages(payload) {
        const messagesEl = document.getElementById("inboxMessages");
        const emptyEl = document.getElementById("inboxMessagesEmpty");
        if (!messagesEl || !emptyEl || !payload) return;

        const messages = payload.messages || [];
        if (!Array.isArray(messages)) { announce(copy("loadError")); return; }
        const followLatest = messagesEl.scrollHeight - messagesEl.scrollTop - messagesEl.clientHeight < 80;
        const signature = JSON.stringify(messages);
        if (signature === messagesSignature) return followLatest;
        messagesSignature = signature;
        const previousTop = messagesEl.scrollTop;
        if (!messages.length) {
            messagesEl.classList.add("hidden");
            emptyEl.classList.remove("hidden");
            return;
        }

        messagesEl.classList.remove("hidden");
        emptyEl.classList.add("hidden");
        messagesEl.replaceChildren();

        messages.forEach(msg => {
            const row = document.createElement("div");
            row.className = "flex";
            row.classList.add(payload.currentUserId === msg.senderUserId ? "inbox-message-own" : "inbox-message-other");

            const card = document.createElement("div");
            card.className = "max-w-2xl rounded-2xl border border-slate-200/70 bg-white px-4 py-3 text-sm shadow-sm dark:border-slate-800 dark:bg-slate-950/40";

            const meta = document.createElement("time");
            meta.className = "text-xs text-slate-500";
            meta.textContent = formatDate(msg.createdAt);
            meta.dateTime = msg.createdAt || "";
            meta.dir = "ltr";

            const body = document.createElement("p");
            body.className = "mt-1 whitespace-pre-wrap text-slate-800 dark:text-slate-200";
            body.textContent = msg.bodyText || "";
            body.dir = "auto";

            if (msg.type === "CHECKIN") {
                const label = document.createElement("p");
                label.className = "inbox-checkin-label";
                label.textContent = copy("checkin");
                card.appendChild(label);
            }
            card.appendChild(meta);
            card.appendChild(body);

            const safeAttachment = msg.attachmentUrl ? safeLink(msg.attachmentUrl) : null;
            if (safeAttachment) {
                const attachment = document.createElement("a");
                attachment.href = safeAttachment;
                attachment.target = "_blank";
                attachment.rel = "noopener noreferrer";
                attachment.className = "inbox-attachment";
                attachment.textContent = msg.attachmentName || copy("viewAttachment");
                card.appendChild(attachment);
            }

            if (payload.currentUserId === msg.senderUserId) {
                const receipt = document.createElement("p");
                receipt.className = "inbox-receipt";
                receipt.textContent = msg.readByOther ? copy("read") : copy("sent");
                card.appendChild(receipt);
            }

            row.appendChild(card);
            messagesEl.appendChild(row);
        });
        messagesEl.scrollTop = followLatest ? messagesEl.scrollHeight : previousTop;
        return followLatest;
    }

    async function markThreadRead(threadId, upToId) {
        try {
            const response = await fetch(`/api/inbox/threads/${threadId}/read?upToId=${upToId}`, { method: "POST", headers });
            if (!response.ok) throw new Error("read-failed");
            return true;
        } catch { announce(copy("readFailed")); return false; }
    }

    async function markNotificationRead(id) {
        if (!id) return;
        try {
            // A notification link may navigate immediately; retain its small read request across that navigation.
            const response = await fetch(`/api/notifications/${id}/read`, { method: "POST", headers, keepalive: true });
            if (!response.ok) throw new Error("notification-read");
            broadcastNotificationSync({ source: "inbox", notificationId: id, action: "read" });
        } catch { announce(copy("loadError")); }
    }

    async function sendMessage(threadId, bodyText, attachmentUrl) {
        const payload = {
            bodyText,
            attachmentUrl: attachmentUrl || null,
            attachmentName: attachmentUrl ? copy("viewAttachment") : null,
            attachmentType: attachmentUrl ? "link" : null
        };
        let res;
        try {
            res = await fetch(`/api/inbox/threads/${threadId}/send`, { method: "POST", headers, body: JSON.stringify(payload) });
        } catch { throw new Error("SEND_UNCONFIRMED"); }
        if (!res.ok) {
            let reason = "SEND_UNCONFIRMED";
            try { reason = (await res.json()).reason || reason; } catch { /* Retain an unconfirmed draft. */ }
            throw new Error(reason);
        }
        let acknowledgement;
        try { acknowledgement = await res.json(); } catch { throw new Error("SEND_UNCONFIRMED"); }
        if (res.redirected || !Number.isSafeInteger(acknowledgement?.id) || acknowledgement.id < 1) throw new Error("SEND_UNCONFIRMED");
    }

    if (listRoot) {
        fetchThreads();
        setInterval(() => { if (!document.hidden) fetchThreads(); }, 8000);
    }

    if (notificationList) {
        fetchNotifications();
    }

    if (notificationReadAll) {
        notificationReadAll.addEventListener("click", async () => {
            notificationReadAll.disabled = true;
            try {
                const response = await fetch("/api/notifications/read-all", { method: "POST", headers });
                if (!response.ok) throw new Error("notification-read-all");
                await fetchNotifications();
                broadcastNotificationSync({ source: "inbox", action: "read-all" });
            } catch { announce(copy("loadError")); }
            finally { notificationReadAll.disabled = !notificationList?.querySelector('.inbox-notification-unread'); }
        });
    }

    window.addEventListener("one-to-one:notifications-updated", async () => {
        if (notificationList) await fetchNotifications();
    });
    notificationSyncChannel?.addEventListener("message", async () => {
        if (notificationList) await fetchNotifications();
    });

    if (threadRoot) {
        const threadId = threadRoot.dataset.threadId;
        const sendForm = document.getElementById("inboxSendForm");
        const bodyInput = document.getElementById("inboxBody");
        const attachmentInput = document.getElementById("inboxAttachmentUrl");
        const sendButton = sendForm?.querySelector("button[type=submit]");
        let locked = threadRoot.dataset.threadStatus !== "OPEN";
        let unconfirmed = false;
        let lastReceivedId = 0, lastReadId = 0, readPending = false;
        const messagesEl = document.getElementById("inboxMessages");
        const checkinForm = document.querySelector('[data-inbox-checkin-form]');
        const checkinFields = [...document.querySelectorAll('[data-inbox-checkin-form] input:not([type=hidden]), [data-inbox-checkin-form] textarea')];
        const fields = [bodyInput, attachmentInput, ...checkinFields].filter(Boolean);
        const initial = fields.map(field => field.value);
        let textRejected = threadRoot.dataset.rejected === "true" && threadRoot.dataset.checkinRejected !== "true";
        const checkinRejected = threadRoot.dataset.checkinRejected === "true";
        const dirty = () => textRejected || checkinRejected || fields.some((field,index) => field.value !== initial[index]);
        const textDirty = () => textRejected || fields.slice(0,2).some((field,index) => field.value !== initial[index]);
        const destinations = [...document.querySelectorAll('a[href^="/inbox"], a[href="/dashboard"]')];
        let leaving = false;
        const updateDraftGuard = () => {
            destinations.forEach(link => dirty() ? link.setAttribute("data-confirm", copy("unsaved")) : link.removeAttribute("data-confirm"));
            if (checkinForm) textDirty() ? checkinForm.setAttribute("data-confirm", copy("unsaved")) : checkinForm.removeAttribute("data-confirm");
        };
        fields.forEach(field => field.addEventListener("input", updateDraftGuard));
        destinations.forEach(link => link.addEventListener("click", event => { if (!event.defaultPrevented) leaving = true; }));
        checkinForm?.addEventListener("submit", event => { if (!event.defaultPrevented) leaving = true; });
        window.addEventListener("beforeunload", event => { if (!leaving && dirty()) { event.preventDefault(); event.returnValue = ""; } });
        updateDraftGuard();
        const markDisplayedRead = async () => {
            if (document.hidden || readPending || lastReceivedId <= lastReadId || !messagesEl
                    || messagesEl.scrollHeight - messagesEl.scrollTop - messagesEl.clientHeight >= 80) return;
            readPending = true;
            const position = lastReceivedId;
            try { if (await markThreadRead(threadId, position)) lastReadId = position; }
            finally { readPending = false; }
        };
        messagesEl?.addEventListener("scroll", markDisplayedRead);

        function updateSendAvailability() {
            if (sendButton) sendButton.disabled = sending || locked || unconfirmed;
            if (bodyInput) bodyInput.readOnly = sending || locked;
            if (attachmentInput) attachmentInput.readOnly = sending || locked;
            const checkinFieldsGroup = checkinForm?.querySelector("fieldset");
            if (checkinFieldsGroup) checkinFieldsGroup.disabled = sending || locked;
            if (locked) announce(copy("locked"));
        }

        const refreshThread = async () => {
            if (threadRequestPending || sending || document.hidden) return;
            threadRequestPending = true;
            try {
                const payload = await fetchThread(threadId);
                if (!payload || !Array.isArray(payload.messages) || !["OPEN", "LOCKED"].includes(payload.status)) { announce(copy("loadError")); return false; }
                locked = payload.status !== "OPEN";
                updateSendAvailability();
                renderMessages(payload);
                const currentConversation = document.querySelector('aside a[aria-current="page"]');
                const latestMessage = payload.messages[payload.messages.length - 1];
                if (currentConversation && latestMessage) {
                    const preview = currentConversation.querySelector('[data-inbox-preview]');
                    const date = currentConversation.querySelector('[data-inbox-preview-date]');
                    if (preview) preview.textContent = latestMessage.bodyText || "";
                    if (date) date.textContent = formatDate(latestMessage.createdAt);
                }
                lastReceivedId = payload.messages.reduce((latest, message) => Number.isSafeInteger(message.id) ? Math.max(latest, message.id) : latest, lastReceivedId);
                await markDisplayedRead();
                return true;
            } finally { threadRequestPending = false; }
        };

        refreshThread();
        updateSendAvailability();
        setInterval(refreshThread, 5000);

        sendForm?.addEventListener("submit", async (event) => {
            if (!threadId || !bodyInput) return;
            event.preventDefault();
            if (sending || locked || unconfirmed) return;
            const attachmentUrl = attachmentInput?.value?.trim() || "";
            const bodyText = bodyInput.value.trim();
            if (!bodyText && !attachmentUrl) { announce(copy("sendError")); bodyInput.focus(); return; }
            sending = true;
            updateSendAvailability();
            try {
                await sendMessage(threadId, bodyText, attachmentUrl);
                bodyInput.value = "";
                if (attachmentInput) attachmentInput.value = "";
                textRejected = false;
                fields.slice(0,2).forEach((field,index) => { initial[index] = field.value; });
                updateDraftGuard();
                announce(copy("sent"));
            } catch (error) {
                if (["THREAD_LOCKED", "THREAD_NOT_ACTIVE"].includes(error.message)) locked = true;
                unconfirmed = error.message === "SEND_UNCONFIRMED";
                announce(unconfirmed ? copy("unconfirmed") : error.message === "OFF_PLATFORM_PAYMENT" ? copy("paymentError") : (locked ? copy("locked") : copy("sendError")));
            } finally {
                sending = false;
                updateSendAvailability();
            }
            refreshThread();
        });
        document.getElementById("inboxReload")?.addEventListener("click", async () => {
            if (await refreshThread()) { unconfirmed = false; updateSendAvailability(); }
        });
    } else {
        document.getElementById("inboxReload")?.addEventListener("click", () => { fetchThreads(); fetchNotifications(); });
    }
});
