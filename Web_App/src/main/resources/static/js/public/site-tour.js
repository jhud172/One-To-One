(function () {
    "use strict";

    var root = document.getElementById("siteTour");
    if (!root) {
        return;
    }

    var STORAGE_KEY = "onetoone.site-tour.v2";
    var reducedMotion = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    var roleMarker = root.querySelector("[data-site-tour-role]");
    var role = roleMarker ? roleMarker.getAttribute("data-site-tour-role") : "CLIENT";
    var previousFocus = null;
    var currentTarget = null;
    var positionFrame = null;
    var navigating = false;
    var memoryState = null;

    var spotlight = document.getElementById("siteTourSpotlight");
    var bubble = document.getElementById("siteTourBubble");
    var connectorPath = document.getElementById("siteTourConnectorPath");
    var connectorDot = document.getElementById("siteTourConnectorDot");
    var progress = document.getElementById("siteTourProgress");
    var stepLabel = document.getElementById("siteTourStep");
    var pageLabel = document.getElementById("siteTourPage");
    var title = document.getElementById("siteTourTitle");
    var copy = document.getElementById("siteTourCopy");
    var hint = document.getElementById("siteTourHint");
    var backButton = document.getElementById("siteTourBack");
    var nextButton = document.getElementById("siteTourNext");
    var skipButton = document.getElementById("siteTourSkip");
    var completeForm = document.getElementById("siteTourCompleteForm");
    var announcement = document.getElementById("siteTourAnnouncement");

    if (!spotlight || !bubble || !connectorPath || !connectorDot || !progress || !stepLabel || !pageLabel ||
        !title || !copy || !hint || !backButton || !nextButton || !skipButton || !completeForm || !announcement) {
        return;
    }

    var journeys = {
        CLIENT: [
            {
                route: "/dashboard",
                page: "Dashboard",
                target: "[data-site-tour='client-dashboard'], #dashboardRoot",
                title: "Your day starts here",
                copy: "Use the dashboard to see today's priorities, your trainer relationship, goals and recent progress without hunting through menus.",
                hint: "Start here whenever you are unsure what needs your attention next."
            },
            {
                route: "/calendar?view=month",
                page: "Calendar",
                target: "[data-site-tour='calendar-controls'], .calendar-view-toggle",
                title: "Plan training around real life",
                copy: "Switch between month, week and day views, open a date, and keep workouts and tasks attached to the days when you will complete them.",
                hint: "Use Schedules for repeating training and Jump controls to find upcoming work quickly."
            },
            {
                route: "/client/trainers",
                page: "Trainers",
                target: "[data-site-tour='client-trainers-search'], #trainerSearch",
                title: "Find the right trainer",
                copy: "Search verified trainers here and send one connection request. Your active trainer relationship and its status remain visible on this page.",
                hint: "One active trainer keeps coaching ownership and payments clear."
            },
            {
                route: "/chat",
                page: "Charlie",
                target: "[data-site-tour='charlie'], #coachChat",
                title: "Ask Charlie for practical help",
                copy: "Use the coaching assistant for explanations, planning ideas and guidance based on the training context available to your account.",
                hint: "Start a new chat for a separate topic so useful answers stay easy to find."
            },
            {
                route: "/inbox",
                page: "Inbox",
                target: "[data-site-tour='inbox'], #inboxThreadList, main h1",
                title: "Keep coaching conversations together",
                copy: "Notifications and direct trainer conversations live here, so decisions about programmes and progress do not get separated from your account.",
                hint: "Unread badges in navigation show when a conversation needs attention."
            },
            {
                route: "/profile",
                page: "Profile",
                target: "[data-site-tour='profile'], .profile-identity-banner, main h1",
                title: "Make the account yours",
                copy: "Update your identity, accessibility, privacy and display preferences here. These settings shape the experience across the website.",
                hint: "You can replay this complete website tour from the account menu at any time."
            },
            {
                route: "/dashboard",
                page: "Ready",
                target: "[data-site-tour='client-dashboard'], #dashboardRoot",
                title: "You are ready to begin",
                copy: "Return to this dashboard, choose today's next action and let the calendar, trainer tools and progress records build one connected routine.",
                hint: "Select Finish tour to save completion and continue using One To One."
            }
        ],
        TRAINER: [
            {
                route: "/trainer/dashboard",
                page: "Trainer dashboard",
                target: "[data-site-tour='trainer-dashboard'], .dashboard-enhanced",
                title: "Run the day from one workspace",
                copy: "Your schedule, client workload and direct routes into programmes are collected here for a quick operational view.",
                hint: "Review today's work first, then move into the client or library area that needs attention."
            },
            {
                route: "/trainer/clients",
                page: "Clients",
                target: "[data-site-tour='trainer-clients'], #clientSearch, main h1",
                title: "Manage every coaching relationship",
                copy: "Accept requests, search current clients, open assessments and plans, pause relationships and start protected conversations from this page.",
                hint: "Client status determines which planning and messaging actions are available."
            },
            {
                route: "/trainer/library",
                page: "Library",
                target: "[data-site-tour='trainer-library'], main h1",
                title: "Build once, coach repeatedly",
                copy: "Create reusable exercises, workouts and programmes in the Library, then share or assign them to active clients.",
                hint: "Begin with exercises, combine them into workouts, then organise workouts into programmes."
            },
            {
                route: "/calendar?view=month",
                page: "Calendar",
                target: "[data-site-tour='calendar-controls'], #scheduleDrawerButton",
                title: "Coordinate the coaching week",
                copy: "Use Calendar to review tasks and sessions across dates, while Schedules handles repeating work and planned programme activity.",
                hint: "Day view is best when you need to act; month view is best when you need to plan."
            },
            {
                route: "/inbox",
                page: "Inbox",
                target: "[data-site-tour='inbox'], #inboxThreadList, main h1",
                title: "Keep client decisions traceable",
                copy: "Use the Inbox for programme questions, check-in follow-ups and ongoing coaching communication with linked clients.",
                hint: "Open a client from Clients when you need to message with their plan already in mind."
            },
            {
                route: "/profile",
                page: "Profile",
                target: "[data-site-tour='profile'], .profile-identity-banner, main h1",
                title: "Maintain a trusted coaching profile",
                copy: "Keep your public identity, trainer information, preferences and security details accurate so clients know who they are working with.",
                hint: "The account menu contains a Replay website tour action for future refreshers."
            },
            {
                route: "/trainer/dashboard",
                page: "Ready",
                target: "[data-site-tour='trainer-dashboard'], .dashboard-enhanced",
                title: "Your coaching workspace is ready",
                copy: "Use the dashboard as your daily checkpoint, Clients for individual work and Library for the reusable systems behind your coaching.",
                hint: "Select Finish tour to save completion and return to live work."
            }
        ],
        GYM: [
            {
                route: "/gym/dashboard",
                page: "Gym dashboard",
                target: "[data-site-tour='gym-dashboard'], .gym-dashboard",
                title: "See gym operations at a glance",
                copy: "The dashboard summarises the working week and provides direct routes to trainers, memberships, support and communication.",
                hint: "Use it as the first check for operational work each day."
            },
            {
                route: "/gym/admin/trainers",
                page: "Trainers",
                target: "[data-site-tour='gym-trainers'], main form, main h1",
                title: "Invite and verify trainers",
                copy: "Create trainer access securely, submit verification details and follow approval status from this operational page.",
                hint: "Temporary credentials should only be shared with the intended trainer through a secure channel."
            },
            {
                route: "/gym/admin/memberships",
                page: "Memberships",
                target: "[data-site-tour='gym-memberships'], main h1",
                title: "Control membership products",
                copy: "Create offerings, review pricing and subscriber counts, and manage which membership products are currently active.",
                hint: "Check the impact on existing subscribers before changing a live price."
            },
            {
                route: "/inbox",
                page: "Inbox",
                target: "[data-site-tour='inbox'], #inboxThreadList, main h1",
                title: "Keep operational messages visible",
                copy: "Use the Inbox for trainer and platform conversations that need a clear account-level record.",
                hint: "Notifications appear above conversations so urgent updates are not buried."
            },
            {
                route: "/profile",
                page: "Gym profile",
                target: "[data-site-tour='profile'], .profile-identity-banner, main h1",
                title: "Keep gym information dependable",
                copy: "Maintain the account identity, contact details, accessibility preferences and security settings used across the gym workspace.",
                hint: "Replay the website tour later from the account menu whenever staff need a refresher."
            },
            {
                route: "/gym/dashboard",
                page: "Ready",
                target: "[data-site-tour='gym-dashboard'], .gym-dashboard",
                title: "Your gym workspace is ready",
                copy: "Return here for the daily overview, then move into Trainers or Memberships to complete operational work.",
                hint: "Select Finish tour to save completion."
            }
        ],
        ADMIN: [
            {
                route: "/admin/dashboard",
                page: "Operations",
                target: "[data-site-tour='admin-dashboard'], main h1",
                title: "Start with platform priorities",
                copy: "The operations dashboard brings support, gym applications, commerce and controlled platform tools into one accountable workspace.",
                hint: "Use the summary counts to decide which queue needs attention first."
            },
            {
                route: "/admin/gym-applications",
                page: "Gym applications",
                target: "[data-site-tour='admin-gym-applications'], main h1",
                title: "Review gym access carefully",
                copy: "Open applications, verify submitted business details and move each request through follow-up, approval or decline.",
                hint: "Record follow-up in the application so the decision remains auditable."
            },
            {
                route: "/admin/feedback",
                page: "Support inbox",
                target: "[data-site-tour='admin-feedback'], main h1",
                title: "Turn feedback into tracked work",
                copy: "Review submissions, update their status and send responses when the requester has allowed email follow-up.",
                hint: "Viewed state is not resolution—use the status field to show the actual outcome."
            },
            {
                route: "/admin/merch",
                page: "Merchandise",
                target: "[data-site-tour='admin-merch'], main h1",
                title: "Keep the storefront accurate",
                copy: "Manage products, stock, visibility and pricing here so the public shop reflects what the platform can actually fulfil.",
                hint: "Check current orders and stock before deactivating or editing a live product."
            },
            {
                route: "/admin/dashboard",
                page: "Ready",
                target: "[data-site-tour='admin-dashboard'], main h1",
                title: "Platform operations are ready",
                copy: "Return to this dashboard, work from the highest-priority queue and keep decisions inside the relevant administration area.",
                hint: "Select Finish tour to save completion and continue."
            }
        ]
    };

    var steps = journeys[role] || journeys.CLIENT;

    function readState() {
        try {
            var parsed = JSON.parse(window.sessionStorage.getItem(STORAGE_KEY));
            return parsed && parsed.active && parsed.role === role ? parsed : null;
        } catch (error) {
            return memoryState;
        }
    }

    function writeState(index) {
        var nextState = { active: true, role: role, index: index, version: 2 };
        memoryState = nextState;
        try {
            window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(nextState));
        } catch (error) {
            // The current page remains usable when browser storage is blocked.
        }
        return nextState;
    }

    function clearState() {
        memoryState = null;
        try {
            window.sessionStorage.removeItem(STORAGE_KEY);
        } catch (error) {
            // Storage access is optional for the tour.
        }
    }

    function cleanTourParameter() {
        var url = new URL(window.location.href);
        if (!url.searchParams.has("tour")) {
            return;
        }
        url.searchParams.delete("tour");
        window.history.replaceState(window.history.state, "", url.pathname + url.search + url.hash);
    }

    function normalisePath(path) {
        if (!path || path === "/") {
            return path || "/";
        }
        return path.replace(/\/+$/, "");
    }

    function stepUrl(step) {
        return new URL(step.route, window.location.origin);
    }

    function onStepRoute(step) {
        return normalisePath(window.location.pathname) === normalisePath(stepUrl(step).pathname);
    }

    function navigableUrl(step) {
        var url = stepUrl(step);
        url.searchParams.set("tour", "continue");
        return url.pathname + url.search + url.hash;
    }

    function visibleElement(selector) {
        var selectors = selector.split(",");
        for (var selectorIndex = 0; selectorIndex < selectors.length; selectorIndex += 1) {
            var candidates = document.querySelectorAll(selectors[selectorIndex].trim());
            for (var candidateIndex = 0; candidateIndex < candidates.length; candidateIndex += 1) {
                var rect = candidates[candidateIndex].getBoundingClientRect();
                var style = window.getComputedStyle(candidates[candidateIndex]);
                if (rect.width > 1 && rect.height > 1 && style.display !== "none" && style.visibility !== "hidden") {
                    return candidates[candidateIndex];
                }
            }
        }
        return null;
    }

    function waitForTarget(selector, attempts) {
        var target = visibleElement(selector) || visibleElement("main h1, main section, main");
        if (target || attempts <= 0) {
            return Promise.resolve(target || document.querySelector("main") || document.body);
        }
        return new Promise(function (resolve) {
            window.setTimeout(function () {
                resolve(waitForTarget(selector, attempts - 1));
            }, 100);
        });
    }

    function clamp(value, minimum, maximum) {
        return Math.min(Math.max(value, minimum), maximum);
    }

    function updateConnector(targetRect, bubbleRect) {
        if (window.innerWidth <= 640) {
            connectorPath.setAttribute("d", "");
            return;
        }

        var targetX = targetRect.left + targetRect.width / 2;
        var targetY = targetRect.top + targetRect.height / 2;
        var bubbleX = clamp(targetX, bubbleRect.left + 24, bubbleRect.right - 24);
        var bubbleY = clamp(targetY, bubbleRect.top + 24, bubbleRect.bottom - 24);
        var horizontal = Math.abs(bubbleX - targetX) > Math.abs(bubbleY - targetY);

        if (horizontal) {
            bubbleX = bubbleX > targetX ? bubbleRect.left : bubbleRect.right;
        } else {
            bubbleY = bubbleY > targetY ? bubbleRect.top : bubbleRect.bottom;
        }

        var controlX = targetX + (bubbleX - targetX) * 0.52;
        var path = "M " + targetX + " " + targetY + " C " + controlX + " " + targetY + ", " + controlX + " " + bubbleY + ", " + bubbleX + " " + bubbleY;
        connectorPath.setAttribute("d", path);
        connectorDot.setAttribute("cx", String(targetX));
        connectorDot.setAttribute("cy", String(targetY));
    }

    function positionTour() {
        if (!currentTarget || root.getAttribute("aria-hidden") === "true") {
            return;
        }

        var rect = currentTarget.getBoundingClientRect();
        var viewportWidth = window.innerWidth;
        var viewportHeight = window.innerHeight;
        var targetPadding = viewportWidth <= 640 ? 7 : 11;
        var left = clamp(rect.left - targetPadding, 6, viewportWidth - 12);
        var top = clamp(rect.top - targetPadding, 6, viewportHeight - 12);
        var width = clamp(rect.width + targetPadding * 2, 34, viewportWidth - left - 6);
        var height = clamp(rect.height + targetPadding * 2, 34, viewportHeight - top - 6);

        spotlight.style.left = left + "px";
        spotlight.style.top = top + "px";
        spotlight.style.width = width + "px";
        spotlight.style.height = height + "px";
        spotlight.style.borderRadius = Math.min(24, Math.max(12, Math.min(width, height) * 0.13)) + "px";

        if (viewportWidth <= 640) {
            window.requestAnimationFrame(function () {
                updateConnector(rect, bubble.getBoundingClientRect());
            });
            return;
        }

        var gap = 28;
        var margin = 14;
        var bubbleRect = bubble.getBoundingClientRect();
        var bubbleWidth = bubbleRect.width;
        var bubbleHeight = bubbleRect.height;
        var bubbleLeft;
        var bubbleTop;

        if (rect.bottom + gap + bubbleHeight <= viewportHeight - margin) {
            bubbleTop = rect.bottom + gap;
            bubbleLeft = clamp(rect.left + rect.width / 2 - bubbleWidth / 2, margin, viewportWidth - bubbleWidth - margin);
        } else if (rect.top - gap - bubbleHeight >= margin) {
            bubbleTop = rect.top - gap - bubbleHeight;
            bubbleLeft = clamp(rect.left + rect.width / 2 - bubbleWidth / 2, margin, viewportWidth - bubbleWidth - margin);
        } else if (rect.right + gap + bubbleWidth <= viewportWidth - margin) {
            bubbleLeft = rect.right + gap;
            bubbleTop = clamp(rect.top + rect.height / 2 - bubbleHeight / 2, margin, viewportHeight - bubbleHeight - margin);
        } else {
            bubbleLeft = Math.max(margin, rect.left - gap - bubbleWidth);
            bubbleTop = clamp(rect.top + rect.height / 2 - bubbleHeight / 2, margin, viewportHeight - bubbleHeight - margin);
        }

        bubble.style.left = bubbleLeft + "px";
        bubble.style.top = bubbleTop + "px";

        window.requestAnimationFrame(function () {
            updateConnector(rect, bubble.getBoundingClientRect());
        });
    }

    function queuePosition() {
        if (positionFrame) {
            window.cancelAnimationFrame(positionFrame);
        }
        positionFrame = window.requestAnimationFrame(function () {
            positionFrame = null;
            positionTour();
        });
    }

    function renderStep(index) {
        var step = steps[index];
        var nextStep = steps[index + 1];
        stepLabel.textContent = "Step " + (index + 1) + " of " + steps.length;
        pageLabel.textContent = step.page;
        title.textContent = step.title;
        copy.textContent = step.copy;
        hint.textContent = step.hint;
        progress.style.width = (((index + 1) / steps.length) * 100) + "%";
        backButton.hidden = index === 0;
        nextButton.textContent = index === steps.length - 1 ? "Finish tour" : (nextStep && onStepRoute(nextStep) ? "Next" : "Next page");
        announcement.textContent = step.page + ". " + step.title + ". Step " + (index + 1) + " of " + steps.length + ".";
    }

    function activateStep(state) {
        var index = clamp(Number(state.index) || 0, 0, steps.length - 1);
        var step = steps[index];
        if (!onStepRoute(step)) {
            window.location.assign(navigableUrl(step));
            return;
        }

        previousFocus = document.activeElement;
        renderStep(index);
        cleanTourParameter();

        waitForTarget(step.target, 24).then(function (target) {
            currentTarget = target;
            var targetRect = target.getBoundingClientRect();
            var isMobile = window.innerWidth <= 640;
            var needsScroll = targetRect.top < 90 || targetRect.bottom > window.innerHeight - (isMobile ? 340 : 90);
            if (needsScroll) {
                if (isMobile) {
                    window.scrollTo({
                        top: Math.max(0, targetRect.top + window.scrollY - 88),
                        behavior: reducedMotion ? "auto" : "smooth"
                    });
                } else {
                    target.scrollIntoView({ behavior: reducedMotion ? "auto" : "smooth", block: "center", inline: "nearest" });
                }
            }

            window.setTimeout(function () {
                root.hidden = false;
                root.setAttribute("aria-hidden", "false");
                document.body.classList.add("site-tour-active");
                positionTour();
                bubble.focus({ preventScroll: true });
            }, needsScroll && !reducedMotion ? 430 : 30);
        });
    }

    function goTo(index) {
        if (navigating || index < 0 || index >= steps.length) {
            return;
        }
        navigating = true;
        var nextState = writeState(index);
        var step = steps[index];

        if (onStepRoute(step)) {
            root.classList.add("is-leaving");
            window.setTimeout(function () {
                root.classList.remove("is-leaving");
                navigating = false;
                activateStep(nextState);
            }, reducedMotion ? 0 : 180);
            return;
        }

        root.classList.add("is-leaving");
        window.setTimeout(function () {
            window.location.assign(navigableUrl(step));
        }, reducedMotion ? 0 : 180);
    }

    function finishTour() {
        if (navigating) {
            return;
        }
        navigating = true;
        clearState();
        root.classList.add("is-celebrating");
        nextButton.disabled = true;
        nextButton.textContent = "Completed";
        announcement.textContent = "Tutorial complete. Returning to your dashboard.";
        window.setTimeout(function () {
            completeForm.submit();
        }, reducedMotion ? 50 : 650);
    }

    function skipTour() {
        if (navigating) {
            return;
        }
        navigating = true;
        clearState();
        root.classList.add("is-leaving");
        window.setTimeout(function () {
            completeForm.submit();
        }, reducedMotion ? 0 : 160);
    }

    function closeWithoutCompleting() {
        clearState();
        root.hidden = true;
        root.setAttribute("aria-hidden", "true");
        document.body.classList.remove("site-tour-active");
        if (previousFocus && typeof previousFocus.focus === "function") {
            previousFocus.focus();
        }
    }

    nextButton.addEventListener("click", function () {
        var state = readState();
        if (!state) {
            closeWithoutCompleting();
            return;
        }
        if (state.index >= steps.length - 1) {
            finishTour();
        } else {
            goTo(state.index + 1);
        }
    });

    backButton.addEventListener("click", function () {
        var state = readState();
        if (state && state.index > 0) {
            goTo(state.index - 1);
        }
    });

    skipButton.addEventListener("click", skipTour);

    document.addEventListener("keydown", function (event) {
        if (root.getAttribute("aria-hidden") !== "false") {
            return;
        }
        if (event.key === "Escape") {
            event.preventDefault();
            skipTour();
        } else if (event.key === "ArrowRight") {
            event.preventDefault();
            nextButton.click();
        } else if (event.key === "ArrowLeft" && !backButton.hidden) {
            event.preventDefault();
            backButton.click();
        } else if (event.key === "Tab") {
            var focusable = Array.prototype.slice.call(bubble.querySelectorAll("button:not([disabled])"));
            if (!focusable.length) {
                return;
            }
            var first = focusable[0];
            var last = focusable[focusable.length - 1];
            if (event.shiftKey && document.activeElement === first) {
                event.preventDefault();
                last.focus();
            } else if (!event.shiftKey && document.activeElement === last) {
                event.preventDefault();
                first.focus();
            }
        }
    });

    window.addEventListener("resize", queuePosition, { passive: true });
    window.addEventListener("scroll", queuePosition, { passive: true });

    var params = new URLSearchParams(window.location.search);
    var trigger = params.get("tour");
    // Only tour controls carry the continuation flag. Ordinary navigation or
    // a restored tab should never redirect the user back into an old step.
    var state = trigger === "start" ? writeState(0) : (trigger === "continue" ? readState() : null);
    if (state) {
        activateStep(state);
    } else {
        clearState();
    }
}());
