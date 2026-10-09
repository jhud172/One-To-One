const chatPanelConcealments = new WeakMap();

function cancelChatPanelConceal(panel) {
    const concealment = chatPanelConcealments.get(panel);
    if (!concealment) return;
    window.clearTimeout(concealment.timer);
    panel.removeEventListener("transitionend", concealment.onTransitionEnd);
    chatPanelConcealments.delete(panel);
}

function revealChatPanel(panel) {
    cancelChatPanelConceal(panel);
    panel.hidden = false;
    // Commit the closed visual state before the existing opening transition.
    panel.getBoundingClientRect();
}

function concealChatPanelAfterTransition(panel) {
    cancelChatPanelConceal(panel);

    const complete = () => {
        cancelChatPanelConceal(panel);
        if (!panel.classList.contains("open")) {
            panel.hidden = true;
        }
    };
    const onTransitionEnd = (event) => {
        if (event.target === panel && event.propertyName === "transform") {
            complete();
        }
    };
    const timer = window.setTimeout(complete, 600);

    chatPanelConcealments.set(panel, { timer, onTransitionEnd });
    panel.addEventListener("transitionend", onTransitionEnd);
}

// Global fallback toggle function (in case DOMContentLoaded hasn't fired yet)
window.toggleChatPanel = function() {
    const panel = document.getElementById("chatPanel");
    const fab = document.getElementById("chatFab");
    if (!panel || !fab) return;
    
    const isOpen = panel.classList.contains("open");
    if (isOpen) {
        panel.classList.remove("open");
        panel.setAttribute("aria-hidden", "true");
        panel.setAttribute("inert", "");
        concealChatPanelAfterTransition(panel);
        fab.setAttribute("aria-expanded", "false");
        fab.setAttribute("aria-label", getChatCopy("openCharlie", "Open Charlie"));
    } else {
        revealChatPanel(panel);
        panel.classList.add("open");
        panel.setAttribute("aria-hidden", "false");
        panel.removeAttribute("inert");
        fab.setAttribute("aria-expanded", "true");
        fab.setAttribute("aria-label", getChatCopy("closeCharlie", "Close Charlie"));
    }
    console.log("Chat panel toggled via fallback:", !isOpen ? "open" : "closed");
};

function getChatCopy(key, fallback, ...values) {
    const source = document.querySelector(`#chatLocalisation [data-chat-copy="${key}"]`)?.textContent?.trim() || fallback;
    return values.reduce((message, value, index) => message.replaceAll(`{${index}}`, value), source);
}

function initCharlieWidget(config) {
    const {
        root, fab, panel, panelContent, closeBtn, clearBtn, form, input, body, sendBtn,
        dot, normalTabBtn, notificationsToggle2, notificationsView, notifFilterAll, notifFilterUnread,
        notificationsUnreadCount, notificationsList, notificationsReadAll, unreadBadge,
        inlineToast, proChatBtn, chatPlusAccessNotice, chatAttachImageBtn, chatComposerTools, chatComposerOptions, chatUseCameraBtn,
        chatUsePhotosBtn, chatCameraInput, chatPhotoInput, chatAttachmentPreviewTray,
        chatAttachmentCount, chatCharacterCount, clearInlineConfirm, clearInlineCancel, clearInlineConfirmBtn,
        chatMediaLightbox, chatMediaLightboxBackdrop, chatMediaLightboxClose, chatMediaLightboxImage,
        isAuthenticated, isPremium, accountRole, initialWelcomeText, csrfToken, csrfHeader,
        STORAGE_KEY, LEGACY_STORAGE_KEY, MAX_ATTACHMENTS, MAX_COMPOSER_HEIGHT, MAX_MESSAGE_LENGTH
    } = config;

    const state = {
        view: "chat",
        filter: "all",
        sending: false,
        pendingAttachments: [],
        history: []
    };
    const overlayManager = window.OneToOneOverlay;
    const canUseInbox = Boolean(isAuthenticated && notificationsToggle2 && notificationsView && notificationsList);
    const composerTooltipLabel = chatAttachImageBtn?.dataset?.tooltip || "";
    let lightboxReturnFocus = null;
    let clearConfirmReturnFocus = null;

    const setChatPlusAccessOpen = (open, options = {}) => {
        if (!chatPlusAccessNotice || !proChatBtn) return;
        const nextOpen = Boolean(open);
        chatPlusAccessNotice.classList.toggle("is-open", nextOpen);
        chatPlusAccessNotice.setAttribute("aria-hidden", String(!nextOpen));
        chatPlusAccessNotice.toggleAttribute("inert", !nextOpen);
        proChatBtn.setAttribute("aria-expanded", String(nextOpen));
        if (!nextOpen && options.restoreFocus) proChatBtn.focus();
    };

    const headers = (json = true) => {
        const next = {};
        if (json) next["Content-Type"] = "application/json";
        if (csrfToken) next[csrfHeader] = csrfToken;
        return next;
    };

    const createId = (prefix) => `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const getWelcome = () => {
        if (initialWelcomeText) return initialWelcomeText;
        if (!isAuthenticated) return "Hi, I am Charlie, the One To One website assistant. I can explain platform features and guide you to the right pages.";
        const roleLabel = accountRole === "TRAINER" ? "trainer" : accountRole === "GYM_ADMIN" ? "gym" : "client";
        return isPremium
            ? `Welcome back. I am Charlie, your premium ${roleLabel} assistant.`
            : "Welcome back. I am Charlie. You have 15 prompts per day on Starter access.";
    };

    const safeParse = (value) => {
        try {
            return JSON.parse(value);
        } catch (_) {
            return null;
        }
    };

    const normalizeAttachment = (raw) => {
        if (!raw) return null;
        const url = raw.url || raw.previewUrl || raw.dataUrl || "";
        if (!url) return null;
        return {
            id: raw.id || createId("attachment"),
            url,
            fileName: raw.fileName || "image",
            contentType: raw.contentType || "image/jpeg"
        };
    };

    const normalizeMessage = (raw) => {
        if (!raw) return null;
        return {
            id: raw.id || createId("message"),
            who: raw.who === "me" ? "me" : "ai",
            text: typeof raw.text === "string" ? raw.text : "",
            attachments: Array.isArray(raw.attachments) ? raw.attachments.map(normalizeAttachment).filter(Boolean).slice(0, MAX_ATTACHMENTS) : [],
            navActions: Array.isArray(raw.navActions) ? raw.navActions : []
        };
    };

    const renderRichText = (text) => {
        const fragment = document.createDocumentFragment();
        const parts = String(text || "").trim().split(/(https?:\/\/[^\s]+)/gi);
        parts.forEach((part) => {
            if (!part) return;
            if (/^https?:\/\//i.test(part)) {
                const link = document.createElement("a");
                link.href = part;
                link.target = "_blank";
                link.rel = "noopener noreferrer";
                link.textContent = part;
                fragment.appendChild(link);
            } else {
                fragment.appendChild(document.createTextNode(part));
            }
        });
        return fragment;
    };

    const openLightbox = (url, label) => {
        if (!chatMediaLightbox || !chatMediaLightboxImage) return;
        lightboxReturnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
        overlayManager?.open("charlie-media", { group: "modal" });
        chatMediaLightboxImage.src = url;
        chatMediaLightboxImage.alt = label || getChatCopy("expandedAttachment", "Expanded chat attachment");
        chatMediaLightbox.classList.add("is-open");
        chatMediaLightbox.setAttribute("aria-hidden", "false");
        chatMediaLightbox.removeAttribute("inert");
        panel.setAttribute("inert", "");
        panel.dataset.chatLightboxInert = "true";
        window.requestAnimationFrame(() => chatMediaLightboxClose?.focus());
    };

    const closeLightbox = (options = {}) => {
        if (!chatMediaLightbox || !chatMediaLightboxImage) return;
        chatMediaLightbox.classList.remove("is-open");
        chatMediaLightbox.setAttribute("aria-hidden", "true");
        chatMediaLightbox.setAttribute("inert", "");
        chatMediaLightboxImage.src = "";
        if (panel.dataset.chatLightboxInert === "true") {
            if (panel.classList.contains("open")) panel.removeAttribute("inert");
            delete panel.dataset.chatLightboxInert;
        }
        if (!options.fromOverlayManager) overlayManager?.release("charlie-media", { group: "modal" });
        if (options.restoreFocus !== false && lightboxReturnFocus?.isConnected) lightboxReturnFocus.focus();
        lightboxReturnFocus = null;
    };

    const buildGallery = (attachments, pending = false) => {
        if (!attachments?.length) return null;
        const gallery = document.createElement("div");
        gallery.className = pending ? "chat-pending-gallery" : "chat-attachment-gallery";
        attachments.forEach((attachment) => {
            const card = document.createElement(pending ? "div" : "button");
            card.className = pending ? "chat-pending-attachment-card" : "chat-attachment-card";
            if (!pending) {
                card.type = "button";
                card.addEventListener("click", () => openLightbox(attachment.url, attachment.fileName));
            }
            const image = document.createElement("img");
            image.src = attachment.url;
            image.alt = attachment.fileName || getChatCopy("attachedImage", "Attached image");
            image.className = pending ? "chat-pending-attachment-image" : "chat-attachment-image";
            card.appendChild(image);
            const meta = document.createElement("div");
            meta.className = pending ? "chat-pending-attachment-meta" : "chat-attachment-meta";
            const label = document.createElement("span");
            label.className = "chat-attachment-name";
            label.textContent = attachment.fileName || getChatCopy("image", "image");
            meta.appendChild(label);
            if (pending) {
                const remove = document.createElement("button");
                remove.type = "button";
                remove.className = "chat-pending-attachment-remove";
                remove.textContent = "X";
                remove.setAttribute("aria-label", getChatCopy("removeAttachment", "Remove attachment"));
                remove.addEventListener("click", () => {
                    state.pendingAttachments = state.pendingAttachments.filter((item) => item.id !== attachment.id);
                    renderPendingAttachments();
                    updateSendState();
                });
                card.appendChild(remove);
            } else {
                const expand = document.createElement("span");
                expand.className = "chat-attachment-expand";
                expand.textContent = getChatCopy("view", "View");
                meta.appendChild(expand);
            }
            card.appendChild(meta);
            gallery.appendChild(card);
        });
        return gallery;
    };

    const buildMessageElement = (message) => {
        const wrap = document.createElement("div");
        wrap.className = `chat-message-wrapper ${message.who === "me" ? "user" : "assistant"}`;
        const bubble = document.createElement("div");
        bubble.className = "chat-message-bubble";
        bubble.appendChild(renderRichText(message.text));
        wrap.appendChild(bubble);
        const gallery = buildGallery(message.attachments, false);
        if (gallery) wrap.appendChild(gallery);
        if (Array.isArray(message.navActions) && message.navActions.length) {
            const nav = document.createElement("div");
            nav.className = "chat-nav-actions";
            message.navActions.forEach((action) => {
                if (!action?.url || !String(action.url).startsWith("/")) return;
                const link = document.createElement("a");
                link.href = action.url;
                link.className = "chat-nav-btn";
                link.textContent = action.label || getChatCopy("open", "Open");
                nav.appendChild(link);
            });
            if (nav.children.length) wrap.appendChild(nav);
        }
        return wrap;
    };

    const persistHistory = () => localStorage.setItem(STORAGE_KEY, JSON.stringify(state.history.slice(-120)));
    const readHistory = () => {
        const next = safeParse(localStorage.getItem(STORAGE_KEY));
        if (Array.isArray(next)) return next.map(normalizeMessage).filter(Boolean);
        const legacy = safeParse(localStorage.getItem(LEGACY_STORAGE_KEY));
        return Array.isArray(legacy) ? legacy.map(normalizeMessage).filter(Boolean) : [];
    };

    const renderHistory = (messages) => {
        body.innerHTML = "";
        state.history = Array.isArray(messages) ? messages.map(normalizeMessage).filter(Boolean) : [];
        if (!state.history.length) {
            const welcome = normalizeMessage({ who: "ai", text: getWelcome(), attachments: [], navActions: [] });
            state.history = [welcome];
            persistHistory();
        }
        state.history.forEach((message) => body.appendChild(buildMessageElement(message)));
        body.scrollTop = body.scrollHeight;
    };

    const appendMessage = (message, persist = true) => {
        const normalized = normalizeMessage(message);
        if (!normalized) return;
        state.history.push(normalized);
        state.history = state.history.slice(-120);
        body.appendChild(buildMessageElement(normalized));
        body.scrollTop = body.scrollHeight;
        if (persist) persistHistory();
    };

    const syncHeaderState = () => {
        const showingInbox = state.view === "inbox";
        if (clearBtn) {
            clearBtn.hidden = showingInbox;
            clearBtn.disabled = showingInbox;
            clearBtn.setAttribute("aria-hidden", showingInbox ? "true" : "false");
            clearBtn.tabIndex = showingInbox ? -1 : 0;
        }
    };

    const setActiveView = (view) => {
        state.view = canUseInbox && view === "inbox" ? "inbox" : "chat";
        panelContent.dataset.view = state.view;
        normalTabBtn?.classList.toggle("active", state.view === "chat");
        notificationsToggle2?.classList.toggle("active", state.view === "inbox");
        normalTabBtn?.setAttribute("aria-pressed", state.view === "chat" ? "true" : "false");
        notificationsToggle2?.setAttribute("aria-pressed", state.view === "inbox" ? "true" : "false");
        if (state.view === "inbox") {
            closeComposerOptions();
            closeInlineClearConfirm({ restoreFocus: false });
        }
        syncHeaderState();
    };

    const setComposerOptionsOpen = (isOpen) => {
        chatComposerOptions?.classList.toggle("hidden", !isOpen);
        chatComposerOptions?.setAttribute("aria-hidden", isOpen ? "false" : "true");
        chatAttachImageBtn?.setAttribute("aria-expanded", isOpen ? "true" : "false");
        if (chatAttachImageBtn) {
            if (isOpen || window.matchMedia("(max-width: 640px)").matches) {
                chatAttachImageBtn.removeAttribute("data-tooltip");
            } else if (composerTooltipLabel) {
                chatAttachImageBtn.dataset.tooltip = composerTooltipLabel;
            }
        }
        panelContent.dataset.composerOpen = isOpen ? "true" : "false";
    };

    const closeComposerOptions = () => {
        setComposerOptionsOpen(false);
    };

    const toggleComposerOptions = () => {
        if (!chatComposerOptions || input.disabled || state.view !== "chat") return;
        const isOpen = !chatComposerOptions.classList.contains("hidden");
        setComposerOptionsOpen(isOpen ? false : true);
    };

    const openInlineClearConfirm = () => {
        if (!clearInlineConfirm) return;
        clearConfirmReturnFocus = document.activeElement instanceof HTMLElement
            ? document.activeElement
            : clearBtn;
        panelContent.dataset.clearConfirm = "true";
        clearInlineConfirm.classList.add("is-open");
        clearInlineConfirm.setAttribute("aria-hidden", "false");
        clearInlineConfirm.removeAttribute("inert");
        clearBtn?.setAttribute("aria-expanded", "true");
        window.requestAnimationFrame(() => clearInlineCancel?.focus());
    };

    const closeInlineClearConfirm = (options = {}) => {
        const wasOpen = clearInlineConfirm?.classList.contains("is-open");
        panelContent.dataset.clearConfirm = "false";
        clearInlineConfirm?.classList.remove("is-open");
        clearInlineConfirm?.setAttribute("aria-hidden", "true");
        clearInlineConfirm?.setAttribute("inert", "");
        clearBtn?.setAttribute("aria-expanded", "false");
        if (options.restoreFocus !== false && wasOpen && clearConfirmReturnFocus?.isConnected) {
            clearConfirmReturnFocus.focus();
        }
        clearConfirmReturnFocus = null;
    };

    const autoResizeInput = () => {
        input.style.height = "auto";
        input.style.height = `${Math.min(input.scrollHeight, MAX_COMPOSER_HEIGHT)}px`;
        input.style.overflowY = input.scrollHeight > MAX_COMPOSER_HEIGHT ? "auto" : "hidden";
    };

    const updateCharacterCount = () => {
        if (!chatCharacterCount) return input.value.length <= MAX_MESSAGE_LENGTH;
        const length = input.value.length;
        const remaining = MAX_MESSAGE_LENGTH - length;
        const isOver = remaining < 0;
        const isAtLimit = remaining === 0;
        const isNearLimit = !isAtLimit && !isOver && length >= Math.floor(MAX_MESSAGE_LENGTH * 0.8);

        chatCharacterCount.textContent = `${length.toLocaleString()} / ${MAX_MESSAGE_LENGTH.toLocaleString()}`;
        chatCharacterCount.classList.toggle("is-near-limit", isNearLimit);
        chatCharacterCount.classList.toggle("is-at-limit", isAtLimit);
        chatCharacterCount.classList.toggle("is-over-limit", isOver);
        chatCharacterCount.setAttribute("title", isOver
            ? getChatCopy("charactersOver", "{0} characters over the limit", Math.abs(remaining).toLocaleString())
            : getChatCopy("charactersRemaining", "{0} characters remaining", remaining.toLocaleString()));
        input.setAttribute("aria-invalid", isOver ? "true" : "false");
        return !isOver;
    };

    const updateSendState = () => {
        const hasText = input.value.trim().length > 0;
        const isWithinLimit = updateCharacterCount();
        sendBtn.disabled = state.sending || !isWithinLimit || (!hasText && !state.pendingAttachments.length);
    };

    let panelReturnFocus = fab;
    const openPanel = (trigger = document.activeElement) => {
        panelReturnFocus = trigger instanceof HTMLElement ? trigger : fab;
        overlayManager?.open("charlie");
        revealChatPanel(panel);
        panel.classList.add("open");
        panel.setAttribute("aria-hidden", "false");
        panel.removeAttribute("inert");
        fab.setAttribute("aria-expanded", "true");
        document.querySelector('#platformPanelTrack [data-action="charlie"]')?.setAttribute("aria-expanded", "true");
        fab.setAttribute("aria-label", getChatCopy("closeCharlie", "Close Charlie"));
        syncHeaderState();
        window.requestAnimationFrame(() => { if (panel.classList.contains("open")) input.focus(); });
    };

    const closePanel = (options = {}) => {
        const focusWasInsidePanel = panel.contains(document.activeElement);
        if (chatMediaLightbox?.classList.contains("is-open")) {
            closeLightbox({ restoreFocus: false });
        }
        panel.classList.remove("open");
        panel.setAttribute("aria-hidden", "true");
        panel.setAttribute("inert", "");
        concealChatPanelAfterTransition(panel);
        fab.setAttribute("aria-expanded", "false");
        document.querySelector('#platformPanelTrack [data-action="charlie"]')?.setAttribute("aria-expanded", "false");
        fab.setAttribute("aria-label", getChatCopy("openCharlie", "Open Charlie"));
        closeComposerOptions();
        setChatPlusAccessOpen(false);
        closeInlineClearConfirm({ restoreFocus: false });
        if (!options.fromOverlayManager) overlayManager?.release("charlie");
        if (options.restoreFocus && focusWasInsidePanel) {
            const target = panelReturnFocus?.isConnected ? panelReturnFocus : fab;
            target.focus();
        }
    };

    overlayManager?.register("charlie", {
        close: (options) => closePanel({ ...options, fromOverlayManager: true })
    });
    overlayManager?.register("charlie-media", {
        group: "modal",
        close: (options) => closeLightbox({ ...options, fromOverlayManager: true })
    });

    const renderPendingAttachments = () => {
        if (!chatAttachmentPreviewTray) return;
        chatAttachmentPreviewTray.innerHTML = "";
        const gallery = buildGallery(state.pendingAttachments, true);
        if (gallery) {
            chatAttachmentPreviewTray.appendChild(gallery);
            chatAttachmentPreviewTray.classList.remove("hidden");
        } else {
            chatAttachmentPreviewTray.classList.add("hidden");
        }
        if (chatAttachmentCount) {
            chatAttachmentCount.textContent = state.pendingAttachments.length
                ? getChatCopy("photosCount", "{0} / {1} photos", state.pendingAttachments.length, MAX_ATTACHMENTS)
                : "";
            chatAttachmentCount.classList.toggle("hidden", !state.pendingAttachments.length);
        }
        autoResizeInput();
    };

    const readFileAsDataUrl = (file) => new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result);
        reader.onerror = () => reject(reader.error);
        reader.readAsDataURL(file);
    });

    const createPreviewDataUrl = async (file) => {
        try {
            const objectUrl = URL.createObjectURL(file);
            const image = await new Promise((resolve, reject) => {
                const img = new Image();
                img.onload = () => resolve(img);
                img.onerror = reject;
                img.src = objectUrl;
            });
            const scale = Math.min(1, 1440 / Math.max(image.naturalWidth || 1, image.naturalHeight || 1));
            const width = Math.max(1, Math.round((image.naturalWidth || 1) * scale));
            const height = Math.max(1, Math.round((image.naturalHeight || 1) * scale));
            const canvas = document.createElement("canvas");
            canvas.width = width;
            canvas.height = height;
            canvas.getContext("2d").drawImage(image, 0, 0, width, height);
            URL.revokeObjectURL(objectUrl);
            return canvas.toDataURL(file.type === "image/png" ? "image/png" : "image/jpeg", file.type === "image/png" ? undefined : 0.82);
        } catch (_) {
            return readFileAsDataUrl(file);
        }
    };

    const prepareAttachment = async (file) => ({
        id: createId("pending"),
        file,
        fileName: file.name || "image",
        contentType: file.type || "image/jpeg",
        url: await createPreviewDataUrl(file)
    });

    const showInlineMessage = (message, tone = "default") => {
        if (!inlineToast) return;
        inlineToast.textContent = message;
        inlineToast.classList.toggle("chat-inline-toast-warning", tone === "warning");
        inlineToast.classList.toggle("chat-inline-toast-success", tone === "success");
        inlineToast.classList.remove("hidden");
        clearTimeout(showInlineMessage.timerId);
        showInlineMessage.timerId = setTimeout(() => inlineToast.classList.add("hidden"), 2800);
    };

    const setUnread = (count) => {
        const hasUnread = count > 0;
        if (unreadBadge) {
            unreadBadge.textContent = hasUnread ? (count > 99 ? "99+" : `${count}`) : "";
            unreadBadge.classList.toggle("hidden", !hasUnread);
            unreadBadge.classList.toggle("inline-flex", hasUnread);
        }
        if (notificationsUnreadCount) {
            notificationsUnreadCount.textContent = hasUnread ? (count > 99 ? "99+" : `${count}`) : "";
            notificationsUnreadCount.classList.toggle("hidden", !hasUnread);
            notificationsUnreadCount.classList.toggle("inline-flex", hasUnread);
        }
        document.querySelectorAll(".nav-notification-badge").forEach((badge) => {
            badge.textContent = hasUnread ? (count > 99 ? "99+" : `${count}`) : "";
            badge.classList.toggle("hidden", !hasUnread);
            badge.classList.toggle("inline-flex", hasUnread);
        });
    };

    const refreshUnread = async () => {
        if (!isAuthenticated) return;
        try {
            const response = await fetch("/api/notifications/unread-count");
            if (!response.ok) return;
            const data = await response.json();
            setUnread(Number(data?.count || 0));
        } catch (_) {
            // ignore
        }
    };

    const renderGuestInbox = () => {
        if (!notificationsList) return;
        notificationsList.innerHTML = "";
        const card = document.createElement("div");
        card.className = "chat-empty-state-card";
        const icon = document.createElement("div");
        icon.className = "chat-empty-state-icon";
        icon.textContent = getChatCopy("inbox", "Inbox");
        const title = document.createElement("p");
        title.className = "chat-empty-state-title";
        title.textContent = getChatCopy("loginInbox", "Log in to use your inbox");
        const copy = document.createElement("p");
        copy.className = "chat-empty-state-text";
        copy.textContent = getChatCopy("guestInbox", "Charlie chat is available on the website, but notifications stay attached to signed-in accounts.");
        card.append(icon, title, copy);
        notificationsList.appendChild(card);
    };

    const loadNotifications = async () => {
        if (!notificationsList) return;
        if (!canUseInbox) {
            renderGuestInbox();
            return;
        }
        try {
            const response = await fetch("/api/notifications?limit=20");
            if (!response.ok) return;
            let items = await response.json();
            notificationsList.innerHTML = "";
            if (!Array.isArray(items)) return;
            if (state.filter === "unread") {
                items = items.filter((item) => !item.readAt && !item.dismissedAt);
            }
            if (!items.length) {
                const empty = document.createElement("div");
                empty.className = "rounded-xl border border-dashed border-slate-200 bg-slate-50 px-3 py-6 text-center text-xs text-slate-500 dark:border-slate-800 dark:bg-slate-900/40 dark:text-slate-400";
                empty.textContent = state.filter === "unread"
                    ? getChatCopy("noUnread", "No unread notifications.")
                    : getChatCopy("noNotifications", "No notifications yet.");
                notificationsList.appendChild(empty);
                return;
            }
            items.forEach((item) => {
                const row = document.createElement("div");
                row.className = `group rounded-xl border p-3 shadow-sm transition-all ${!item.readAt && !item.dismissedAt ? "border-emerald-500/30 bg-emerald-500/5 dark:bg-emerald-500/10" : "border-slate-200 bg-slate-50/60 dark:border-slate-800 dark:bg-slate-900/60"}`;
                row.innerHTML = `<div class="flex items-start justify-between gap-2"><div class="text-xs font-semibold text-slate-600 dark:text-slate-300"></div><button type="button" class="text-[11px] font-semibold text-slate-400"></button></div><div class="mt-1.5 text-sm leading-snug text-slate-700 dark:text-slate-200"></div>`;
                row.querySelector(".text-xs").textContent = item.title || getChatCopy("notification", "Notification");
                row.querySelector("button").textContent = getChatCopy("dismiss", "Dismiss");
                row.querySelector(".mt-1\\.5").textContent = item.message || "";
                row.querySelector("button")?.addEventListener("click", async (event) => {
                    event.stopPropagation();
                    await fetch(`/api/notifications/${item.id}/dismiss`, { method: "POST", headers: headers(false) });
                    await loadNotifications();
                    await refreshUnread();
                });
                notificationsList.appendChild(row);
            });
        } catch (_) {
            // ignore
        }
    };

    const handleFilesSelected = async (files) => {
        const candidates = Array.from(files || []).filter((file) => file?.type?.startsWith("image/"));
        const availableSlots = MAX_ATTACHMENTS - state.pendingAttachments.length;
        if (availableSlots <= 0) {
            showInlineMessage(getChatCopy("photoLimit", "You can attach up to 5 photos per message."), "warning");
            return;
        }
        const selected = candidates.slice(0, availableSlots);
        if (candidates.length > availableSlots) {
            showInlineMessage(getChatCopy("photosKept", "Only the first 5 photos were kept for this message."), "warning");
        }
        for (const file of selected) {
            state.pendingAttachments.push(await prepareAttachment(file));
        }
        renderPendingAttachments();
        updateSendState();
        closeComposerOptions();
    };

    const uploadAttachments = async (queuedAttachments) => {
        if (!queuedAttachments.length || !isAuthenticated) return [];
        const formData = new FormData();
        queuedAttachments.forEach((attachment) => attachment.file && formData.append("files", attachment.file));
        const nextHeaders = {};
        if (csrfToken) nextHeaders[csrfHeader] = csrfToken;
        const response = await fetch("/chat/attachments", { method: "POST", headers: nextHeaders, body: formData });
        const data = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(data?.error || getChatCopy("uploadUnavailable", "Unable to upload chat images right now."));
        return Array.isArray(data?.attachments) ? data.attachments.map(normalizeAttachment).filter(Boolean) : [];
    };

    const fetchHistory = async () => {
        if (!isAuthenticated) return [];
        try {
            const response = await fetch("/chat/history");
            if (!response.ok) return [];
            const data = await response.json();
            return Array.isArray(data) ? data.map(normalizeMessage).filter(Boolean) : [];
        } catch (_) {
            return [];
        }
    };

    const resetComposer = () => {
        input.value = "";
        input.style.height = "auto";
        chatCameraInput && (chatCameraInput.value = "");
        chatPhotoInput && (chatPhotoInput.value = "");
        state.pendingAttachments = [];
        renderPendingAttachments();
        updateSendState();
    };

    const submitMessage = async (event) => {
        event.preventDefault();
        if (state.sending) return;
        if (input.value.length > MAX_MESSAGE_LENGTH) {
            const excess = input.value.length - MAX_MESSAGE_LENGTH;
            showInlineMessage(getChatCopy("messageOverLimit", "Your message is {0} characters over the {1} character limit.", excess.toLocaleString(), MAX_MESSAGE_LENGTH.toLocaleString()), "warning");
            updateSendState();
            input.focus();
            return;
        }
        const text = input.value.trim();
        const queuedAttachments = state.pendingAttachments.slice();
        if (!text && !queuedAttachments.length) {
            showInlineMessage(getChatCopy("addBeforeSending", "Add a message or photo before sending."), "warning");
            updateSendState();
            return;
        }
        appendMessage({ who: "me", text, attachments: queuedAttachments, navActions: [] });
        state.sending = true;
        updateSendState();
        resetComposer();
        try {
            const attachments = await uploadAttachments(queuedAttachments);
            const response = await fetch(isAuthenticated ? "/chat/api" : "/chat/ask", {
                method: "POST",
                headers: headers(true),
                body: JSON.stringify({
                    message: text,
                    attachments: isAuthenticated
                        ? attachments
                        : queuedAttachments.map((attachment) => ({ fileName: attachment.fileName, contentType: attachment.contentType }))
                })
            });
            const data = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(data?.reply || getChatCopy("charlieUnavailable", "Charlie is unavailable right now."));
            appendMessage({ who: "ai", text: data?.reply || getChatCopy("noResponse", "No response received."), attachments: [], navActions: Array.isArray(data?.navActions) ? data.navActions : [] });
            dot?.classList.add("active");
        } catch (error) {
            appendMessage({ who: "ai", text: error?.message || getChatCopy("charlieUnavailable", "Charlie is unavailable right now."), attachments: [], navActions: [] });
        } finally {
            state.sending = false;
            updateSendState();
        }
    };

    window.toggleChatPanel = trigger => (panel.classList.contains("open") ? closePanel() : openPanel(trigger));
    window.openCharlieChatWithMessage = (message) => {
        if (!message || typeof message !== "string") return;
        openPanel();
        setActiveView("chat");
        appendMessage({ who: "ai", text: message.trim(), attachments: [], navActions: [] });
    };

    fab.addEventListener("click", (event) => { event.preventDefault(); window.toggleChatPanel(fab); });
    closeBtn.addEventListener("click", () => closePanel({ restoreFocus: true }));
    clearBtn.addEventListener("click", openInlineClearConfirm);
    clearInlineCancel?.addEventListener("click", () => closeInlineClearConfirm({ restoreFocus: true }));
    clearInlineConfirmBtn?.addEventListener("click", async () => {
        closeInlineClearConfirm();
        localStorage.removeItem(STORAGE_KEY);
        localStorage.removeItem(LEGACY_STORAGE_KEY);
        renderHistory([]);
        showInlineMessage(getChatCopy("historyCleared", "Chat history cleared."), "success");
        if (isAuthenticated) await fetch("/chat/clear", { method: "POST", headers: headers(false) }).catch(() => undefined);
    });
    normalTabBtn?.addEventListener("click", () => {
        setChatPlusAccessOpen(false);
        openPanel();
        setActiveView("chat");
    });
    notificationsToggle2?.addEventListener("click", async () => {
        setChatPlusAccessOpen(false);
        openPanel();
        setActiveView(state.view === "inbox" ? "chat" : "inbox");
        if (state.view === "inbox") await loadNotifications();
    });
    proChatBtn?.addEventListener("click", (event) => {
        const locked = proChatBtn.getAttribute("data-locked") === "true" || !isPremium;
        if (!locked) {
            window.location.href = "/chat";
            return;
        }

        event.preventDefault();
        const isOpen = proChatBtn.getAttribute("aria-expanded") === "true";
        setChatPlusAccessOpen(!isOpen);
    });
    chatAttachImageBtn?.addEventListener("click", (event) => {
        event.preventDefault();
        toggleComposerOptions();
    });
    chatUseCameraBtn?.addEventListener("click", () => {
        closeComposerOptions();
        chatCameraInput?.click();
    });
    chatUsePhotosBtn?.addEventListener("click", () => {
        closeComposerOptions();
        chatPhotoInput?.click();
    });
    const handleFileInputChange = async (event) => {
        const fileInput = event.currentTarget;
        try {
            await handleFilesSelected(fileInput.files);
        } finally {
            // Allow choosing the same image again after it has been removed.
            fileInput.value = "";
        }
    };
    chatCameraInput?.addEventListener("change", handleFileInputChange);
    chatPhotoInput?.addEventListener("change", handleFileInputChange);
    chatMediaLightboxBackdrop?.addEventListener("click", closeLightbox);
    chatMediaLightboxClose?.addEventListener("click", closeLightbox);
    notifFilterAll?.addEventListener("click", async () => { state.filter = "all"; notifFilterAll.classList.add("active"); notifFilterUnread?.classList.remove("active"); await loadNotifications(); });
    notifFilterUnread?.addEventListener("click", async () => { state.filter = "unread"; notifFilterUnread.classList.add("active"); notifFilterAll?.classList.remove("active"); await loadNotifications(); });
    notificationsReadAll?.addEventListener("click", async () => { if (!isAuthenticated) return; await fetch("/api/notifications/read-all", { method: "POST", headers: headers(false) }); await loadNotifications(); await refreshUnread(); });
    form.addEventListener("submit", submitMessage);
    input.addEventListener("input", () => { autoResizeInput(); updateSendState(); });
    input.addEventListener("keydown", (event) => { if (event.key === "Enter" && !event.shiftKey) { event.preventDefault(); form.requestSubmit(); } });
    document.addEventListener("click", (event) => {
        if (!chatComposerTools?.contains(event.target)) closeComposerOptions();
        if (chatPlusAccessNotice?.classList.contains("is-open")
            && !chatPlusAccessNotice.contains(event.target)
            && !proChatBtn?.contains(event.target)) {
            setChatPlusAccessOpen(false);
        }
    });
    document.addEventListener("keydown", (event) => {
        if (event.key !== "Escape") return;
        if (chatMediaLightbox?.classList.contains("is-open")) closeLightbox();
        else if (clearInlineConfirm?.classList.contains("is-open")) closeInlineClearConfirm({ restoreFocus: true });
        else if (chatPlusAccessNotice?.classList.contains("is-open")) setChatPlusAccessOpen(false, { restoreFocus: true });
        else if (panel.classList.contains("open")) closePanel({ restoreFocus: true });
    });

    const panelStartsOpen = panel.classList.contains("open");
    panel.toggleAttribute("inert", !panelStartsOpen);
    panel.hidden = !panelStartsOpen;
    if (panelStartsOpen) overlayManager?.open("charlie");
    else overlayManager?.release("charlie");
    renderHistory(isAuthenticated ? readHistory() : []);
    setActiveView("chat");
    setChatPlusAccessOpen(false);
    closeComposerOptions();
    autoResizeInput();
    renderPendingAttachments();
    updateSendState();
    refreshUnread();
    if (isAuthenticated) {
        fetchHistory().then((messages) => { if (messages.length) renderHistory(messages); });
        setInterval(refreshUnread, 30000);
    }
    return true;
}

document.addEventListener("DOMContentLoaded", () => {
    const root = document.getElementById("chatWidget") || document;
    if (root !== document && root.dataset.chatInitialized === "true") return;
    if (root !== document) root.dataset.chatInitialized = "true";

    const fab = document.getElementById("chatFab");
    const panel = document.getElementById("chatPanel");
    const panelContent = document.getElementById("chatPanelContent");
    const closeBtn = document.getElementById("chatClose");
    const clearBtn = document.getElementById("chatClear");
    const form = document.getElementById("chatForm");
    const input = document.getElementById("chatInput");
    const body = document.getElementById("chatBody");
    const sendBtn = document.getElementById("chatSend");
    const dot = document.getElementById("chatNotificationDot");
    const normalTabBtn = document.getElementById("chatTabNormal");
    const notificationsToggle2 = document.getElementById("chatNotificationsToggle2");
    const notifFilterAll = document.getElementById("chatNotifFilterAll");
    const notifFilterUnread = document.getElementById("chatNotifFilterUnread");
    const notificationsUnreadCount = document.getElementById("chatNotificationsUnreadCount");
    const notificationsView = document.getElementById("chatNotificationsView");
    const chatView = document.getElementById("chatChatView");
    const notificationsList = document.getElementById("chatNotificationsList");
    const notificationsReadAll = document.getElementById("chatNotificationsReadAll");
    const unreadBadge = document.getElementById("chatUnreadBadge");
    const toastRegion = document.getElementById("chatToastRegion");
    const inlineToast = document.getElementById("chatInlineToast");
    const proChatBtn = document.getElementById("chatProChatBtn");
    const chatPlusAccessNotice = document.getElementById("chatPlusAccessNotice");
    const chatAttachImageBtn = document.getElementById("chatAttachImageBtn");
    const chatComposerTools = document.getElementById("chatComposerTools");
    const chatComposerOptions = document.getElementById("chatComposerOptions");
    const chatUseCameraBtn = document.getElementById("chatUseCameraBtn");
    const chatUsePhotosBtn = document.getElementById("chatUsePhotosBtn");
    const chatCameraInput = document.getElementById("chatCameraInput");
    const chatPhotoInput = document.getElementById("chatPhotoInput");
    const chatAttachmentPreviewTray = document.getElementById("chatAttachmentPreviewTray");
    const chatAttachmentCount = document.getElementById("chatAttachmentCount");
    const chatCharacterCount = document.getElementById("chatCharacterCount");
    const clearInlineConfirm = document.getElementById("chatInlineClearConfirm");
    const clearInlineCancel = document.getElementById("chatClearCancel");
    const clearInlineConfirmBtn = document.getElementById("chatClearConfirm");
    const chatMediaLightbox = document.getElementById("chatMediaLightbox");
    const chatMediaLightboxBackdrop = document.getElementById("chatMediaLightboxBackdrop");
    const chatMediaLightboxClose = document.getElementById("chatMediaLightboxClose");
    const chatMediaLightboxImage = document.getElementById("chatMediaLightboxImage");

    // Detailed error logging for debugging
    if (!fab || !panel || !panelContent || !closeBtn || !clearBtn || !form || !input || !body || !sendBtn) {
        console.error("Chat widget init failed - missing elements:", {
            fab: !!fab, panel: !!panel, closeBtn: !!closeBtn, clearBtn: !!clearBtn,
            form: !!form, input: !!input, body: !!body, sendBtn: !!sendBtn
        });
        
        // Fallback: Create a minimal toggle function if panel exists
        if (panel && fab) {
            window.toggleChatPanel = function() {
                const isOpen = panel.classList.contains("open");
                if (isOpen) {
                    panel.classList.remove("open");
                    panel.setAttribute("aria-hidden", "true");
                    panel.setAttribute("inert", "");
                    concealChatPanelAfterTransition(panel);
                    fab.setAttribute("aria-expanded", "false");
                } else {
                    revealChatPanel(panel);
                    panel.classList.add("open");
                    panel.setAttribute("aria-hidden", "false");
                    panel.removeAttribute("inert");
                    fab.setAttribute("aria-expanded", "true");
                }
                console.log("Chat panel toggled:", !isOpen ? "open" : "closed");
            };
            console.log("Chat: Using fallback toggle function");
        }
        return;
    }

    const authMarker = document.getElementById("chatAuth");
    const isAuthenticated = authMarker?.dataset?.authenticated === "true";
    const isPremium = root?.dataset?.premium === "true";
    const accountRole = root?.dataset?.role || "GUEST";
    const initialWelcomeText = body.querySelector(".chat-message-bubble")?.textContent?.trim() || "";

    const csrfToken = document.getElementById("chat_csrf")?.value || null;
    const csrfHeader = document.getElementById("chat_csrf_header")?.value || "X-CSRF-TOKEN";

    const STORAGE_KEY = "one2one_chat_history_v2";
    const LEGACY_STORAGE_KEY = "one2one_chat_history_v1";
    const TOAST_DURATION = 5000;
    const MAX_ATTACHMENTS = 5;
    const MAX_COMPOSER_HEIGHT = 160;
    const MAX_MESSAGE_LENGTH = 1600;
    let notificationsPollTimer = null;
    let eventSource = null;
    let sseRetryCount = 0;
    const MAX_SSE_RETRIES = 5;
    const notificationSyncChannel = "BroadcastChannel" in window ? new BroadcastChannel("one-to-one-notifications") : null;

    return initCharlieWidget({
        root, fab, panel, panelContent, closeBtn, clearBtn, form, input, body, sendBtn,
        dot, normalTabBtn, notificationsToggle2, notificationsView, notifFilterAll, notifFilterUnread,
        notificationsUnreadCount, notificationsList, notificationsReadAll, unreadBadge,
        inlineToast, proChatBtn, chatPlusAccessNotice, chatAttachImageBtn, chatComposerTools, chatComposerOptions, chatUseCameraBtn,
        chatUsePhotosBtn, chatCameraInput, chatPhotoInput, chatAttachmentPreviewTray,
        chatAttachmentCount, chatCharacterCount, clearInlineConfirm, clearInlineCancel, clearInlineConfirmBtn,
        chatMediaLightbox, chatMediaLightboxBackdrop, chatMediaLightboxClose, chatMediaLightboxImage,
        isAuthenticated, isPremium, accountRole, initialWelcomeText, csrfToken, csrfHeader,
        STORAGE_KEY, LEGACY_STORAGE_KEY, MAX_ATTACHMENTS, MAX_COMPOSER_HEIGHT, MAX_MESSAGE_LENGTH
    });

    function broadcastNotificationSync(detail) {
        const payload = detail || {};
        window.dispatchEvent(new CustomEvent("one-to-one:notifications-updated", { detail: payload }));
        try {
            notificationSyncChannel?.postMessage(payload);
        } catch (_) {
            // ignore
        }
    }

    function open() {
        revealChatPanel(panel);
        panel.classList.add("open");
        panel.setAttribute("aria-hidden", "false");
        panel.removeAttribute("inert");
        fab.setAttribute("aria-expanded", "true");
        hideNotificationsPanel();
        dot?.classList.remove("active");
        if (!input.disabled) {
            setTimeout(() => input.focus(), 50);
        }
    }

    function close() {
        const activeElement = document.activeElement;
        if (activeElement && panel.contains(activeElement)) {
            fab.focus();
        }
        hideNotificationsPanel();
        panel.classList.remove("open");
        panel.setAttribute("aria-hidden", "true");
        panel.setAttribute("inert", "");
        concealChatPanelAfterTransition(panel);
        fab.setAttribute("aria-expanded", "false");
    }

    function scrollToBottom() {
        body.scrollTop = body.scrollHeight;
    }

    function addMsg(text, who) {
        const wrap = document.createElement("div");
        wrap.className = `chat-message-wrapper ${who === "me" ? "user" : "assistant"}`;

        const bubble = document.createElement("div");
        bubble.className = "chat-message-bubble";
        bubble.appendChild(renderRichMessageContent(text));

        wrap.appendChild(bubble);
        appendImagePreviews(wrap, text);
        body.appendChild(wrap);
        scrollToBottom();
    }

    function renderRichMessageContent(text) {
        const fragment = document.createDocumentFragment();
        const safeText = (text || "").trim();
        const urlRegex = /(https?:\/\/[^\s]+)/gi;
        const parts = safeText.split(urlRegex);
        parts.forEach((part) => {
            if (!part) return;
            if (urlRegex.test(part)) {
                const link = document.createElement("a");
                link.href = part;
                link.target = "_blank";
                link.rel = "noopener noreferrer";
                link.textContent = part;
                fragment.appendChild(link);
            } else {
                fragment.appendChild(document.createTextNode(part));
            }
            urlRegex.lastIndex = 0;
        });
        return fragment;
    }

    function appendImagePreviews(target, text) {
        const matches = (text || "").match(/https?:\/\/[^\s]+/gi) || [];
        matches
            .filter((url) => /\.(png|jpe?g|gif|webp|svg)(\?.*)?$/i.test(url))
            .slice(0, 2)
            .forEach((url) => {
                const image = document.createElement("img");
                image.src = url;
                image.alt = "Shared image";
                image.className = "chat-image-preview";
                target.appendChild(image);
            });
    }

    function addMsgWithNav(text, who, navActions, imageUrl) {
        const wrap = document.createElement("div");
        wrap.className = `chat-message-wrapper ${who === "me" ? "user" : "assistant"}`;

        const bubble = document.createElement("div");
        bubble.className = "chat-message-bubble";
        bubble.appendChild(renderRichMessageContent(text));
        wrap.appendChild(bubble);

        if (imageUrl) {
            const image = document.createElement("img");
            image.src = imageUrl;
            image.alt = "Attached image";
            image.className = "chat-image-preview";
            wrap.appendChild(image);
        }
        appendImagePreviews(wrap, text);

        if (Array.isArray(navActions) && navActions.length > 0) {
            const navRow = document.createElement("div");
            navRow.className = "chat-nav-actions";
            navActions.forEach(action => {
                const btn = document.createElement("a");
                btn.href = action.url;
                btn.className = "chat-nav-btn";
                btn.textContent = action.label;
                // Safety: only relative paths are rendered (whitelist enforced server-side)
                if (!action.url.startsWith("/")) return;
                navRow.appendChild(btn);
            });
            if (navRow.children.length > 0) {
                wrap.appendChild(navRow);
            }
        }

        body.appendChild(wrap);
        scrollToBottom();
    }

    function addTyping() {
        const wrap = document.createElement("div");
        wrap.className = "chat-typing-indicator";
        wrap.id = "typingRow";

        const bubble = document.createElement("div");
        bubble.className = "chat-typing-bubble";
        bubble.textContent = "Charlie is thinking";
        const dots = document.createElement("span");
        dots.className = "chat-typing-dots";
        dots.innerHTML = "<span></span><span></span><span></span>";
        bubble.appendChild(dots);

        wrap.appendChild(bubble);
        body.appendChild(wrap);
        scrollToBottom();
    }

    function removeTyping() {
        const t = document.getElementById("typingRow");
        if (t) t.remove();
    }

    function getAuthenticatedWelcome() {
        if (initialWelcomeText) return initialWelcomeText;
        const roleLabel = accountRole === "TRAINER"
            ? "trainer"
            : accountRole === "GYM_ADMIN"
                ? "gym"
                : "client";
        if (isPremium) {
            return `Welcome back — I’m Charlie, your premium ${roleLabel} assistant.`;
        }
        return `Welcome back — I’m Charlie. You have 15 prompts per day on Starter access.`;
    }

    function getUnauthWelcome() {
        return initialWelcomeText || "Hi, I’m Charlie — the One To One website assistant. I can explain platform features and guide you to the right pages.";
    }

    function saveHistory() {
        if (!historyAllowed) return;

        const msgs = [];
        body.querySelectorAll(".chat-message-wrapper, .chat-msg").forEach(m => {
            const isUser = m.classList.contains("user") || m.classList.contains("chat-msg-me");
            const text = m.querySelector(".chat-message-bubble, .bubble")?.textContent || "";
            if (text.trim().length > 0 && text !== "Typing…") msgs.push({ who: isUser ? "me" : "ai", text });
        });
        localStorage.setItem(STORAGE_KEY, JSON.stringify(msgs.slice(-200)));
    }

    function readHistory() {
        try {
            const raw = localStorage.getItem(STORAGE_KEY);
            if (!raw) return;
            const msgs = JSON.parse(raw);
            if (!Array.isArray(msgs) || msgs.length === 0) return;

            return msgs;
        } catch (e) {
            // ignore
        }
    }

    function renderHistory(msgs) {
        body.innerHTML = "";
        if (!Array.isArray(msgs) || msgs.length === 0) {
            addMsg(getAuthenticatedWelcome(), "ai");
            return;
        }
        msgs.forEach(m => addMsg(m.text, m.who));
    }

    function renderUnauth() {
        body.innerHTML = "";
        addMsg(getUnauthWelcome(), "ai");
    }

    async function fetchServerHistory() {
        if (!isAuthenticated) return null;
        try {
            const res = await fetch("/chat/history", { method: "GET" });
            if (!res.ok) return null;
            const data = await res.json();
            if (!Array.isArray(data)) return null;
            return data
                .filter(m => m && typeof m.text === "string" && (m.who === "me" || m.who === "ai"))
                .map(m => ({ who: m.who, text: m.text }));
        } catch {
            return null;
        }
    }

    async function sendMessage(message) {
        if (!isAuthenticated) {
            addMsg(getUnauthWelcome(), "ai");
            return;
        }
        sendBtn.disabled = true;
        addTyping();

        try {
            const headers = { "Content-Type": "application/json" };
            if (csrfToken) headers[csrfHeader] = csrfToken;

            const res = await fetch("/chat/api", {
                method: "POST",
                headers,
                body: JSON.stringify({ message })
            });

            removeTyping();

            let data = null;
            try {
                data = await res.json();
            } catch {
                // ignore
            }

            if (!res.ok) {
                addMsg(data?.reply || "Something went wrong talking to the AI. Try again.", "ai");
                dot?.classList.add("active");
                return;
            }

            const reply = data?.reply || "No response";
            const navActions = Array.isArray(data?.navActions) ? data.navActions : [];
            addMsgWithNav(reply, "ai", navActions);
            saveHistory();

        } catch (err) {
            removeTyping();
            addMsg("AI request failed (network/server).", "ai");
            dot?.classList.add("active");
        } finally {
            sendBtn.disabled = false;
        }
    }

    function showInlineToast(message, tone = "default") {
        if (!inlineToast) return;
        inlineToast.textContent = message;
        inlineToast.classList.toggle("chat-inline-toast-warning", tone === "warning");
        inlineToast.classList.remove("hidden");
        setTimeout(() => inlineToast.classList.add("hidden"), 2500);
    }

    function setUnreadBadge(count) {
        if (!unreadBadge) return;
        if (count > 0) {
            unreadBadge.textContent = count > 99 ? "99+" : `${count}`;
            unreadBadge.classList.remove("hidden");
            unreadBadge.classList.add("inline-flex");
        } else {
            unreadBadge.textContent = "";
            unreadBadge.classList.add("hidden");
            unreadBadge.classList.remove("inline-flex");
        }
        // Also update the notifications panel header count
        if (notificationsUnreadCount) {
            if (count > 0) {
                notificationsUnreadCount.textContent = count > 99 ? "99+" : `${count}`;
                notificationsUnreadCount.classList.remove("hidden");
                notificationsUnreadCount.classList.add("inline-flex");
            } else {
                notificationsUnreadCount.textContent = "";
                notificationsUnreadCount.classList.add("hidden");
                notificationsUnreadCount.classList.remove("inline-flex");
            }
        }
    }

    function setNavUnreadBadges(count) {
        const badges = document.querySelectorAll(".nav-notification-badge");
        if (!badges.length) return;
        badges.forEach((badge) => {
            if (count > 0) {
                badge.textContent = count > 99 ? "99+" : `${count}`;
                badge.classList.remove("hidden");
                badge.classList.add("inline-flex");
            } else {
                badge.textContent = "";
                badge.classList.add("hidden");
                badge.classList.remove("inline-flex");
            }
        });
    }

    async function refreshUnreadCount() {
        if (!isAuthenticated) return;
        try {
            const res = await fetch("/api/notifications/unread-count");
            if (!res.ok) return;
            const data = await res.json();
            const count = Number(data?.count || 0);
            setUnreadBadge(count);
            setNavUnreadBadges(count);
        } catch {
            // ignore
        }
    }

    function buildNotificationRow(notification) {
        const row = document.createElement("div");
        const isUnread = !notification.readAt && !notification.dismissedAt;
        
        // Premium styling: Unread has subtle accent color
        row.className = `group rounded-xl border p-3 shadow-sm transition-all ${
            isUnread 
                ? "border-emerald-500/30 bg-emerald-500/5 dark:bg-emerald-500/10" 
                : "border-slate-200 bg-slate-50/60 hover:bg-white dark:border-slate-800 dark:bg-slate-900/60 dark:hover:bg-slate-900"
        }`;

        const header = document.createElement("div");
        header.className = "flex items-start justify-between gap-2";

        const title = document.createElement("div");
        title.className = "flex items-center gap-2 text-xs font-semibold text-slate-600 dark:text-slate-300";
        
        if (isUnread) {
             const dot = document.createElement("span");
             dot.className = "h-2 w-2 rounded-full bg-emerald-500 ring-2 ring-emerald-500/20";
             title.appendChild(dot);
        }
        
        const titleText = document.createElement("span");
        titleText.textContent = notification.title || "Notification";
        title.appendChild(titleText);

        const dismiss = document.createElement("button");
        dismiss.type = "button";
        dismiss.className = "opacity-0 group-hover:opacity-100 transition-opacity text-[11px] font-semibold text-slate-400 hover:text-slate-700 dark:hover:text-slate-200";
        dismiss.textContent = "Dismiss";
        dismiss.addEventListener("click", async (e) => {
            e.stopPropagation();
            await dismissNotification(notification.id);
            await loadNotifications();
            await refreshUnreadCount();
            broadcastNotificationSync({ source: "chat-widget", notificationId: notification.id, action: "dismiss" });
        });

        header.appendChild(title);
        header.appendChild(dismiss);

        const message = document.createElement("div");
        message.className = "mt-1.5 text-sm text-slate-700 dark:text-slate-200 leading-snug";
        message.textContent = notification.message;

        const time = document.createElement("div");
        time.className = "mt-2 text-[10px] text-slate-400";
        // Simple relative time approximation or date parse could go here

        row.appendChild(header);
        row.appendChild(message);

        if (notification.ctaUrl) {
            const cta = document.createElement("a");
            cta.href = notification.ctaUrl;
            cta.className = "mt-2 inline-flex items-center rounded-lg border border-slate-200 bg-white px-2.5 py-1 text-[11px] font-semibold text-slate-700 shadow-sm transition hover:bg-slate-50 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-200";
            cta.textContent = "Open";
            cta.addEventListener("click", async (e) => {
                e.stopPropagation();
                if (isUnread) {
                    const headers = {};
                    if (csrfToken) headers[csrfHeader] = csrfToken;
                    await fetch(`/api/notifications/${notification.id}/read`, { method: "POST", headers });
                    await refreshUnreadCount();
                    broadcastNotificationSync({ source: "chat-widget", notificationId: notification.id, action: "read" });
                }
            });
            row.appendChild(cta);
        }
        
        // Mark as read on click if unread
        if (isUnread) {
            row.addEventListener("click", async () => {
                 const headers = {};
                 if (csrfToken) headers[csrfHeader] = csrfToken;
                 await fetch(`/api/notifications/${notification.id}/read`, { method: "POST", headers });
                 await loadNotifications();
                 await refreshUnreadCount();
                 broadcastNotificationSync({ source: "chat-widget", notificationId: notification.id, action: "read" });
             });
            row.style.cursor = "pointer";
        }
        
        return row;
    }

    async function loadNotifications() {
        if (!isAuthenticated || !notificationsList) return;
        try {
            const res = await fetch("/api/notifications?limit=20");
            if (!res.ok) return;
            let data = await res.json();
            notificationsList.innerHTML = "";
            if (!Array.isArray(data)) return;
            // Apply filter
            if (currentFilter === "unread") {
                data = data.filter(n => !n.readAt && !n.dismissedAt);
            }
            if (data.length === 0) {
                const emptyMsg = currentFilter === "unread" ? "No unread notifications." : "No notifications yet.";
                notificationsList.innerHTML = `<div class="rounded-xl border border-dashed border-slate-200 bg-slate-50 px-3 py-6 text-center text-xs text-slate-500 dark:border-slate-800 dark:bg-slate-900/40 dark:text-slate-400"><div class="text-2xl mb-1" aria-hidden="true">🔔</div>${emptyMsg}</div>`;
                return;
            }

            data.forEach(n => notificationsList.appendChild(buildNotificationRow(n)));
        } catch {
            // ignore
        }
    }

    async function dismissNotification(id) {
        if (!id) return;
        const headers = {};
        if (csrfToken) headers[csrfHeader] = csrfToken;
        await fetch(`/api/notifications/${id}/dismiss`, { method: "POST", headers });
        broadcastNotificationSync({ source: "chat-widget", notificationId: id, action: "dismiss" });
    }

    function showToast(notification) {
        if (!toastRegion || !notification) return;

        const toast = document.createElement("div");
        toast.className = "pointer-events-auto overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-lg dark:border-slate-800 dark:bg-slate-950";

        const content = document.createElement("div");
        content.className = "flex items-start justify-between gap-3 px-4 py-3";

        const textWrap = document.createElement("div");
        textWrap.className = "flex-1";

        if (notification.title) {
            const title = document.createElement("div");
            title.className = "text-xs font-semibold text-slate-500 dark:text-slate-400";
            title.textContent = notification.title;
            textWrap.appendChild(title);
        }

        const message = document.createElement("div");
        message.className = "mt-1 text-sm text-slate-900 dark:text-slate-100";
        message.textContent = notification.message || "Notification";
        textWrap.appendChild(message);

        const close = document.createElement("button");
        close.type = "button";
        close.className = "inline-flex h-7 w-7 items-center justify-center rounded-full border border-slate-200 text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900";
        close.textContent = "✕";

        const progress = document.createElement("div");
        progress.className = "h-1 w-full bg-slate-200 dark:bg-slate-800";
        const bar = document.createElement("div");
        bar.className = "h-full bg-emerald-500";
        bar.style.width = "100%";
        bar.style.transition = `width ${TOAST_DURATION}ms linear`;
        progress.appendChild(bar);

        content.appendChild(textWrap);
        content.appendChild(close);
        toast.appendChild(content);
        toast.appendChild(progress);
        toastRegion.prepend(toast);

        const finish = async () => {
            toast.remove();
            await refreshUnreadCount();
            if (notificationsView && !notificationsView.classList.contains("hidden")) {
                await loadNotifications();
            }
        };

        const timeoutId = setTimeout(finish, TOAST_DURATION);
        setTimeout(() => {
            bar.style.width = "0%";
        }, 20);

        close.addEventListener("click", async () => {
            clearTimeout(timeoutId);
            await dismissNotification(notification.id);
            await finish();
        });
    }

    function connectSse() {
        if (!isAuthenticated) return;
        if (eventSource) {
            // If already OPEN (1) or CONNECTING (0), do nothing
            if (eventSource.readyState === 0 || eventSource.readyState === 1) return;
            eventSource.close();
        }

        eventSource = new EventSource("/api/notifications/stream");
        
        eventSource.addEventListener("open", () => {
             sseRetryCount = 0; // Reset retry count on successful connection
        });

        eventSource.addEventListener("notification", (event) => {
            try {
                const data = JSON.parse(event.data);
                showToast(data);
                if (notificationsView && !notificationsView.classList.contains("hidden")) {
                    loadNotifications();
                }
                refreshUnreadCount();
                broadcastNotificationSync({ source: "chat-widget", notificationId: data?.id, action: "create" });
            } catch {
                // ignore
            }
        });

        eventSource.onerror = () => {
            eventSource.close();
            eventSource = null;
            
            if (sseRetryCount < MAX_SSE_RETRIES) {
                const delay = Math.pow(2, sseRetryCount) * 2000; // 2s, 4s, 8s, 16s, 32s
                console.log(`SSE connection lost. Retrying in ${delay}ms (Attempt ${sseRetryCount + 1}/${MAX_SSE_RETRIES})`);
                sseRetryCount++;
                setTimeout(connectSse, delay);
            } else {
                console.warn("SSE connection failed tailored after max retries. Switching to polling fallback.");
                startPolling();
            }
        };
    }

    function startPolling() {
        if (notificationsPollTimer || !isAuthenticated) return;
        // Poll unread count frequently (30s)
        notificationsPollTimer = setInterval(refreshUnreadCount, 30000);
        
        // Also verify immediate sync
        refreshUnreadCount();
    }

    window.addEventListener("one-to-one:notifications-updated", async () => {
        await refreshUnreadCount();
        if (notificationsView && !notificationsView.classList.contains("hidden")) {
            await loadNotifications();
        }
    });
    notificationSyncChannel?.addEventListener("message", async () => {
        await refreshUnreadCount();
        if (notificationsView && !notificationsView.classList.contains("hidden")) {
            await loadNotifications();
        }
    });

    // Events
    window.toggleChatPanel = function() {
        try {
            const isOpen = panel.classList.contains("open");
            console.log("toggleChatPanel called, current state:", isOpen ? "open" : "closed");
            isOpen ? close() : open();
        } catch (err) {
            console.error("Error in toggleChatPanel:", err);
        }
    };

    fab.addEventListener("click", (e) => {
        try {
            e.preventDefault();
            e.stopPropagation();
            console.log("Chat FAB clicked");
            window.toggleChatPanel();
        } catch (err) {
            console.error("Error handling chat FAB click:", err);
        }
    });
    
    closeBtn.addEventListener("click", (e) => {
        try {
            e.preventDefault();
            close();
        } catch (err) {
            console.error("Error handling chat close:", err);
        }
    });
    
    console.log("Chat widget initialized successfully. FAB element:", fab);

    // Quick-action suggestion chips
    const suggestions = document.getElementById("chatSuggestions");
    if (suggestions) {
        suggestions.addEventListener("click", async (e) => {
            const chip = e.target.closest(".chat-suggestion-chip");
            if (!chip || !isAuthenticated) return;
            const msg = chip.dataset.msg;
            if (!msg) return;
            input.value = msg;
            form.dispatchEvent(new Event("submit", { cancelable: true, bubbles: true }));
        });
    }

    function showNotificationsPanel() {
        if (!notificationsView || !chatView) return;
        chatView.classList.add("hidden");
        notificationsView.classList.remove("hidden");
        chatView.style.display = "none";
        notificationsView.style.display = "flex";
        notificationsToggle2?.classList.add("active");
        normalTabBtn?.classList.remove("active");
        loadNotifications();
    }

    function hideNotificationsPanel() {
        if (!notificationsView || !chatView) return;
        notificationsView.classList.add("hidden");
        chatView.classList.remove("hidden");
        notificationsView.style.display = "none";
        chatView.style.display = "flex";
        notificationsToggle2?.classList.remove("active");
        normalTabBtn?.classList.add("active");
    }

    function showCharlieOutput(message) {
        if (!message || typeof message !== "string") return;
        open();
        hideNotificationsPanel();
        addMsg(message.trim(), "ai");
        saveHistory();
    }

    // Public helper so any page can hand off Charlie text to chat panel.
    window.openCharlieChatWithMessage = function(message) {
        showCharlieOutput(message);
    };

    if (notificationsToggle2 && notificationsView && chatView) {
        notificationsToggle2.addEventListener("click", async () => {
            const showingNotifications = !notificationsView.classList.contains("hidden");
            if (showingNotifications) {
                hideNotificationsPanel();
            } else {
                showNotificationsPanel();
            }
        });
    }

    if (normalTabBtn) {
        normalTabBtn.addEventListener("click", () => {
            hideNotificationsPanel();
            if (!panel.classList.contains("open")) open();
        });
    }

    // Notification filter tabs
    let currentFilter = "all";
    if (notifFilterAll) {
        notifFilterAll.addEventListener("click", async () => {
            currentFilter = "all";
            notifFilterAll.classList.add("active");
            notifFilterUnread?.classList.remove("active");
            await loadNotifications();
        });
    }
    if (notifFilterUnread) {
        notifFilterUnread.addEventListener("click", async () => {
            currentFilter = "unread";
            notifFilterUnread.classList.add("active");
            notifFilterAll?.classList.remove("active");
            await loadNotifications();
        });
    }

    // Mobile notifications toggle button (legacy, keep for compat)
    const notificationsToggleMobile = document.getElementById("chatNotificationsToggleMobile");
    if (notificationsToggleMobile && notificationsView && chatView) {
        notificationsToggleMobile.addEventListener("click", async () => {
            const showingNotifications = !notificationsView.classList.contains("hidden");
            if (showingNotifications) {
                hideNotificationsPanel();
            } else {
                showNotificationsPanel();
            }
        });
    }

    if (notificationsReadAll) {
        notificationsReadAll.addEventListener("click", async () => {
            if (!isAuthenticated) return;
            const headers = {};
            if (csrfToken) headers[csrfHeader] = csrfToken;
            await fetch("/api/notifications/read-all", { method: "POST", headers });
            await loadNotifications();
            await refreshUnreadCount();
            broadcastNotificationSync({ source: "chat-widget", action: "read-all" });
        });
    }

    const dashboardPrompt = document.getElementById("charlieDashboardPrompt");
    if (dashboardPrompt && dashboardPrompt.dataset.message) {
        const accountKey = (dashboardPrompt.dataset.account || "anonymous").trim().toLowerCase();
        const promptSeenKey = `charlieDashboardPromptSeen:${accountKey}`;
        let promptAlreadySeen = false;
        try {
            promptAlreadySeen = window.localStorage.getItem(promptSeenKey) === "1";
        } catch (_) {
            promptAlreadySeen = false;
        }

        if (!promptAlreadySeen) {
            setTimeout(() => {
                showCharlieOutput(dashboardPrompt.dataset.message);
                try {
                    window.localStorage.setItem(promptSeenKey, "1");
                } catch (_) {
                    // Ignore storage failures (private mode, quota, blocked storage).
                }
            }, 140);
        }
    }

    if (proChatBtn) {
        const locked = proChatBtn.getAttribute("data-locked") === "true" || !isPremium;
        if (locked) {
            proChatBtn.classList.add("locked-pill");
            proChatBtn.setAttribute("aria-disabled", "true");
            proChatBtn.title = "Upgrade to use Pro Chat";
        }
        proChatBtn.addEventListener("click", (e) => {
            if (locked) {
                e.preventDefault();
                showInlineToast("Upgrade to use Pro Chat", "warning");
                setTimeout(() => {
                    window.location.href = "/pricing";
                }, 320);
            } else {
                window.location.href = "/chat";
            }
        });
    }

    clearBtn.addEventListener("click", () => {
        if (clearModal) {
            clearModal.classList.add("show");
        }
    });

    clearModalCancel?.addEventListener("click", () => {
        clearModal?.classList.remove("show");
    });

    clearModal?.addEventListener("click", (e) => {
        if (e.target === clearModal) clearModal.classList.remove("show");
    });

    clearModalConfirm?.addEventListener("click", () => {
        clearModal?.classList.remove("show");

        if (isAuthenticated) {
            renderHistory([]);
        } else {
            renderUnauth();
        }
        localStorage.removeItem(STORAGE_KEY);
        showInlineToast("✓ Chat history cleared");

        if (!isAuthenticated) return;
        const headers = {};
        if (csrfToken) headers[csrfHeader] = csrfToken;
        fetch("/chat/clear", { method: "POST", headers }).catch(() => {
            // ignore
        });
    });

    chatAttachImageBtn?.addEventListener("click", () => {
        if (input.disabled) return;
        chatImageInput?.click();
    });

    chatImageInput?.addEventListener("change", () => {
        const file = chatImageInput.files?.[0];
        if (!file) return;
        const previewUrl = URL.createObjectURL(file);
        pendingImageAttachment = {
            fileName: file.name,
            previewUrl
        };
        showInlineToast(`Attached image: ${file.name}`);
    });

    form.addEventListener("submit", async (e) => {
        e.preventDefault();

        if (!isAuthenticated || input.disabled) {
            addMsg(getUnauthWelcome(), "ai");
            return;
        }

        const msg = (input.value || "").trim();
        if (!msg && !pendingImageAttachment) return;
        const finalMessage = pendingImageAttachment
            ? `${msg}${msg ? "\n\n" : ""}[Attached image: ${pendingImageAttachment.fileName}]`
            : msg;
        input.value = "";
        addMsgWithNav(finalMessage, "me", [], pendingImageAttachment?.previewUrl || null);
        if (pendingImageAttachment?.previewUrl) {
            URL.revokeObjectURL(pendingImageAttachment.previewUrl);
        }
        pendingImageAttachment = null;
        if (chatImageInput) chatImageInput.value = "";
        saveHistory();
        await sendMessage(finalMessage);
    });

    // Load previous messages
    if (isAuthenticated) {
        const local = readHistory();
        renderHistory(local);

        if (historyAllowed) {
            fetchServerHistory().then(serverMsgs => {
                if (Array.isArray(serverMsgs) && serverMsgs.length > 0) {
                    localStorage.setItem(STORAGE_KEY, JSON.stringify(serverMsgs.slice(-200)));
                    renderHistory(serverMsgs);
                }
            });
        }
    } else {
        renderUnauth();
    }
    scrollToBottom();

    if (isAuthenticated) {
        refreshUnreadCount();
        connectSse();
    }
});
