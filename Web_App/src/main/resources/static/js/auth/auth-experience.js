(() => {
    "use strict";

    const roots = Array.from(document.querySelectorAll(".guest-auth[data-guest-scene]"));
    if (roots.length === 0) {
        return;
    }

    const reducedMotion = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
    document.documentElement.classList.add("auth-flow-ready");

    function setRegionAvailable(region, available) {
        region.hidden = !available;
        region.setAttribute("aria-hidden", available ? "false" : "true");
        if (available) {
            region.removeAttribute("inert");
            region.inert = false;
        } else {
            region.setAttribute("inert", "");
            region.inert = true;
        }
    }

    function initialiseFields(root) {
        const controls = root.querySelectorAll(".auth-field__input, .code-input-part, .verify-code-input");

        controls.forEach((control) => {
            const field = control.closest(".auth-field, .auth-code-fieldset, .auth-inline-section") || control.parentElement;
            const syncValueState = () => field?.classList.toggle("auth-field--filled", control.value.trim().length > 0);

            control.addEventListener("focus", () => field?.classList.add("auth-field--focused"));
            control.addEventListener("blur", () => {
                field?.classList.remove("auth-field--focused");
                syncValueState();
            });
            control.addEventListener("input", () => {
                syncValueState();
                if (control.checkValidity()) {
                    control.removeAttribute("aria-invalid");
                    field?.classList.remove("auth-field--invalid");
                }
            });
            syncValueState();
        });
    }

    function initialisePasswordStrength(root) {
        root.querySelectorAll("[data-password-strength]").forEach((meter) => {
            const inputId = meter.dataset.passwordStrength || "password";
            const input = root.querySelector(`#${CSS.escape(inputId)}`);
            const label = meter.querySelector("[data-password-strength-label]");
            const labels = [
                meter.dataset.emptyLabel || "Start with 8 or more characters",
                meter.dataset.weakLabel || "Needs more variety",
                meter.dataset.fairLabel || "Getting stronger",
                meter.dataset.goodLabel || "Good password",
                meter.dataset.strongLabel || "Strong password",
                meter.dataset.completeLabel || "Excellent password"
            ];

            if (!input) {
                return;
            }

            const update = () => {
                const value = input.value;
                const score = value.length === 0 ? 0 : [
                    value.length >= 8,
                    /[a-z]/.test(value) && /[A-Z]/.test(value),
                    /\d/.test(value),
                    /[^A-Za-z0-9]/.test(value),
                    value.length >= 12
                ].filter(Boolean).length;

                meter.dataset.score = String(score);
                meter.style.setProperty("--password-score", String(score));
                meter.setAttribute("aria-valuenow", String(score));
                if (label) {
                    label.textContent = labels[score];
                }
            };

            input.addEventListener("input", update);
            update();
        });
    }

    function initialisePasswordToggles(root) {
        const visibleIcon = '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21" />';
        const hiddenIcon = '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" /><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />';

        root.querySelectorAll(".password-toggle").forEach((button) => {
            if (button.closest("#loginForm")) {
                return;
            }
            const wrapper = button.closest(".auth-field__input-wrapper");
            const input = wrapper?.querySelector("input[type='password'], input[data-password-field]");
            const icon = button.querySelector("svg");
            if (!input || !icon) {
                return;
            }

            button.addEventListener("click", () => {
                const showing = input.type === "password";
                input.type = showing ? "text" : "password";
                button.setAttribute("aria-pressed", showing ? "true" : "false");
                button.setAttribute("aria-label", showing
                    ? (button.dataset.hideLabel || "Hide password")
                    : (button.dataset.showLabel || "Show password"));
                icon.innerHTML = showing ? visibleIcon : hiddenIcon;
            });
        });
    }

    function initialiseWizard(form) {
        const steps = Array.from(form.querySelectorAll(":scope > [data-auth-step]"));
        if (steps.length < 2) {
            return;
        }

        const navItems = Array.from(form.querySelectorAll("[data-auth-step-nav]"));
        const progress = form.querySelector("[data-auth-progress]");
        const progressLabel = form.querySelector("[data-auth-progress-label]");
        let currentIndex = Math.max(0, steps.findIndex((step) => step.querySelector(".auth-field__error")));

        const showStepError = (step, control) => {
            const error = step.querySelector("[data-auth-step-error]");
            const field = control.closest(".auth-field, .auth-code-fieldset") || control.parentElement;

            control.setAttribute("aria-invalid", "true");
            field?.classList.add("auth-field--invalid");
            if (error) {
                error.hidden = false;
                error.textContent = form.dataset.stepError || "Please complete the highlighted fields before continuing.";
            }
            control.focus({ preventScroll: true });
            control.scrollIntoView({ behavior: reducedMotion ? "auto" : "smooth", block: "center" });
        };

        const validateStep = (step) => {
            const controls = Array.from(step.querySelectorAll("input, textarea, select"))
                .filter((control) => !control.disabled && control.type !== "hidden");
            const firstInvalid = controls.find((control) => !control.checkValidity());

            if (firstInvalid) {
                showStepError(step, firstInvalid);
                return false;
            }
            step.querySelector("[data-auth-step-error]")?.setAttribute("hidden", "");
            return true;
        };

        const setStep = (nextIndex, focusHeading = true) => {
            currentIndex = Math.min(steps.length - 1, Math.max(0, nextIndex));

            steps.forEach((step, index) => {
                const active = index === currentIndex;
                step.classList.toggle("is-active", active);
                step.classList.toggle("is-complete", index < currentIndex);
                setRegionAvailable(step, active);
            });

            navItems.forEach((item, index) => {
                const active = index === currentIndex;
                item.classList.toggle("is-active", active);
                item.classList.toggle("is-complete", index < currentIndex);
                item.toggleAttribute("disabled", index > currentIndex);
                if (active) {
                    item.setAttribute("aria-current", "step");
                } else {
                    item.removeAttribute("aria-current");
                }
            });

            const completedRatio = steps.length === 1 ? 1 : currentIndex / (steps.length - 1);
            form.style.setProperty("--auth-progress", `${completedRatio * 100}%`);
            progress?.setAttribute("aria-valuenow", String(currentIndex + 1));
            if (progressLabel) {
                progressLabel.textContent = progressLabel.dataset.pattern
                    ?.replace("{current}", String(currentIndex + 1))
                    .replace("{total}", String(steps.length)) || `Step ${currentIndex + 1} of ${steps.length}`;
            }

            if (focusHeading) {
                const heading = steps[currentIndex].querySelector("[data-auth-step-heading]");
                window.setTimeout(() => heading?.focus({ preventScroll: true }), reducedMotion ? 0 : 180);
            }
        };

        form.addEventListener("click", (event) => {
            const nextButton = event.target.closest("[data-auth-next]");
            const backButton = event.target.closest("[data-auth-back]");
            const navButton = event.target.closest("[data-auth-step-nav]");

            if (nextButton) {
                if (validateStep(steps[currentIndex])) {
                    setStep(currentIndex + 1);
                }
                return;
            }
            if (backButton) {
                setStep(currentIndex - 1);
                return;
            }
            if (navButton && !navButton.disabled) {
                setStep(navItems.indexOf(navButton));
            }
        });

        form.addEventListener("submit", (event) => {
            const firstInvalidStep = steps.findIndex((step) => !validateStep(step));
            if (firstInvalidStep >= 0) {
                event.preventDefault();
                setStep(firstInvalidStep, false);
            }
        });

        setStep(currentIndex, false);
    }

    function initialiseSubmitState(root) {
        root.querySelectorAll("form").forEach((form) => {
            form.addEventListener("submit", (event) => {
                if (event.defaultPrevented || !form.checkValidity()) {
                    return;
                }
                const submit = form.querySelector("button[type='submit']");
                form.classList.add("is-submitting");
                submit?.setAttribute("aria-busy", "true");
            });
        });
    }

    function initialiseRoleStory(root) {
        const story = root.querySelector("[data-auth-role-story]");
        if (!story) {
            return;
        }

        const eyebrow = story.querySelector("[data-auth-role-eyebrow]");
        const title = story.querySelector("[data-auth-role-title]");
        const copy = story.querySelector("[data-auth-role-copy]");
        let swapTimer = 0;

        const update = (role, animate) => {
            const resolvedRole = ["client", "trainer", "gym"].includes(role) ? role : "client";
            const applyCopy = () => {
                if (eyebrow) eyebrow.textContent = story.dataset[`${resolvedRole}Eyebrow`] || "Your workspace";
                if (title) title.textContent = story.dataset[`${resolvedRole}Title`] || "Continue where you left off.";
                if (copy) copy.textContent = story.dataset[`${resolvedRole}Copy`] || "Your account keeps every next step in one place.";
                story.dataset.activeRole = resolvedRole;
                story.classList.remove("is-swapping");
            };

            window.clearTimeout(swapTimer);
            if (!animate || reducedMotion) {
                applyCopy();
                return;
            }
            story.classList.add("is-swapping");
            swapTimer = window.setTimeout(applyCopy, 130);
        };

        root.addEventListener("auth:rolechange", (event) => update(event.detail?.role, true));
        update(root.dataset.loginServerRole || "client", false);
    }

    function initialiseRouteTransitions(root) {
        if (reducedMotion) {
            return;
        }

        root.addEventListener("click", (event) => {
            const link = event.target.closest("a[href]");
            if (!link || event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
                return;
            }
            if (link.target || link.hasAttribute("download") || link.getAttribute("href").startsWith("#")) {
                return;
            }

            const destination = new URL(link.href, window.location.href);
            if (destination.origin !== window.location.origin || destination.href === window.location.href) {
                return;
            }

            event.preventDefault();
            root.classList.add("is-leaving");
            window.setTimeout(() => window.location.assign(destination.href), 180);
        });
    }

    roots.forEach((root) => {
        initialiseFields(root);
        initialisePasswordStrength(root);
        initialisePasswordToggles(root);
        root.querySelectorAll("form[data-auth-wizard]").forEach(initialiseWizard);
        initialiseSubmitState(root);
        initialiseRoleStory(root);
        initialiseRouteTransitions(root);
        window.requestAnimationFrame(() => root.classList.add("is-auth-ready"));
    });
})();
