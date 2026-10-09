(function () {
    "use strict";

    const NOTIFICATION_CHANNEL = "one-to-one-notifications";
    const notificationChannel = "BroadcastChannel" in window ? new BroadcastChannel(NOTIFICATION_CHANNEL) : null;

    function getCsrfHeaders(contentType) {
        const token = document.querySelector('meta[name="_csrf"]')?.getAttribute("content")
            || document.getElementById("chat_csrf")?.value
            || document.getElementById("inbox_csrf")?.value
            || document.getElementById("quick_actions_csrf")?.value
            || "";
        const header = document.querySelector('meta[name="_csrf_header"]')?.getAttribute("content")
            || document.getElementById("chat_csrf_header")?.value
            || document.getElementById("inbox_csrf_header")?.value
            || document.getElementById("quick_actions_csrf_header")?.value
            || "X-CSRF-TOKEN";
        const headers = { "X-Requested-With": "XMLHttpRequest" };
        if (contentType) headers["Content-Type"] = contentType;
        if (token) headers[header] = token;
        return headers;
    }

    function emitNotificationUpdate(detail) {
        const payload = detail || {};
        window.dispatchEvent(new CustomEvent("one-to-one:notifications-updated", { detail: payload }));
        try {
            notificationChannel?.postMessage(payload);
        } catch (_) {
            // ignore channel issues
        }
    }

    function onNotificationUpdate(callback) {
        if (typeof callback !== "function") return;
        window.addEventListener("one-to-one:notifications-updated", callback);
        notificationChannel?.addEventListener("message", callback);
    }

    function prefersReducedMotion() {
        return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    }

    function getDashboardRoot() {
        return document.querySelector("[data-dashboard-role='CLIENT']");
    }

    function initDashboardShell() {
        const page = getDashboardRoot();
        const backdrop = document.querySelector("[data-dashboard-drawer-backdrop]");
        const flyouts = Array.from(document.querySelectorAll("[data-dashboard-flyout]"));
        if (!page || !backdrop || !flyouts.length || !window.matchMedia) return;

        const mobileQuery = window.matchMedia("(max-width: 1030px)");
        let activeFlyout = null;
        let lastHandle = null;

        const getHandle = (flyout) => flyout.querySelector("[data-dashboard-flyout-handle]");
        const getPanel = (flyout) => flyout.querySelector(".cd-dashboard-flyout__panel");
        const getFocusTarget = (flyout) => flyout.querySelector(".cd-dashboard-flyout__panel-inner");

        const sync = () => {
            const isMobile = mobileQuery.matches;
            if (!isMobile) {
                activeFlyout = null;
            }

            page.dataset.dashboardCondensed = isMobile ? "true" : "false";
            document.body.classList.toggle("cd-dashboard-drawer-open", isMobile && Boolean(activeFlyout));

            backdrop.hidden = !isMobile;
            backdrop.classList.toggle("is-visible", isMobile && Boolean(activeFlyout));
            backdrop.setAttribute("aria-hidden", isMobile && activeFlyout ? "false" : "true");

            flyouts.forEach((flyout) => {
                const key = flyout.dataset.dashboardFlyout;
                const isOpen = isMobile && key === activeFlyout;
                const handle = getHandle(flyout);
                const panel = getPanel(flyout);
                flyout.classList.toggle("is-open", isOpen);
                handle?.setAttribute("aria-expanded", isOpen ? "true" : "false");
                handle?.classList.toggle("is-active", isOpen);
                panel?.setAttribute("aria-hidden", isOpen ? "false" : "true");
                panel?.toggleAttribute("inert", !isOpen);
            });
        };

        const close = (restoreFocus = true) => {
            if (!activeFlyout && !mobileQuery.matches) {
                sync();
                return;
            }
            activeFlyout = null;
            sync();
            if (restoreFocus) {
                lastHandle?.focus();
            }
        };

        const open = (key, handle) => {
            if (!mobileQuery.matches) return;
            activeFlyout = key;
            lastHandle = handle || null;
            sync();
            getFocusTarget(flyouts.find((flyout) => flyout.dataset.dashboardFlyout === key))?.focus();
        };

        flyouts.forEach((flyout) => {
            const key = flyout.dataset.dashboardFlyout;
            const handle = getHandle(flyout);
            if (!key || !handle) return;

            handle.setAttribute("role", "button");
            handle.setAttribute("aria-label", handle.dataset.panelLabel || handle.getAttribute("aria-label"));
            handle.addEventListener("click", (event) => {
                if (!mobileQuery.matches) return;
                event.preventDefault();
                if (activeFlyout === key) {
                    close(false);
                    handle.focus();
                    return;
                }
                open(key, handle);
            });
            handle.addEventListener("keydown", (event) => {
                if (event.key !== " " || !mobileQuery.matches) return;
                event.preventDefault();
                handle.click();
            });

            flyout.querySelectorAll("[data-dashboard-flyout-close]").forEach((button) => {
                button.addEventListener("click", () => close());
            });
        });

        backdrop.addEventListener("click", () => close());
        document.addEventListener("keydown", (event) => {
            if (event.key === "Escape" && activeFlyout) {
                close();
            }
        });

        if (typeof mobileQuery.addEventListener === "function") {
            mobileQuery.addEventListener("change", sync);
        } else if (typeof mobileQuery.addListener === "function") {
            mobileQuery.addListener(sync);
        }

        sync();
        page.dataset.dashboardShellReady = "true";
    }

    function initCardRevealAnimations() {
        const page = getDashboardRoot();
        if (!page) return;

        const cards = Array.from(page.querySelectorAll([
            ".cd-panel",
            ".cd-rail-card",
            ".cd-primary-cta",
            ".cd-action-card",
            ".cd-upcoming-card",
            ".cd-goal-card",
            ".cd-summary-card",
            ".cd-week-preview",
            ".cd-week-day-button",
            ".cd-link-tile",
            ".cd-context-panel",
            ".cd-activity-item",
            ".cd-identity-stat",
            ".cd-profile-card__section",
            ".cd-streak-card",
            ".cd-trainer-banner-shell",
            ".cd-reveal-panel",
            ".cd-empty-state",
            ".cd-dashboard-flyout__panel-inner"
        ].join(",")));

        if (!cards.length) return;

        cards.forEach((card, index) => {
            card.classList.add("cd-inview-reveal");
            card.style.setProperty("--cd-reveal-delay", `${Math.min(index % 5, 4) * 85}ms`);
        });

        if (prefersReducedMotion() || !("IntersectionObserver" in window)) {
            cards.forEach((card) => card.classList.add("is-visible"));
            return;
        }

        let revealCheckQueued = false;

        const reveal = (card) => {
            card.classList.add("is-visible");
            observer.unobserve(card);
        };

        const stopRevealChecksWhenComplete = () => {
            if (cards.some((card) => !card.classList.contains("is-visible"))) return;
            window.removeEventListener("scroll", queueRevealCheck);
            window.removeEventListener("resize", queueRevealCheck);
            page.removeEventListener("focusin", revealFocusedCard);
        };

        const revealReachedCards = () => {
            revealCheckQueued = false;
            cards.forEach((card) => {
                if (card.classList.contains("is-visible")) return;
                if (card.getBoundingClientRect().top > window.innerHeight * 0.92) return;
                reveal(card);
            });
            stopRevealChecksWhenComplete();
        };

        function queueRevealCheck() {
            if (revealCheckQueued) return;
            revealCheckQueued = true;
            window.requestAnimationFrame(revealReachedCards);
        }

        function revealFocusedCard(event) {
            const card = event.target.closest(".cd-inview-reveal");
            if (!card || card.classList.contains("is-visible")) return;
            reveal(card);
            card.scrollIntoView({ block: "nearest", behavior: "auto" });
            stopRevealChecksWhenComplete();
        }

        const observer = new IntersectionObserver((entries) => {
            entries.forEach((entry) => {
                if (!entry.isIntersecting) return;
                reveal(entry.target);
            });
            stopRevealChecksWhenComplete();
        }, {
            threshold: 0.12,
            rootMargin: "0px 0px -10% 0px"
        });

        cards.forEach((card) => observer.observe(card));
        window.addEventListener("scroll", queueRevealCheck, { passive: true });
        window.addEventListener("resize", queueRevealCheck);
        page.addEventListener("focusin", revealFocusedCard);
        revealReachedCards();
    }

    function getDashboardTimeDisplayFormat() {
        return getDashboardRoot()?.dataset.dashboardTimeDisplayFormat || "TWELVE_HOUR";
    }

    function formatCountdown(diffMs) {
        const totalSeconds = Math.max(0, Math.floor(diffMs / 1000));
        const timeDisplayFormat = getDashboardTimeDisplayFormat();
        const hoursValue = Math.floor(totalSeconds / 3600);
        const hours = timeDisplayFormat === "TWENTY_FOUR_HOUR"
            ? String(hoursValue).padStart(2, "0")
            : String(hoursValue);
        const minutes = String(Math.floor((totalSeconds % 3600) / 60)).padStart(2, "0");
        const seconds = String(totalSeconds % 60).padStart(2, "0");
        return `${hours}:${minutes}:${seconds}`;
    }

    function describeCountdown(diffMs) {
        if (diffMs <= 0) return "Due now";
        const mins = Math.floor(diffMs / 60000);
        if (mins < 1) return "Less than 1 min";
        if (mins < 60) return `${mins} min left`;
        const hours = Math.floor(mins / 60);
        const rem = mins % 60;
        return rem ? `${hours}h ${rem}m left` : `${hours}h left`;
    }

    function initGoalSlider() {
        document.querySelectorAll("[data-goal-slider]").forEach((root) => {
            const tabs = Array.from(root.querySelectorAll("[data-goal-tab]"));
            const track = root.querySelector("[data-goal-slider-track]");
            const views = Array.from(root.querySelectorAll("[data-goal-view]"));
            if (!tabs.length || !track || !views.length) return;
            root.querySelector(".cd-goal-slider__tabs")?.setAttribute("role", "tablist");
            tabs.forEach((tab) => tab.setAttribute("role", "tab"));
            views.forEach((view) => view.setAttribute("role", "tabpanel"));

            const setHeight = (view) => {
                if (!view) return;
                track.style.height = `${view.offsetHeight}px`;
            };

            const setActive = (key) => {
                const index = Math.max(0, tabs.findIndex((tab) => tab.dataset.goalTab === key));
                tabs.forEach((tab, tabIndex) => {
                    const active = tabIndex === index;
                    tab.classList.toggle("is-active", active);
                    tab.setAttribute("aria-selected", active ? "true" : "false");
                    tab.tabIndex = active ? 0 : -1;
                });
                views.forEach((view, viewIndex) => {
                    const active = viewIndex === index;
                    view.classList.toggle("is-active", active);
                    view.setAttribute("aria-hidden", active ? "false" : "true");
                    view.toggleAttribute("inert", !active);
                });
                track.style.transform = `translateX(-${index * 100}%)`;
                setHeight(views[index]);
            };

            tabs.forEach((tab) => {
                tab.addEventListener("click", (event) => {
                    event.preventDefault();
                    setActive(tab.dataset.goalTab || "week");
                });
                tab.addEventListener("keydown", (event) => {
                    if (event.key === " ") {
                        event.preventDefault();
                        tab.click();
                        return;
                    }
                    if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
                    event.preventDefault();
                    const currentIndex = tabs.indexOf(tab);
                    const nextIndex = event.key === "Home"
                        ? 0
                        : event.key === "End"
                            ? tabs.length - 1
                            : (currentIndex + (event.key === "ArrowRight" ? 1 : -1) + tabs.length) % tabs.length;
                    setActive(tabs[nextIndex].dataset.goalTab || "week");
                    tabs[nextIndex].focus();
                });
            });

            const initial = tabs.find((tab) => tab.classList.contains("is-active"))?.dataset.goalTab || tabs[0].dataset.goalTab;
            root.dataset.goalEnhanced = "true";
            setActive(initial);
            window.addEventListener("resize", () => {
                const activeView = views.find((view) => view.classList.contains("is-active"));
                setHeight(activeView);
            });
        });
    }

    function initActionHubTabs() {
        document.querySelectorAll("[data-action-hub]").forEach((root) => {
            const tabs = Array.from(root.querySelectorAll("[data-action-tab]"));
            const views = Array.from(root.querySelectorAll("[data-action-view]"));
            const track = root.querySelector("[data-action-track]");
            const segmented = root.querySelector("[data-selected-tab]");
            if (!tabs.length || views.length < 2 || !track) return;
            segmented?.setAttribute("role", "tablist");
            tabs.forEach((tab) => tab.setAttribute("role", "tab"));
            views.forEach((view) => view.setAttribute("role", "tabpanel"));

            const setHeight = (view) => {
                if (!view) return;
                track.style.height = `${view.offsetHeight}px`;
            };

            const activate = (key) => {
                const index = Math.max(0, views.findIndex((view) => view.dataset.actionView === key));
                if (segmented) {
                    segmented.dataset.selectedTab = index === 1 ? "all" : "recommended";
                }
                tabs.forEach((tab, tabIndex) => {
                    const active = tabIndex === index;
                    tab.classList.toggle("is-active", active);
                    tab.setAttribute("aria-selected", active ? "true" : "false");
                    tab.tabIndex = active ? 0 : -1;
                });
                views.forEach((view, viewIndex) => {
                    const active = viewIndex === index;
                    view.classList.toggle("is-active", active);
                    view.setAttribute("aria-hidden", active ? "false" : "true");
                    view.toggleAttribute("inert", !active);
                });
                track.style.transform = `translateX(-${index * 100}%)`;
                setHeight(views[index]);
            };

            tabs.forEach((tab) => {
                tab.addEventListener("click", (event) => {
                    event.preventDefault();
                    activate(tab.dataset.actionTab || "recommended");
                });
                tab.addEventListener("keydown", (event) => {
                    if (event.key === " ") {
                        event.preventDefault();
                        tab.click();
                        return;
                    }
                    if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
                    event.preventDefault();
                    const currentIndex = tabs.indexOf(tab);
                    const nextIndex = event.key === "Home"
                        ? 0
                        : event.key === "End"
                            ? tabs.length - 1
                            : (currentIndex + (event.key === "ArrowRight" ? 1 : -1) + tabs.length) % tabs.length;
                    activate(tabs[nextIndex].dataset.actionTab || "recommended");
                    tabs[nextIndex].focus();
                });
            });

            const initial = tabs.find((tab) => tab.classList.contains("is-active"))?.dataset.actionTab || tabs[0].dataset.actionTab;
            root.dataset.actionEnhanced = "true";
            activate(initial);
            window.addEventListener("resize", () => {
                const activeView = views.find((view) => view.classList.contains("is-active"));
                setHeight(activeView);
            });
            if ("ResizeObserver" in window) {
                const observer = new ResizeObserver(() => {
                    const activeView = views.find((view) => view.classList.contains("is-active"));
                    setHeight(activeView);
                });
                views.forEach((view) => observer.observe(view));
                root._actionHubResizeObserver = observer;
            }
        });
    }

    function initCountdowns() {
        const cards = Array.from(document.querySelectorAll('[data-countdown-enabled="true"]'));
        if (!cards.length) return;

        const update = () => {
            const now = Date.now();
            cards.forEach((card) => {
                const target = Date.parse(card.dataset.countdownTarget || card.dataset.upcomingTarget || "");
                if (Number.isNaN(target)) return;
                card.dataset.countdownReady = "true";
                const diff = target - now;
                const output = card.querySelector("[data-countdown-output]");
                if (output) output.textContent = formatCountdown(diff);
                const meta = card.querySelector("#recommendedUpcomingRelative");
                if (meta) meta.textContent = describeCountdown(diff);
                const timer = card.querySelector("#recommendedUpcomingTimer");
                if (timer) timer.textContent = formatCountdown(diff);
            });
        };

        update();
        window.setInterval(update, 1000);
    }

    function buildWeekPreviewButton(item, fallbackLabel) {
        const el = document.createElement(item.href ? "a" : "button");
        if (item.href) {
            el.href = item.href;
        } else {
            el.type = "button";
        }
        el.className = "cd-week-preview-item";

        const title = document.createElement("span");
        title.className = "cd-week-preview-item__title";
        title.textContent = item.title || fallbackLabel;

        const meta = document.createElement("span");
        meta.className = "cd-week-preview-item__meta";
        meta.textContent = item.kind === "workout" ? "Workout" : "Task";

        const content = document.createElement("span");
        content.className = "cd-week-preview-item__content";
        content.append(title, meta);

        el.append(content);
        return el;
    }

    function initWeekPreview() {
        const strip = document.querySelector("[data-week-strip]");
        const panel = document.querySelector("[data-week-preview-panel]");
        if (!strip || !panel) return;
        strip.setAttribute("role", "tablist");

        const buttons = Array.from(strip.querySelectorAll("[data-week-day-button]"));
        panel.id = "dashboard-week-preview";
        panel.setAttribute("role", "tabpanel");
        buttons.forEach((button, index) => {
            button.setAttribute("role", "tab");
            button.id = `dashboard-week-tab-${index}`;
            button.setAttribute("aria-controls", panel.id);
        });
        const title = document.getElementById("weekSelectedTitle");
        const summary = document.getElementById("weekSelectedSummary");
        const link = document.getElementById("weekOpenDayLink");
        const taskList = document.getElementById("weekTaskList");
        const workoutList = document.getElementById("weekWorkoutList");
        let dragged = false;

        const setList = (container, items, emptyLabel) => {
            if (!container) return;
            container.innerHTML = "";
            if (!items.length) {
                const empty = document.createElement("span");
                empty.className = "cd-empty-note";
                empty.textContent = emptyLabel;
                container.appendChild(empty);
                return;
            }
            items.forEach((item) => container.appendChild(buildWeekPreviewButton(item, emptyLabel)));
        };

        const parseItems = (button, selector) => {
            return Array.from(button.querySelectorAll(`${selector} [data-week-item]`)).map((node) => ({
                title: node.dataset.title || "",
                href: node.dataset.href || "",
                kind: node.dataset.kind || "task"
            }));
        };

        const activate = (button, scrollIntoView) => {
            if (!button) return;
            buttons.forEach((candidate) => {
                const active = candidate === button;
                candidate.classList.toggle("is-active", active);
                candidate.setAttribute("aria-selected", active ? "true" : "false");
                candidate.tabIndex = active ? 0 : -1;
            });

            panel.classList.add("is-updating");
            panel.setAttribute("aria-labelledby", button.id);
            const tasks = parseItems(button, "[data-week-day-tasks]");
            const workouts = parseItems(button, "[data-week-day-workouts]");
            const dateLabel = button.dataset.dayLabel || "Selected day";
            const taskCount = Number(button.dataset.taskCount || "0");
            const workoutCount = Number(button.dataset.workoutCount || "0");
            const path = button.dataset.dayPath || "/calendar";

            window.setTimeout(() => {
                if (title) title.textContent = dateLabel;
                if (summary) summary.textContent = `${taskCount} tasks, ${workoutCount} workouts`;
                if (link) link.href = path;
                panel.dataset.openHref = path;
                setList(taskList, tasks, "No tasks selected");
                setList(workoutList, workouts, "No workouts selected");
                panel.classList.remove("is-updating");
                strip.closest(".cd-week-panel").dataset.weekEnhanced = "true";
            }, prefersReducedMotion() ? 0 : 140);

            if (scrollIntoView) {
                button.scrollIntoView({ behavior: prefersReducedMotion() ? "auto" : "smooth", inline: "center", block: "nearest" });
            }
        };

        buttons.forEach((button) => {
            button.addEventListener("click", (event) => {
                event.preventDefault();
                if (dragged) {
                    return;
                }
                activate(button, false);
            });
            button.addEventListener("keydown", (event) => {
                if (event.key === " ") {
                    event.preventDefault();
                    button.click();
                    return;
                }
                if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
                event.preventDefault();
                const index = buttons.indexOf(button);
                const next = event.key === "Home" ? 0 : event.key === "End" ? buttons.length - 1
                    : (index + (event.key === "ArrowRight" ? 1 : -1) + buttons.length) % buttons.length;
                activate(buttons[next], true);
                buttons[next].focus();
            });
        });

        panel.addEventListener("click", (event) => {
            if (event.target.closest("a,button")) return;
            const href = panel.dataset.openHref;
            if (href) window.location.href = href;
        });

        panel.addEventListener("dragstart", (event) => event.preventDefault());
        attachDragScroll(strip, {
            onDragStateChange(value) {
                dragged = value;
            }
        });

        activate(buttons.find((button) => button.dataset.daySelected === "true") || buttons[0], false);
    }

    function initTrainerTabs() {
        document.querySelectorAll("[data-trainer-tab]").forEach((button) => {
            button.addEventListener("click", () => {
                const target = button.dataset.trainerTab;
                const scope = button.closest(".cd-trainer-stage");
                if (!scope) return;
                scope.querySelectorAll("[data-trainer-tab]").forEach((candidate) => {
                    const active = candidate === button;
                    candidate.classList.toggle("is-active", active);
                    candidate.setAttribute("aria-selected", active ? "true" : "false");
                });
                scope.querySelectorAll("[data-trainer-view]").forEach((panel) => {
                    panel.classList.toggle("is-active", panel.dataset.trainerView === target);
                });
            });
        });
    }

    function renderTrainerActivityItem(item) {
        const article = document.createElement("article");
        article.className = `cd-activity-item${item.unread ? " is-unread" : ""}`;
        article.dataset.notificationId = item.notificationId || "";
        if (item.href) article.dataset.notificationHref = item.href;

        const mainTag = item.href ? document.createElement("a") : document.createElement("div");
        if (item.href) mainTag.href = item.href;
        mainTag.className = "cd-activity-item__main";

        const icon = document.createElement("span");
        icon.className = "cd-activity-item__icon";
        icon.dataset.kind = item.icon || "coach";

        const body = document.createElement("span");
        body.className = "cd-activity-item__body";

        const title = document.createElement("span");
        title.className = "cd-activity-item__title";
        title.textContent = item.title || "Trainer update";

        const copy = document.createElement("span");
        copy.className = "cd-activity-item__copy";
        copy.textContent = item.body || "";

        const meta = document.createElement("span");
        meta.className = "cd-activity-item__meta";
        meta.textContent = item.meta || "";

        body.append(title, copy, meta);
        mainTag.append(icon, body);
        article.appendChild(mainTag);

        if (item.canMarkRead || item.canDismiss) {
            const actions = document.createElement("div");
            actions.className = "cd-activity-item__actions";
            if (item.canMarkRead) {
                const read = document.createElement("button");
                read.type = "button";
                read.className = "cd-activity-item__action cd-activity-item__action--check";
                read.dataset.activityRead = "true";
                read.setAttribute("aria-label", "Mark trainer notification as read");
                read.innerHTML = "<svg viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.8' aria-hidden='true'><path d='M6 12.5 10 16 18 8'/></svg>";
                actions.appendChild(read);
            }
            if (item.canDismiss) {
                const dismiss = document.createElement("button");
                dismiss.type = "button";
                dismiss.className = "cd-activity-item__action";
                dismiss.dataset.activityDismiss = "true";
                dismiss.setAttribute("aria-label", "Dismiss trainer notification");
                dismiss.innerHTML = "<svg viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.8' aria-hidden='true'><path d='M8 8 16 16M16 8l-8 8'/></svg>";
                actions.appendChild(dismiss);
            }
            article.appendChild(actions);
        }

        return article;
    }

    function initTrainerActivity() {
        document.querySelectorAll("[data-trainer-activity-feed]").forEach((feed) => {
            const refresh = async () => {
                try {
                    const res = await fetch("/dashboard/trainer-activity", { headers: { "X-Requested-With": "XMLHttpRequest" } });
                    if (!res.ok) return;
                    const items = await res.json();
                    if (!Array.isArray(items)) return;
                    feed.innerHTML = "";
                    if (!items.length) {
                        feed.innerHTML = "<div class='cd-empty-state cd-empty-state--subtle'><p class='cd-empty-state__title'>No trainer notifications yet</p><p class='cd-empty-state__copy'>When your coach sends messages or coaching updates, they will appear here with quick actions.</p></div>";
                        return;
                    }
                    items.forEach((item) => feed.appendChild(renderTrainerActivityItem(item)));
                } catch (_) {
                    // ignore refresh failure
                }
            };

            feed.addEventListener("click", async (event) => {
                const readButton = event.target.closest("[data-activity-read]");
                const dismissButton = event.target.closest("[data-activity-dismiss]");
                const item = event.target.closest(".cd-activity-item");
                if (!item) return;
                const id = item.dataset.notificationId;
                if (!id) return;

                if (readButton || dismissButton) {
                    event.preventDefault();
                    const action = dismissButton ? "dismiss" : "read";
                    const res = await fetch(`/api/notifications/${id}/${action}`, {
                        method: "POST",
                        headers: getCsrfHeaders()
                    });
                    if (!res.ok) return;

                    if (dismissButton) {
                        item.classList.add("is-leaving");
                        window.setTimeout(() => item.remove(), 220);
                    } else {
                        item.classList.remove("is-unread");
                    }
                    emitNotificationUpdate({ source: "dashboard-trainer-feed", notificationId: id, action });
                    return;
                }

                const main = event.target.closest(".cd-activity-item__main");
                if (!main || !item.classList.contains("is-unread")) return;
                fetch(`/api/notifications/${id}/read`, {
                    method: "POST",
                    headers: getCsrfHeaders(),
                    keepalive: true
                }).catch(() => undefined);
                item.classList.remove("is-unread");
                emitNotificationUpdate({ source: "dashboard-trainer-feed", notificationId: id, action: "read" });
            });

            onNotificationUpdate(() => {
                window.clearTimeout(feed._refreshTimer);
                feed._refreshTimer = window.setTimeout(refresh, 120);
            });
        });
    }

    function renderTrainerMessage(message) {
        const node = document.createElement("div");
        node.className = `cd-trainer-message${message.mine ? " is-mine" : ""}`;
        node.dataset.messageId = message.id;

        const body = document.createElement("p");
        body.className = "cd-trainer-message__body";
        body.textContent = message.body || "";

        const meta = document.createElement("p");
        meta.className = "cd-trainer-message__meta";
        meta.textContent = message.createdLabel || "";

        node.append(body, meta);
        return node;
    }

    function initTrainerMessages() {
        document.querySelectorAll("[data-trainer-message-box]").forEach((box) => {
            const threadId = box.dataset.threadId;
            const list = box.querySelector("[data-trainer-message-list]");
            const form = box.querySelector("[data-trainer-message-form]");
            const input = box.querySelector("[data-trainer-message-input]");
            if (!threadId || !list) return;

            const prependMessages = (messages) => {
                const loadMore = list.querySelector("[data-trainer-load-more]");
                messages.forEach((message) => {
                    list.insertBefore(renderTrainerMessage(message), loadMore ? loadMore.nextSibling : list.firstChild);
                });
            };

            list.addEventListener("click", async (event) => {
                const trigger = event.target.closest("[data-trainer-load-more]");
                if (!trigger) return;
                const oldest = list.querySelector(".cd-trainer-message[data-message-id]");
                const beforeId = oldest?.dataset.messageId;
                const res = await fetch(`/dashboard/trainer-thread/${threadId}/messages?beforeId=${beforeId || ""}`, {
                    headers: { "X-Requested-With": "XMLHttpRequest" }
                });
                if (!res.ok) return;
                const payload = await res.json();
                if (!Array.isArray(payload.messages)) return;
                prependMessages(payload.messages);
                if (!payload.hasMore) trigger.remove();
            });

            form?.addEventListener("submit", async (event) => {
                event.preventDefault();
                const body = input?.value?.trim();
                if (!body) return;
                const headers = getCsrfHeaders("application/x-www-form-urlencoded;charset=UTF-8");
                const res = await fetch(`/dashboard/trainer-thread/${threadId}/messages`, {
                    method: "POST",
                    headers,
                    body: new URLSearchParams({ body }).toString()
                });
                if (!res.ok) return;
                const payload = await res.json();
                if (!payload.message) return;
                input.value = "";
                list.appendChild(renderTrainerMessage(payload.message));
                list.scrollTop = list.scrollHeight;
            });
        });
    }

    function initInfoPops() {
        document.querySelectorAll(".cd-info-pop").forEach((root) => {
            const trigger = root.querySelector("[data-info-trigger]");
            if (!trigger) return;
            trigger.addEventListener("click", (event) => {
                event.preventDefault();
                const open = !root.classList.contains("is-open");
                document.querySelectorAll(".cd-info-pop.is-open").forEach((node) => {
                    if (node !== root) {
                        node.classList.remove("is-open");
                        node.querySelector("[data-info-trigger]")?.setAttribute("aria-expanded", "false");
                    }
                });
                root.classList.toggle("is-open", open);
                trigger.setAttribute("aria-expanded", open ? "true" : "false");
            });
        });

        document.addEventListener("click", (event) => {
            document.querySelectorAll(".cd-info-pop.is-open").forEach((root) => {
                if (!root.contains(event.target)) {
                    root.classList.remove("is-open");
                    root.querySelector("[data-info-trigger]")?.setAttribute("aria-expanded", "false");
                }
            });
        });
    }

    const WEATHER_PERMISSION_COPY = "Please allow this site to access your location to use the weather feature.";
    const WEATHER_PERMISSION_PENDING_KEY = "one-to-one-weather-awaiting-permission";
    const WEATHER_PERMISSION_RELOADED_KEY = "one-to-one-weather-reloaded-after-grant";

    function resolvePeriod(date) {
        const hour = date.getHours();
        if (hour >= 5 && hour < 11) return "morning";
        if (hour >= 11 && hour < 16) return "midday";
        if (hour >= 16 && hour < 21) return "evening";
        return "night";
    }

    function weatherKey(code) {
        if ([0, 1].includes(code)) return "sunny";
        if ([2, 3, 45, 48].includes(code)) return "cloudy";
        if ([51, 53, 55, 61, 63, 65, 80, 81, 82].includes(code)) return "rain";
        if ([71, 73, 75, 77, 85, 86].includes(code)) return "snow";
        if ([95, 96, 99].includes(code)) return "storm";
        return "auto";
    }

    function weatherLabel(key) {
        return ({
            sunny: "Clear and bright",
            cloudy: "Mostly cloudy",
            rain: "Showers nearby",
            snow: "Cold and snowy",
            storm: "Storm risk active",
            auto: "Calm local conditions"
        })[key] || "Local conditions";
    }

    function weatherIcon(key, isDay) {
        if (key === "sunny") return isDay === false ? "🌙" : "☀️";
        if (key === "cloudy") return isDay === false ? "🌙☁️" : "⛅";
        if (key === "rain") return isDay === false ? "🌙☁️🌧️" : "🌧️";
        if (key === "snow") return isDay === false ? "🌙☁️❄️" : "🌨️";
        if (key === "storm") return isDay === false ? "🌙☁️⛈️" : "⛈️";
        return "•";
    }

    function toTemperatureValue(tempC, unit) {
        const numeric = Number(tempC || 0);
        if (unit === "FAHRENHEIT") {
            return Math.round((numeric * 9) / 5 + 32);
        }
        return Math.round(numeric);
    }

    function formatTemperature(tempC, unit) {
        return `${toTemperatureValue(tempC, unit)}\u00B0`;
    }

    function formatTimelineTime(date, isNow) {
        if (!(date instanceof Date) || Number.isNaN(date.getTime())) return "";
        if (isNow) return "Now";
        const timeDisplayFormat = getDashboardTimeDisplayFormat();
        const label = date.toLocaleTimeString("en-GB", {
            hour: "numeric",
            minute: "2-digit",
            hour12: timeDisplayFormat !== "TWENTY_FOUR_HOUR"
        }).replace(/\s+/g, " ").trim();
        return timeDisplayFormat === "TWENTY_FOUR_HOUR" ? label : label.toUpperCase();
    }

    function formatClockTime(date) {
        if (!(date instanceof Date) || Number.isNaN(date.getTime())) return "";
        const timeDisplayFormat = getDashboardTimeDisplayFormat();
        const label = date.toLocaleTimeString("en-GB", {
            hour: "numeric",
            minute: "2-digit",
            second: "2-digit",
            hour12: timeDisplayFormat !== "TWENTY_FOUR_HOUR"
        }).replace(/\s+/g, " ").trim();
        return timeDisplayFormat === "TWENTY_FOUR_HOUR" ? label : label.toUpperCase();
    }

    function buildLocationLabel(reverse) {
        const place = reverse?.results?.[0] || reverse || null;
        if (!place) return "";
        const primary = place.postcode
            || place.locality
            || place.city
            || place.name
            || place.principalSubdivision
            || place.admin2
            || place.admin1;
        const secondary = place.postcode
            ? (place.locality || place.city || place.principalSubdivision || place.admin1 || place.countryName)
            : (place.principalSubdivision || place.admin1 || place.countryName || place.country_code);
        return [primary, secondary]
            .filter((value, index, values) => value && values.indexOf(value) === index)
            .slice(0, 2)
            .join(", ");
    }

    function normalizeGeoError(error) {
        if (!error) return "unavailable";
        if (typeof error.code === "number" && error.code === 1) return "denied";
        return "unavailable";
    }

    async function getGeolocationPermissionStatus() {
        if (!navigator.permissions || typeof navigator.permissions.query !== "function") {
            return null;
        }
        try {
            return await navigator.permissions.query({ name: "geolocation" });
        } catch (_) {
            return null;
        }
    }

    function markPermissionPromptPending() {
        try {
            window.sessionStorage.setItem(WEATHER_PERMISSION_PENDING_KEY, "true");
        } catch (_) {
            // ignore storage issues
        }
    }

    function clearPermissionPromptFlags(clearReloadFlag) {
        try {
            window.sessionStorage.removeItem(WEATHER_PERMISSION_PENDING_KEY);
            if (clearReloadFlag) {
                window.sessionStorage.removeItem(WEATHER_PERMISSION_RELOADED_KEY);
            }
        } catch (_) {
            // ignore storage issues
        }
    }

    function shouldReloadAfterGrant() {
        try {
            return window.sessionStorage.getItem(WEATHER_PERMISSION_PENDING_KEY) === "true"
                && window.sessionStorage.getItem(WEATHER_PERMISSION_RELOADED_KEY) !== "true";
        } catch (_) {
            return false;
        }
    }

    function reloadAfterGrant() {
        try {
            window.sessionStorage.setItem(WEATHER_PERMISSION_RELOADED_KEY, "true");
        } catch (_) {
            // ignore storage issues
        }
        window.location.reload();
    }

    async function fetchForecast() {
        if (!("geolocation" in navigator)) return { error: "unsupported" };
        const position = await new Promise((resolve, reject) => {
            navigator.geolocation.getCurrentPosition(resolve, reject, {
                enableHighAccuracy: false,
                timeout: 8000,
                maximumAge: 300000
            });
        }).catch((error) => ({ error }));
        if (!position || position.error) {
            return { error: normalizeGeoError(position?.error) };
        }

        const { latitude, longitude } = position.coords;
        const forecastUrl = `https://api.open-meteo.com/v1/forecast?latitude=${latitude}&longitude=${longitude}&current=temperature_2m,weather_code,is_day&hourly=temperature_2m,weather_code,precipitation_probability&forecast_days=2&timezone=auto`;
        const reverseUrl = `https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=${latitude}&longitude=${longitude}&localityLanguage=en`;
        const [forecastRes, reverseRes] = await Promise.all([
            fetch(forecastUrl),
            fetch(reverseUrl).catch(() => null)
        ]);
        if (!forecastRes.ok) return { error: "unavailable" };
        const forecast = await forecastRes.json();
        const reverse = reverseRes && reverseRes.ok ? await reverseRes.json() : null;
        return { forecast, reverse };
    }

    function computeTrendValue(weatherCode, precipitationProbability) {
        const key = weatherKey(Number(weatherCode || 0));
        const precipitation = Math.max(0, Math.min(100, Number(precipitationProbability || 0)));
        const baseline = ({
            sunny: 18,
            cloudy: 42,
            rain: 72,
            snow: 60,
            storm: 92,
            auto: 36
        })[key] || 36;
        return Math.min(100, Math.round((baseline * 0.7) + (precipitation * 0.3)));
    }

    function buildTimelineSlots(forecast, unit) {
        const currentTime = new Date(forecast?.current?.time || Date.now());
        const currentDayKey = `${currentTime.getFullYear()}-${currentTime.getMonth()}-${currentTime.getDate()}`;
        const hourlyTimes = Array.isArray(forecast?.hourly?.time) ? forecast.hourly.time : [];
        const hourlyTemps = Array.isArray(forecast?.hourly?.temperature_2m) ? forecast.hourly.temperature_2m : [];
        const hourlyCodes = Array.isArray(forecast?.hourly?.weather_code) ? forecast.hourly.weather_code : [];
        const hourlyPrecip = Array.isArray(forecast?.hourly?.precipitation_probability) ? forecast.hourly.precipitation_probability : [];
        const slots = [];

        const currentCode = Number(forecast?.current?.weather_code ?? 0);
        const currentKey = weatherKey(currentCode);
        slots.push({
            time: currentTime,
            label: "Now",
            tempValue: toTemperatureValue(forecast?.current?.temperature_2m, unit),
            tempDisplay: formatTemperature(forecast?.current?.temperature_2m, unit),
            key: currentKey,
            condition: weatherLabel(currentKey),
            icon: weatherIcon(currentKey, Boolean(forecast?.current?.is_day)),
            precipitationProbability: Number(hourlyPrecip[0] || 0),
            trendValue: computeTrendValue(currentCode, hourlyPrecip[0])
        });

        for (let i = 0; i < hourlyTimes.length && slots.length < 24; i += 1) {
            const time = new Date(hourlyTimes[i]);
            if (Number.isNaN(time.getTime()) || time <= currentTime) continue;
            const slotDayKey = `${time.getFullYear()}-${time.getMonth()}-${time.getDate()}`;
            if (slotDayKey !== currentDayKey) break;
            const code = Number(hourlyCodes[i] || 0);
            const key = weatherKey(code);
            slots.push({
                time,
                label: formatTimelineTime(time, false),
                tempValue: toTemperatureValue(hourlyTemps[i], unit),
                tempDisplay: formatTemperature(hourlyTemps[i], unit),
                key,
                condition: weatherLabel(key),
                icon: weatherIcon(key, time.getHours() >= 6 && time.getHours() < 19),
                precipitationProbability: Number(hourlyPrecip[i] || 0),
                trendValue: computeTrendValue(code, hourlyPrecip[i])
            });
        }

        return slots.slice(0, 24);
    }

    function buildOverviewSummary(slots) {
        if (!Array.isArray(slots) || !slots.length) return "Local forecast unavailable right now";
        const counts = slots.reduce((acc, slot) => {
            acc[slot.key] = (acc[slot.key] || 0) + 1;
            return acc;
        }, {});
        const laterSlots = slots.slice(Math.floor(slots.length / 2));
        const laterHasRain = laterSlots.some((slot) => slot.key === "rain" || slot.key === "storm");
        const laterHasSnow = laterSlots.some((slot) => slot.key === "snow");
        const rainCount = (counts.rain || 0) + (counts.storm || 0);
        const cloudCount = counts.cloudy || 0;
        const sunnyCount = counts.sunny || 0;

        if ((counts.storm || 0) >= 2) return "Stormy spells are possible later";
        if (laterHasSnow && (counts.snow || 0) >= 2) return "Cold conditions with snow later on";
        if (laterHasRain && rainCount >= 3) return "Cloudy with rain developing later";
        if (rainCount >= 8) return "Showers expected through much of the next 24 hours";
        if (sunnyCount >= Math.max(cloudCount, rainCount) && sunnyCount >= 8) return "Sunny for most of the next 24 hours";
        if (cloudCount >= sunnyCount && sunnyCount >= 4) return "Mostly cloudy with occasional sun";
        if (cloudCount >= 8) return "Cloudy for much of the next 24 hours";
        if (rainCount >= 3) return "Light showers are expected later";
        return "Mixed local conditions across the next 24 hours";
    }

    function buildGraphPath(points) {
        if (!points.length) return "";
        if (points.length === 1) return `M ${points[0].x} ${points[0].y}`;
        let path = `M ${points[0].x} ${points[0].y}`;
        for (let i = 1; i < points.length; i += 1) {
            const previous = points[i - 1];
            const current = points[i];
            const midpointX = (previous.x + current.x) / 2;
            const midpointY = (previous.y + current.y) / 2;
            path += ` Q ${previous.x} ${previous.y} ${midpointX} ${midpointY}`;
        }
        const last = points[points.length - 1];
        path += ` T ${last.x} ${last.y}`;
        return path;
    }

    function createGraphPoints(slots, graphMode, slotWidth, chartHeight, topPadding) {
        const values = slots.map((slot) => graphMode === "trend" ? slot.trendValue : slot.tempValue);
        const max = Math.max(...values, 1);
        const min = Math.min(...values, 0);
        const usableHeight = chartHeight - topPadding - 18;
        return slots.map((slot, index) => {
            const value = graphMode === "trend" ? slot.trendValue : slot.tempValue;
            const normalized = (value - min) / Math.max(max - min, 1);
            return {
                ...slot,
                x: (index * slotWidth) + (slotWidth / 2),
                y: chartHeight - 12 - (normalized * usableHeight)
            };
        });
    }

    function attachDragScroll(strip, options = {}) {
        if (!strip || strip.dataset.dragBound === "true") return;
        strip.dataset.dragBound = "true";
        let dragged = false;
        let pointerId = null;
        let startX = 0;
        let scrollStart = 0;
        const onDragStateChange = typeof options.onDragStateChange === "function"
            ? options.onDragStateChange
            : null;

        const setDragged = (value) => {
            dragged = value;
            if (onDragStateChange) {
                onDragStateChange(value);
            }
        };

        strip.addEventListener("dragstart", (event) => event.preventDefault());

        strip.addEventListener("pointerdown", (event) => {
            pointerId = event.pointerId;
            startX = event.clientX;
            scrollStart = strip.scrollLeft;
            setDragged(false);
            strip.classList.add("dragging");
            strip.setPointerCapture(pointerId);
        });

        strip.addEventListener("pointermove", (event) => {
            if (pointerId !== event.pointerId) return;
            const delta = event.clientX - startX;
            if (Math.abs(delta) > 6) setDragged(true);
            if (!dragged) return;
            strip.scrollLeft = scrollStart - delta;
        });

        const releasePointer = (event) => {
            if (pointerId !== event.pointerId) return;
            strip.classList.remove("dragging");
            try {
                strip.releasePointerCapture(pointerId);
            } catch (_) {
                // ignore capture issues
            }
            window.setTimeout(() => {
                setDragged(false);
            }, 0);
            pointerId = null;
        };

        strip.addEventListener("pointerup", releasePointer);
        strip.addEventListener("pointercancel", releasePointer);
        strip.addEventListener("pointerleave", releasePointer);
    }

    function initAmbienceCard() {
        const root = document.querySelector("[data-dashboard-ambience]");
        const page = document.querySelector("[data-dashboard-role='CLIENT']");
        if (!root || !page) return;

        const toggle = root.querySelector("[data-ambience-toggle]");
        const icon = root.querySelector("[data-ambience-icon]");
        const periodEl = root.querySelector("[data-ambience-period]");
        const clockEl = root.querySelector("[data-ambience-clock]");
        const weatherEl = root.querySelector("[data-ambience-weather-label]");
        const summaryEl = root.querySelector("[data-ambience-summary]");
        const locationEl = root.querySelector("[data-ambience-location]");
        const locationMetaEl = root.querySelector("[data-ambience-location-meta]");
        const locationIndicatorEl = root.querySelector("[data-ambience-location-indicator]");
        const permissionEl = root.querySelector("[data-ambience-permission-message]");
        const permissionCopyEl = root.querySelector("[data-ambience-permission-copy]");
        const locationRequestEl = root.querySelector("[data-ambience-location-request]");
        const graphToggleEl = root.querySelector("[data-ambience-graph-toggle]");
        const timelineWrapEl = root.querySelector("[data-ambience-timeline-wrap]");
        const timelineEl = root.querySelector("[data-ambience-timeline]");
        const temperatureUnit = (root.dataset.temperatureUnit || page.dataset.dashboardTemperatureUnit || "CELSIUS").toUpperCase();
        const displayMode = (root.dataset.displayMode || page.dataset.dashboardWeatherDisplayMode || "VISUAL").toUpperCase();
        let currentWeather = page.dataset.dashboardWeather || "auto";
        let currentGraphMode = "temperature";
        let currentSlots = [];

        const setGraphToggleState = (mode) => {
            if (!graphToggleEl) return;
            const selectedMode = mode === "trend" ? "trend" : "temperature";
            graphToggleEl.dataset.selectedMode = selectedMode;
            graphToggleEl.querySelectorAll("[data-graph-mode]").forEach((candidate) => {
                const active = (candidate.dataset.graphMode || "temperature") === selectedMode;
                candidate.classList.toggle("is-active", active);
                candidate.setAttribute("aria-pressed", active ? "true" : "false");
            });
        };

        const setCompactState = (compact) => {
            root.dataset.compact = compact ? "true" : "false";
        };

        const applyTheme = () => {
            const now = new Date();
            const period = resolvePeriod(now);
            page.dataset.dashboardPeriod = period;
            if (icon) {
                icon.dataset.period = period;
                icon.dataset.weather = currentWeather;
            }
            if (periodEl) periodEl.textContent = period.charAt(0).toUpperCase() + period.slice(1);
            if (clockEl) {
                clockEl.textContent = formatClockTime(now);
            }
        };

        const savePreference = async () => {
            if (!toggle) return;
            await fetch("/dashboard/ambience-preference", {
                method: "POST",
                headers: getCsrfHeaders("application/x-www-form-urlencoded;charset=UTF-8"),
                body: new URLSearchParams({
                    enabled: toggle.getAttribute("aria-checked") === "true" ? "true" : "false",
                    weather: currentWeather || "auto"
                }).toString()
            }).catch(() => undefined);
        };

        const renderStatusOnly = (weatherCopy, summaryCopy, showPermission, canRequestLocation = showPermission) => {
            currentWeather = "auto";
            page.dataset.dashboardWeather = currentWeather;
            setCompactState(true);
            if (weatherEl) weatherEl.textContent = weatherCopy;
            if (summaryEl) summaryEl.textContent = summaryCopy;
            if (locationIndicatorEl) locationIndicatorEl.hidden = true;
            if (locationMetaEl) locationMetaEl.hidden = true;
            if (graphToggleEl) graphToggleEl.hidden = true;
            if (timelineWrapEl) timelineWrapEl.hidden = true;
            if (timelineEl) timelineEl.innerHTML = "";
            if (permissionEl) {
                permissionEl.hidden = !showPermission;
            }
            if (permissionCopyEl) {
                permissionCopyEl.textContent = WEATHER_PERMISSION_COPY;
            }
            if (locationRequestEl) {
                locationRequestEl.hidden = !showPermission || !canRequestLocation;
            }
            applyTheme();
        };

        const renderVisualTimeline = (slots) => {
            if (!timelineEl) return;
            timelineEl.innerHTML = "";
            const strip = document.createElement("div");
            strip.className = "cd-ambience-timeline__strip";
            slots.forEach((slot) => {
                const item = document.createElement("article");
                item.className = "cd-ambience-timeline__item";
                item.dataset.weather = slot.key;

                const time = document.createElement("p");
                time.className = "cd-ambience-timeline__time";
                time.textContent = slot.label;

                const glyph = document.createElement("div");
                glyph.className = "cd-ambience-timeline__glyph";
                glyph.textContent = slot.icon;

                const temp = document.createElement("p");
                temp.className = "cd-ambience-timeline__temp";
                temp.textContent = slot.tempDisplay;

                const condition = document.createElement("p");
                condition.className = "cd-ambience-timeline__condition";
                condition.textContent = slot.condition;

                item.append(time, glyph, temp, condition);
                strip.appendChild(item);
            });
            timelineEl.appendChild(strip);
            attachDragScroll(strip);
        };

        const renderGraphTimeline = (slots) => {
            if (!timelineEl) return;
            timelineEl.innerHTML = "";
            const slotWidth = 78;
            const chartHeight = 138;
            const topPadding = 14;
            const points = createGraphPoints(slots, currentGraphMode, slotWidth, chartHeight, topPadding);
            const graph = document.createElement("div");
            graph.className = "cd-ambience-graph";
            graph.dataset.graphMode = currentGraphMode;

            const scroller = document.createElement("div");
            scroller.className = "cd-ambience-graph__scroller";

            const inner = document.createElement("div");
            inner.className = "cd-ambience-graph__inner";
            inner.style.width = `${points.length * slotWidth}px`;

            const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
            svg.setAttribute("viewBox", `0 0 ${points.length * slotWidth} ${chartHeight}`);
            svg.setAttribute("class", "cd-ambience-graph__svg");
            svg.setAttribute("preserveAspectRatio", "none");

            const path = document.createElementNS("http://www.w3.org/2000/svg", "path");
            path.setAttribute("class", "cd-ambience-graph__line");
            path.setAttribute("d", buildGraphPath(points));
            svg.appendChild(path);

            points.forEach((point, index) => {
                const circle = document.createElementNS("http://www.w3.org/2000/svg", "circle");
                circle.setAttribute("class", "cd-ambience-graph__dot");
                circle.setAttribute("cx", String(point.x));
                circle.setAttribute("cy", String(point.y));
                circle.setAttribute("r", "4");
                circle.style.setProperty("--dot-delay", `${index * 32}ms`);
                svg.appendChild(circle);
            });

            const columns = document.createElement("div");
            columns.className = "cd-ambience-graph__columns";
            points.forEach((point) => {
                const column = document.createElement("article");
                column.className = "cd-ambience-graph__column";
                column.dataset.weather = point.key;

                const time = document.createElement("p");
                time.className = "cd-ambience-graph__time";
                time.textContent = point.label;

                const spacer = document.createElement("div");
                spacer.className = "cd-ambience-graph__spacer";

                const temp = document.createElement("p");
                temp.className = "cd-ambience-graph__temp";
                temp.textContent = currentGraphMode === "trend"
                    ? `${point.trendValue}%`
                    : point.tempDisplay;

                const meta = document.createElement("p");
                meta.className = "cd-ambience-graph__meta";
                meta.textContent = currentGraphMode === "trend"
                    ? `${point.precipitationProbability}% rain chance`
                    : point.condition;

                column.append(time, spacer, temp, meta);
                columns.appendChild(column);
            });

            inner.append(svg, columns);
            scroller.appendChild(inner);
            graph.appendChild(scroller);
            timelineEl.appendChild(graph);
            attachDragScroll(scroller);

            window.requestAnimationFrame(() => {
                try {
                    const length = path.getTotalLength();
                    path.style.strokeDasharray = `${length}`;
                    path.style.strokeDashoffset = `${length}`;
                    path.classList.add("is-visible");
                } catch (_) {
                    path.classList.add("is-visible");
                }
            });
        };

        const renderTimeline = () => {
            const showGraph = displayMode === "GRAPH";
            if (permissionEl) permissionEl.hidden = true;
            if (timelineWrapEl) timelineWrapEl.hidden = false;
            if (graphToggleEl) graphToggleEl.hidden = !showGraph || !currentSlots.length;
            if (timelineEl) {
                timelineEl.dataset.viewMode = showGraph ? "graph" : "visual";
            }
            if (!currentSlots.length) {
                if (timelineEl) timelineEl.innerHTML = "";
                return;
            }
            if (showGraph) {
                setGraphToggleState(currentGraphMode);
                renderGraphTimeline(currentSlots);
                return;
            }
            setGraphToggleState("temperature");
            renderVisualTimeline(currentSlots);
        };

        graphToggleEl?.querySelectorAll("[data-graph-mode]").forEach((button) => {
            button.addEventListener("click", () => {
                if (displayMode !== "GRAPH") return;
                if (timelineEl) {
                    timelineEl.classList.add("is-transitioning");
                }
                currentGraphMode = button.dataset.graphMode || "temperature";
                setGraphToggleState(currentGraphMode);
                window.setTimeout(() => {
                    renderTimeline();
                    window.requestAnimationFrame(() => {
                        timelineEl?.classList.remove("is-transitioning");
                    });
                }, 120);
            });
        });

        const loadForecast = async (allowPrompt = false) => {
            const permissionStatus = await getGeolocationPermissionStatus();
            if (permissionStatus?.state === "denied") {
                renderStatusOnly(
                    WEATHER_PERMISSION_COPY,
                    "Location access is required before the 24 hour outlook can load.",
                    true,
                    false
                );
                return;
            }

            if (permissionStatus?.state !== "granted" && !allowPrompt) {
                renderStatusOnly(
                    "Local weather is off",
                    "Enable local weather when you want a location-aware 24 hour outlook.",
                    true,
                    true
                );
                return;
            }

            if (permissionStatus?.state === "prompt" || permissionStatus === null) {
                markPermissionPromptPending();
            }

            permissionStatus?.addEventListener?.("change", () => {
                if (permissionStatus.state === "granted" && shouldReloadAfterGrant()) {
                    reloadAfterGrant();
                    return;
                }
                if (permissionStatus.state === "denied") {
                    renderStatusOnly(
                        WEATHER_PERMISSION_COPY,
                        "Location access is required before the 24 hour outlook can load.",
                        true,
                        false
                    );
                }
            });

            try {
                const payload = await fetchForecast();
                if (payload?.error === "denied") {
                    renderStatusOnly(
                        WEATHER_PERMISSION_COPY,
                        "Location access is required before the 24 hour outlook can load.",
                        true
                    );
                    return;
                }
                if (payload?.error) {
                    renderStatusOnly(
                        "Local forecast unavailable right now",
                        "We could not load the next 24 hours for your local area.",
                        false
                    );
                    return;
                }

                if (permissionStatus?.state === "prompt" && shouldReloadAfterGrant()) {
                    reloadAfterGrant();
                    return;
                }

                clearPermissionPromptFlags(true);
                const { forecast, reverse } = payload;
                currentSlots = buildTimelineSlots(forecast, temperatureUnit);
                setCompactState(!currentSlots.length);
                currentWeather = currentSlots[0]?.key || weatherKey(Number(forecast?.current?.weather_code ?? -1));
                page.dataset.dashboardWeather = currentWeather;
                if (weatherEl) weatherEl.textContent = weatherLabel(currentWeather);
                if (summaryEl) summaryEl.textContent = buildOverviewSummary(currentSlots);
                const locationLabel = buildLocationLabel(reverse);
                if (locationEl) locationEl.textContent = locationLabel || "Local area";
                if (locationMetaEl) locationMetaEl.hidden = !locationLabel;
                if (locationIndicatorEl) locationIndicatorEl.hidden = false;
                renderTimeline();
                applyTheme();
            } catch (_) {
                renderStatusOnly(
                    "Local forecast unavailable right now",
                    "We could not load the next 24 hours for your local area.",
                    false
                );
            }
        };

        toggle?.addEventListener("click", async () => {
            const next = toggle.getAttribute("aria-checked") !== "true";
            toggle.setAttribute("aria-checked", next ? "true" : "false");
            page.dataset.dashboardImmersive = next ? "true" : "false";
            await savePreference();
        });
        locationRequestEl?.addEventListener("click", () => loadForecast(true));

        applyTheme();
        setGraphToggleState(currentGraphMode);
        loadForecast(false);
        window.setInterval(applyTheme, 1000);
    }

    function init() {
        const dashboardRoot = getDashboardRoot();
        if (dashboardRoot) {
            document.body.classList.add("cd-dashboard-shell-active");
        }
        const mobileDock = document.querySelector(".cd-dashboard-mobile-dock");
        const syncDockReservation = () => {
            if (!mobileDock) return;
            const styles = window.getComputedStyle(mobileDock);
            const height = styles.display === "none" ? 0 : Math.ceil(mobileDock.getBoundingClientRect().height);
            const gap = window.matchMedia("(max-width: 640px)").matches ? 9 : 12;
            document.body.style.setProperty("--shell-local-dock-height", height > 0 ? `${height + gap}px` : "0px");
        };
        syncDockReservation();
        if (mobileDock && "ResizeObserver" in window) {
            new ResizeObserver(syncDockReservation).observe(mobileDock);
        }
        window.addEventListener("resize", syncDockReservation, { passive: true });
        initDashboardShell();
        initCardRevealAnimations();
        initGoalSlider();
        initActionHubTabs();
        initCountdowns();
        initWeekPreview();
        initTrainerTabs();
        initTrainerActivity();
        initTrainerMessages();
        initInfoPops();
        initAmbienceCard();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
}());
