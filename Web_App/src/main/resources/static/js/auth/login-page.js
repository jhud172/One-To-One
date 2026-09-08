(function () {
    "use strict";

    const pageRoot = document.querySelector("[data-login-server-role]");
    const loginForm = document.getElementById("loginForm");
    const roleSlider = document.getElementById("roleSlider");
    const roleOptions = document.querySelectorAll(".role-slider__option");
    const loginTypeInput = document.getElementById("loginType");
    const authPanelStage = document.getElementById("authPanelStage");
    const emailFields = document.getElementById("emailFields");
    const gymFields = document.getElementById("gymFields");
    const trainerCodeField = document.getElementById("trainerCodeField");
    const roleHint = document.getElementById("roleHint");
    const socialAuthPanels = document.getElementById("socialAuthPanels");
    const usernameInput = document.getElementById("username");
    const passwordInput = document.getElementById("password");
    const gymUsernameInput = document.getElementById("gymUsername");
    const gymPasswordInput = document.getElementById("gymPassword");
    const togglePassword = document.getElementById("togglePassword");
    const eyeIcon = document.getElementById("eyeIcon");
    const toggleGymPassword = document.getElementById("toggleGymPassword");
    const gymEyeIcon = document.getElementById("gymEyeIcon");
    const roleSignupLink = document.getElementById("roleSignupLink");
    const loginError = document.getElementById("loginError");
    const loginValidationError = document.getElementById("loginValidationError");
    const trainerCodeFull = document.getElementById("trainerCodeFull");
    const gymSecretCodeFull = document.getElementById("gymSecretCodeFull");
    const trainerCodeInputs = [
        document.getElementById("trainerCode1"),
        document.getElementById("trainerCode2"),
        document.getElementById("trainerCode3")
    ].filter(Boolean);
    const gymSecretCodeInputs = [
        document.getElementById("gymSecretCode1"),
        document.getElementById("gymSecretCode2"),
        document.getElementById("gymSecretCode3"),
        document.getElementById("gymSecretCode4")
    ].filter(Boolean);

    if (!loginForm || !loginTypeInput || !authPanelStage || !emailFields || !gymFields || !trainerCodeField || !roleHint) {
        return;
    }

    const roles = ["client", "trainer", "gym"];
    const roleIndexMap = {
        client: 0,
        trainer: 1,
        gym: 2
    };
    const panels = {
        client: emailFields,
        trainer: emailFields,
        gym: gymFields
    };
    const hints = {
        client: pageRoot && pageRoot.dataset.clientHint ? pageRoot.dataset.clientHint : "Login with your email and password.",
        trainer: pageRoot && pageRoot.dataset.trainerHint ? pageRoot.dataset.trainerHint : "Login with email, password, and trainer code.",
        gym: pageRoot && pageRoot.dataset.gymHint ? pageRoot.dataset.gymHint : "Login with your gym username, gym secret code, and password."
    };
    const signupUrls = {
        client: pageRoot && pageRoot.dataset.clientSignupUrl ? pageRoot.dataset.clientSignupUrl : "/signup/client",
        trainer: pageRoot && pageRoot.dataset.trainerSignupUrl ? pageRoot.dataset.trainerSignupUrl : "/signup/trainer",
        gym: pageRoot && pageRoot.dataset.gymSignupUrl ? pageRoot.dataset.gymSignupUrl : "/signup/gym"
    };
    const prefersReducedMotion = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    let activeRole = null;
    let hintSwapTimer = null;

    function setPasswordIcon(icon, visible) {
        if (!icon) {
            return;
        }
        icon.innerHTML = visible
            ? '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21" />'
            : '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" /><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />';
    }

    function getStoredRole() {
        try {
            return sessionStorage.getItem("authRole");
        } catch (error) {
            return null;
        }
    }

    function persistRole(role) {
        try {
            sessionStorage.setItem("authRole", role);
        } catch (error) {
            // Ignore storage failures.
        }
    }

    function getActivePanel() {
        return emailFields.hidden ? gymFields : emailFields;
    }

    function setRegionAvailable(region, available) {
        if (!region) {
            return;
        }
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

    function setStageHeight(immediate) {
        const activePanel = getActivePanel();

        if (!activePanel) {
            return;
        }

        const nextHeight = activePanel.offsetHeight;

        if (immediate) {
            authPanelStage.style.transition = "none";
            authPanelStage.style.height = nextHeight + "px";
            authPanelStage.offsetHeight;
            authPanelStage.style.transition = "";
            return;
        }

        authPanelStage.style.height = nextHeight + "px";
    }

    function updateHint(role, animate) {
        const nextHint = hints[role] || hints.client;

        window.clearTimeout(hintSwapTimer);

        if (!animate) {
            roleHint.textContent = nextHint;
            roleHint.classList.remove("role-hint--swapping");
            return;
        }

        roleHint.classList.add("role-hint--swapping");
        hintSwapTimer = window.setTimeout(() => {
            roleHint.textContent = nextHint;
            roleHint.classList.remove("role-hint--swapping");
        }, 120);
    }

    function syncSliderIndicator(role) {
        if (!roleSlider) {
            return;
        }

        const activeOption = roleSlider.querySelector('[data-role="' + role + '"]');

        if (!activeOption) {
            return;
        }

        const sliderRect = roleSlider.getBoundingClientRect();
        const optionRect = activeOption.getBoundingClientRect();
        const left = optionRect.left - sliderRect.left;

        roleSlider.style.setProperty("--indicator-left", left + "px");
        roleSlider.style.setProperty("--indicator-width", optionRect.width + "px");
    }

    function setSliderState(role) {
        roleOptions.forEach((option) => {
            const isActive = option.dataset.role === role;
            option.classList.toggle("active", isActive);
            option.setAttribute("aria-selected", isActive ? "true" : "false");
            option.tabIndex = isActive ? 0 : -1;
        });
        window.requestAnimationFrame(() => syncSliderIndicator(role));
    }

    function setPanelOffset(panel, offset) {
        if (panel) {
            panel.style.setProperty("--panel-offset", offset + "px");
        }
    }

    function switchPanels(nextPanel, direction, animate) {
        const currentPanel = getActivePanel();
        const enteringOffset = direction >= 0 ? 28 : -28;
        const leavingOffset = direction >= 0 ? -28 : 28;

        if (!nextPanel) {
            return;
        }

        if (!currentPanel || currentPanel === nextPanel) {
            setPanelOffset(nextPanel, 0);
            nextPanel.classList.add("auth-fields-block--active");
            nextPanel.classList.remove("auth-fields-block--inactive");
            setRegionAvailable(nextPanel, true);
            setStageHeight(!animate);
            return;
        }

        setPanelOffset(currentPanel, leavingOffset);
        setPanelOffset(nextPanel, enteringOffset);

        authPanelStage.style.height = currentPanel.offsetHeight + "px";
        currentPanel.classList.remove("auth-fields-block--active");
        currentPanel.classList.add("auth-fields-block--inactive");
        setRegionAvailable(currentPanel, false);

        nextPanel.classList.remove("auth-fields-block--active");
        nextPanel.classList.add("auth-fields-block--inactive");
        setRegionAvailable(nextPanel, true);
        authPanelStage.style.height = nextPanel.offsetHeight + "px";

        if (!animate || prefersReducedMotion) {
            nextPanel.classList.remove("auth-fields-block--inactive");
            nextPanel.classList.add("auth-fields-block--active");
            setPanelOffset(nextPanel, 0);
            setStageHeight(true);
            return;
        }

        window.requestAnimationFrame(() => {
            nextPanel.classList.remove("auth-fields-block--inactive");
            nextPanel.classList.add("auth-fields-block--active");
            setPanelOffset(nextPanel, 0);
        });
    }

    function setTrainerCodeVisible(visible) {
        setRegionAvailable(trainerCodeField, visible);
        trainerCodeField.classList.toggle("auth-inline-section--expanded", visible);
        trainerCodeField.classList.toggle("auth-inline-section--collapsed", !visible);
    }

    function setSocialAuthRole(role) {
        if (!socialAuthPanels) {
            return;
        }

        const panels = socialAuthPanels.querySelectorAll("[data-social-role]");
        let matched = false;

        panels.forEach((panel) => {
            const isActive = panel.getAttribute("data-social-role") === role;
            panel.classList.toggle("social-auth-panel--active", isActive);
            setRegionAvailable(panel, isActive);
            matched = matched || isActive;
        });

        socialAuthPanels.hidden = !matched;
    }

    function sanitizeTrainerSegment(value) {
        return value.replace(/[^a-z0-9]/gi, "").toUpperCase().slice(0, 4);
    }

    function normalizeTrainerCode(value) {
        return value.replace(/[^a-z0-9]/gi, "").toUpperCase();
    }

    function sanitizeGymSecretSegment(value) {
        return value.replace(/\D/g, "").slice(0, 4);
    }

    function normalizeGymSecretCode(value) {
        return value.replace(/\D/g, "");
    }

    function updateSegmentedCode(inputs, hiddenInput, sanitizer) {
        if (!hiddenInput || inputs.length === 0) {
            return;
        }
        hiddenInput.value = inputs.map((input) => sanitizer(input.value)).join("");
    }

    function removeDescription(input, descriptionId) {
        if (!input) {
            return;
        }

        const descriptionIds = (input.getAttribute("aria-describedby") || "")
            .split(/\s+/)
            .filter((id) => id && id !== descriptionId);

        if (descriptionIds.length > 0) {
            input.setAttribute("aria-describedby", descriptionIds.join(" "));
        } else {
            input.removeAttribute("aria-describedby");
        }
    }

    function clearLoginValidation() {
        const controls = [
            usernameInput,
            passwordInput,
            gymUsernameInput,
            gymPasswordInput,
            ...trainerCodeInputs,
            ...gymSecretCodeInputs
        ].filter(Boolean);

        controls.forEach((input) => {
            input.removeAttribute("aria-invalid");
            removeDescription(input, "loginValidationError");
        });

        if (loginValidationError) {
            loginValidationError.textContent = "";
            loginValidationError.hidden = true;
        }
    }

    function showLoginValidation(input, message) {
        if (!input || !loginValidationError) {
            return;
        }

        clearLoginValidation();
        loginValidationError.textContent = message;
        loginValidationError.hidden = false;
        input.setAttribute("aria-invalid", "true");

        const descriptionIds = new Set((input.getAttribute("aria-describedby") || "").split(/\s+/).filter(Boolean));
        descriptionIds.add("loginValidationError");
        input.setAttribute("aria-describedby", Array.from(descriptionIds).join(" "));
        input.focus();
    }

    function focusFirstIncomplete(inputs, message) {
        const firstIncomplete = inputs.find((input) => input.value.length < 4) || inputs[0];
        if (firstIncomplete) {
            showLoginValidation(firstIncomplete, message);
            firstIncomplete.select();
        }
    }

    function validateSegmentedCode(inputs, hiddenInput, expectedLength, message, sanitizer) {
        if (!hiddenInput || inputs.length === 0) {
            return true;
        }

        updateSegmentedCode(inputs, hiddenInput, sanitizer);
        const isComplete = hiddenInput.value.length === expectedLength && inputs.every((input) => sanitizer(input.value).length === 4);

        if (isComplete) {
            return true;
        }

        focusFirstIncomplete(inputs, message);
        return false;
    }

    function bindSegmentedCodeInputs(inputs, hiddenInput, sanitizer, fullSanitizer) {
        inputs.forEach((input, index) => {
            input.addEventListener("input", () => {
                input.value = sanitizer(input.value);
                if (input.getAttribute("aria-invalid") === "true") {
                    clearLoginValidation();
                }
                updateSegmentedCode(inputs, hiddenInput, sanitizer);

                if (input.value.length === 4 && index < inputs.length - 1) {
                    inputs[index + 1].focus();
                    inputs[index + 1].select();
                }
            });

            input.addEventListener("keydown", (event) => {
                if (event.key === "Backspace" && input.value.length === 0 && index > 0) {
                    inputs[index - 1].focus();
                }
            });

            input.addEventListener("paste", (event) => {
                const pasted = fullSanitizer((event.clipboardData || window.clipboardData).getData("text"));
                if (!pasted) {
                    return;
                }

                const sliceLength = inputs.length * 4;
                const normalized = pasted.slice(0, sliceLength);
                window.setTimeout(() => {
                    inputs.forEach((segmentInput, segmentIndex) => {
                        segmentInput.value = normalized.slice(segmentIndex * 4, (segmentIndex + 1) * 4);
                    });
                    clearLoginValidation();
                    updateSegmentedCode(inputs, hiddenInput, sanitizer);

                    const lastFilledIndex = Math.min(Math.ceil(normalized.length / 4) - 1, inputs.length - 1);
                    if (lastFilledIndex >= 0) {
                        inputs[lastFilledIndex].focus();
                    }
                }, 0);
            });
        });
    }

    function setFieldMode(input, options) {
        if (!input) {
            return;
        }

        if (options.required) {
            input.setAttribute("required", "required");
        } else {
            input.removeAttribute("required");
        }

        input.name = options.name || "";
    }

    function activateRole(role, animate) {
        const resolvedRole = roles.includes(role) ? role : "client";
        const previousRole = activeRole || resolvedRole;
        const shouldAnimate = Boolean(animate && activeRole && resolvedRole !== activeRole);
        const targetPanel = panels[resolvedRole];

        setSliderState(resolvedRole);
        clearLoginValidation();
        loginTypeInput.value = resolvedRole;
        updateHint(resolvedRole, shouldAnimate);
        persistRole(resolvedRole);
        setSocialAuthRole(resolvedRole);
        if (roleSignupLink) {
            roleSignupLink.href = signupUrls[resolvedRole];
        }
        pageRoot?.dispatchEvent(new CustomEvent("auth:rolechange", {
            detail: { role: resolvedRole }
        }));

        emailFields.setAttribute("aria-labelledby", resolvedRole === "trainer" ? "loginRoleTrainer" : "loginRoleClient");

        if (resolvedRole === "gym") {
            switchPanels(targetPanel, roleIndexMap[resolvedRole] - roleIndexMap[previousRole], shouldAnimate);
            setTrainerCodeVisible(false);

            setFieldMode(usernameInput, { required: false, name: "" });
            setFieldMode(passwordInput, { required: false, name: "" });
            setFieldMode(gymUsernameInput, { required: true, name: "username" });
            setFieldMode(gymPasswordInput, { required: true, name: "password" });
            setFieldMode(trainerCodeFull, { required: false, name: "" });
            setFieldMode(gymSecretCodeFull, { required: false, name: "gymSecretCode" });

            activeRole = resolvedRole;
            return;
        }

        setFieldMode(usernameInput, { required: true, name: "username" });
        setFieldMode(passwordInput, { required: true, name: "password" });
        setFieldMode(gymUsernameInput, { required: false, name: "" });
        setFieldMode(gymPasswordInput, { required: false, name: "" });
        setFieldMode(gymSecretCodeFull, { required: false, name: "" });
        setFieldMode(trainerCodeFull, { required: false, name: resolvedRole === "trainer" ? "trainerCode" : "" });

        switchPanels(targetPanel, roleIndexMap[resolvedRole] - roleIndexMap[previousRole], shouldAnimate);
        setTrainerCodeVisible(resolvedRole === "trainer");
        activeRole = resolvedRole;

        window.requestAnimationFrame(() => setStageHeight(false));
    }

    roleOptions.forEach((option) => {
        option.addEventListener("click", () => activateRole(option.dataset.role, true));
        option.addEventListener("keydown", (event) => {
            const currentIndex = roleIndexMap[option.dataset.role];
            let nextRole = null;

            if (event.key === "ArrowRight") {
                nextRole = roles[(currentIndex + 1) % roles.length];
            } else if (event.key === "ArrowLeft") {
                nextRole = roles[(currentIndex - 1 + roles.length) % roles.length];
            } else if (event.key === "Home") {
                nextRole = roles[0];
            } else if (event.key === "End") {
                nextRole = roles[roles.length - 1];
            }

            if (!nextRole) return;
            event.preventDefault();
            activateRole(nextRole, true);
            Array.from(roleOptions).find((roleOption) => roleOption.dataset.role === nextRole)?.focus();
        });
    });

    const serverRole = pageRoot ? pageRoot.dataset.loginServerRole : "";
    const storedRole = getStoredRole();
    activateRole(serverRole || storedRole || "client", false);
    setStageHeight(true);
    syncSliderIndicator(activeRole || "client");

    if (typeof ResizeObserver === "function") {
        const observer = new ResizeObserver(() => {
            window.requestAnimationFrame(() => setStageHeight(false));
        });

        observer.observe(emailFields);
        observer.observe(gymFields);
    }

    trainerCodeField.addEventListener("transitionend", () => setStageHeight(false));
    window.addEventListener("resize", () => {
        setStageHeight(true);
        syncSliderIndicator(activeRole || "client");
    });

    if (togglePassword && passwordInput) {
        togglePassword.addEventListener("click", () => {
            const visible = passwordInput.getAttribute("type") === "password";
            passwordInput.setAttribute("type", visible ? "text" : "password");
            togglePassword.setAttribute("aria-label", visible ? "Hide password" : "Show password");
            setPasswordIcon(eyeIcon, visible);
        });
    }

    if (toggleGymPassword && gymPasswordInput) {
        toggleGymPassword.addEventListener("click", () => {
            const visible = gymPasswordInput.getAttribute("type") === "password";
            gymPasswordInput.setAttribute("type", visible ? "text" : "password");
            toggleGymPassword.setAttribute("aria-label", visible ? "Hide gym password" : "Show gym password");
            setPasswordIcon(gymEyeIcon, visible);
        });
    }

    bindSegmentedCodeInputs(trainerCodeInputs, trainerCodeFull, sanitizeTrainerSegment, normalizeTrainerCode);
    bindSegmentedCodeInputs(gymSecretCodeInputs, gymSecretCodeFull, sanitizeGymSecretSegment, normalizeGymSecretCode);

    [usernameInput, passwordInput, gymUsernameInput, gymPasswordInput].filter(Boolean).forEach((input) => {
        input.addEventListener("input", () => {
            if (input.getAttribute("aria-invalid") === "true" && input.value.trim()) {
                clearLoginValidation();
            }
        });
    });

    const serverError = pageRoot ? pageRoot.dataset.loginError : "";
    if (serverError) {
        const invalidGroup = activeRole === "trainer" ? trainerCodeInputs : activeRole === "gym" ? gymSecretCodeInputs : [];
        if (serverError === "invalid" && invalidGroup.length > 0) {
            invalidGroup.forEach((input) => input.setAttribute("aria-invalid", "true"));
            window.setTimeout(() => invalidGroup[0].focus(), 0);
        } else if (loginError) {
            window.setTimeout(() => loginError.focus(), 0);
        }
    }

    loginForm.addEventListener("submit", (event) => {
        clearLoginValidation();

        const requiredFields = activeRole === "gym"
            ? [
                { input: gymUsernameInput, message: "Enter your gym username." },
                { input: gymPasswordInput, message: "Enter your gym password." }
            ]
            : [
                { input: usernameInput, message: "Enter your email or username." },
                { input: passwordInput, message: "Enter your password." }
            ];
        const missingField = requiredFields.find(({ input }) => input && !input.value.trim());

        if (missingField) {
            event.preventDefault();
            showLoginValidation(missingField.input, missingField.message);
            return;
        }

        if (activeRole === "trainer" && !validateSegmentedCode(
            trainerCodeInputs,
            trainerCodeFull,
            12,
            "Enter your full trainer authorization code.",
            sanitizeTrainerSegment
        )) {
            event.preventDefault();
            return;
        }

        if (activeRole === "gym" && !validateSegmentedCode(
            gymSecretCodeInputs,
            gymSecretCodeFull,
            16,
            "Enter your full 16-digit gym secret code.",
            sanitizeGymSecretSegment
        )) {
            event.preventDefault();
        }
    });
})();
