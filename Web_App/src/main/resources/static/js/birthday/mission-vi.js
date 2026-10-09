(() => {
    "use strict";

    const root = document.documentElement;
    const body = document.body;
    const experience = document.querySelector("[data-birthday-main]");
    const intro = document.querySelector("[data-birthday-intro]");
    const holdButton = document.querySelector("[data-birthday-hold]");
    const openButton = document.querySelector("[data-birthday-open]");
    const holdCaption = document.querySelector("[data-birthday-hold-caption]");
    const replayButton = document.querySelector("[data-birthday-replay]");
    const fireworksButton = document.querySelector("[data-birthday-fireworks]");
    const fireworksLayer = document.querySelector("[data-birthday-fireworks-layer]");
    const unlockTakeover = document.querySelector("[data-birthday-takeover]");
    const promise = document.querySelector("[data-birthday-promise]");
    const confettiLayer = document.querySelector("[data-birthday-confetti]");
    const liveRegion = document.querySelector("[data-birthday-live]");
    const chapterLinks = [...document.querySelectorAll("[data-chapter-link]")];
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const finePointer = window.matchMedia("(pointer: fine)").matches;
    const holdDuration = reduceMotion ? 250 : 1250;
    let holdFrame = 0;
    let holdStartedAt = 0;
    let unlocked = false;

    if (!experience || !intro || !holdButton || !promise) {
        return;
    }

    root.classList.add("birthday-ready");
    [holdButton, openButton, replayButton, fireworksButton, ...document.querySelectorAll('[data-birthday-enhancement]')]
        .filter(Boolean).forEach((control) => { control.hidden = false; });
    body.classList.add("birthday-is-locked");
    experience.classList.remove("is-unlocked");
    experience.dataset.state = "locked";

    const finishIntro = () => {
        intro.hidden = true;
        intro.classList.remove("is-leaving");
        body.classList.remove("birthday-is-locked");
        root.classList.add("birthday-entered");
        holdButton.focus({ preventScroll: true });
    };

    const playIntro = () => {
        intro.hidden = false;
        body.classList.add("birthday-is-locked");
        root.classList.remove("birthday-entered");
        window.scrollTo({ top: 0, behavior: "auto" });
        window.setTimeout(() => intro.classList.add("is-leaving"), reduceMotion ? 40 : 1250);
        window.setTimeout(finishIntro, reduceMotion ? 80 : 2120);
    };

    const updateHoldProgress = (progress) => {
        holdButton.style.setProperty("--birthday-hold-progress", Math.min(1, Math.max(0, progress)).toFixed(3));
    };

    const cancelHold = () => {
        if (unlocked) {
            return;
        }
        window.cancelAnimationFrame(holdFrame);
        holdFrame = 0;
        holdStartedAt = 0;
        holdButton.classList.remove("is-holding");
        if (holdCaption) {
            holdCaption.textContent = "Press and hold your birthday mission";
        }
        updateHoldProgress(0);
    };

    const burstConfetti = (pieceCount = 72) => {
        if (!confettiLayer || reduceMotion) {
            return;
        }

        const colours = ["#ff5f6d", "#ff3fa4", "#7cecff", "#fff3dc", "#ffad78"];
        const fragment = document.createDocumentFragment();

        for (let index = 0; index < pieceCount; index += 1) {
            const piece = document.createElement("i");
            const width = 5 + Math.random() * 8;
            piece.style.setProperty("--confetti-x", `${Math.random() * 100}%`);
            piece.style.setProperty("--confetti-width", `${width}px`);
            piece.style.setProperty("--confetti-height", `${width * (0.45 + Math.random())}px`);
            piece.style.setProperty("--confetti-colour", colours[index % colours.length]);
            piece.style.setProperty("--confetti-duration", `${2.2 + Math.random() * 2.2}s`);
            piece.style.setProperty("--confetti-delay", `${Math.random() * 0.45}s`);
            piece.style.setProperty("--confetti-drift", `${-120 + Math.random() * 240}px`);
            piece.style.setProperty("--confetti-rotation", `${Math.random() * 360}deg`);
            fragment.appendChild(piece);
        }

        confettiLayer.replaceChildren(fragment);
        window.setTimeout(() => confettiLayer.replaceChildren(), 5200);
    };

    const playUnlockTakeover = () => {
        if (!unlockTakeover) {
            return reduceMotion ? 40 : 420;
        }

        const holdTime = reduceMotion ? 180 : 1480;
        const totalTime = reduceMotion ? 340 : 2010;
        body.classList.add("birthday-is-celebrating");
        unlockTakeover.hidden = false;
        unlockTakeover.setAttribute("aria-hidden", "false");
        unlockTakeover.classList.remove("is-leaving");

        window.requestAnimationFrame(() => unlockTakeover.classList.add("is-active"));
        window.setTimeout(() => unlockTakeover.classList.add("is-leaving"), holdTime);
        window.setTimeout(() => {
            unlockTakeover.hidden = true;
            unlockTakeover.setAttribute("aria-hidden", "true");
            unlockTakeover.classList.remove("is-active", "is-leaving");
            body.classList.remove("birthday-is-celebrating");
        }, totalTime);

        return totalTime;
    };

    const launchFireworks = () => {
        if (!fireworksLayer || reduceMotion) {
            return;
        }

        const colours = ["#ff5f6d", "#ff3fa4", "#7cecff", "#fff3dc", "#ffad78"];
        const fragment = document.createDocumentFragment();

        for (let index = 0; index < 9; index += 1) {
            const firework = document.createElement("i");
            firework.style.setProperty("--firework-x", `${12 + Math.random() * 76}%`);
            firework.style.setProperty("--firework-y", `${8 + Math.random() * 48}%`);
            firework.style.setProperty("--firework-colour", colours[index % colours.length]);
            firework.style.setProperty("--firework-delay", `${Math.random() * 0.85}s`);
            fragment.appendChild(firework);
        }

        fireworksLayer.replaceChildren(fragment);
        window.setTimeout(() => fireworksLayer.replaceChildren(), 2600);
    };

    const unlockGift = () => {
        if (unlocked) {
            return;
        }

        unlocked = true;
        window.cancelAnimationFrame(holdFrame);
        updateHoldProgress(1);
        holdButton.classList.remove("is-holding");
        holdButton.querySelector("strong").textContent = "Mission unlocked";
        if (holdCaption) {
            holdCaption.textContent = "Your GTA VI promise is ready";
        }
        experience.classList.add("is-unlocked");
        experience.dataset.state = "unlocked";
        if (liveRegion) liveRegion.textContent = "Mission unlocked. Your GTA VI birthday promise is ready.";
        navigator.vibrate?.([18, 42, 38]);
        const takeoverDuration = playUnlockTakeover();
        window.setTimeout(() => burstConfetti(), Math.max(0, takeoverDuration - 460));

        window.setTimeout(() => {
            promise.setAttribute('tabindex', '-1');
            promise.focus({ preventScroll: true });
            promise.scrollIntoView({ behavior: reduceMotion ? "auto" : "smooth", block: "start" });
        }, takeoverDuration);
    };

    const advanceHold = (timestamp) => {
        if (!holdStartedAt) {
            holdStartedAt = timestamp;
        }

        const progress = (timestamp - holdStartedAt) / holdDuration;
        updateHoldProgress(progress);

        if (progress >= 1) {
            unlockGift();
            return;
        }

        holdFrame = window.requestAnimationFrame(advanceHold);
    };

    const startHold = (event) => {
        if (unlocked || holdFrame) {
            return;
        }
        if (event.type === "keydown" && event.key !== "Enter" && event.key !== " ") {
            return;
        }
        if (event.type === "keydown") {
            event.preventDefault();
        }
        holdButton.classList.add("is-holding");
        if (holdCaption) {
            holdCaption.textContent = "Keep holding - almost there";
        }
        navigator.vibrate?.(12);
        holdFrame = window.requestAnimationFrame(advanceHold);
    };

    const endHold = (event) => {
        if (event.type === "keyup" && event.key !== "Enter" && event.key !== " ") {
            return;
        }
        cancelHold();
    };

    holdButton.addEventListener("pointerdown", (event) => {
        event.preventDefault();
        holdButton.setPointerCapture?.(event.pointerId);
        startHold(event);
    });
    holdButton.addEventListener("pointerup", endHold);
    holdButton.addEventListener("pointercancel", endHold);
    holdButton.addEventListener("lostpointercapture", endHold);
    holdButton.addEventListener("keydown", startHold);
    holdButton.addEventListener("keyup", endHold);
    holdButton.addEventListener("blur", cancelHold);
    holdButton.addEventListener("contextmenu", (event) => event.preventDefault());
    openButton?.addEventListener('click', unlockGift);
    window.addEventListener('blur', cancelHold);
    document.addEventListener('visibilitychange', () => { if (document.hidden) cancelHold(); });

    const sections = document.querySelectorAll(".birthday-promise, .birthday-countdown, .birthday-finale");
    const observer = 'IntersectionObserver' in window ? new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
            if (entry.isIntersecting) {
                entry.target.classList.add("is-visible");
            }
        });
    }, { threshold: 0.18 }) : null;
    sections.forEach((section) => observer ? observer.observe(section) : section.classList.add('is-visible'));

    const chapterObserver = 'IntersectionObserver' in window ? new IntersectionObserver((entries) => {
        const current = entries
            .filter((entry) => entry.isIntersecting)
            .sort((first, second) => second.intersectionRatio - first.intersectionRatio)[0];

        if (!current) {
            return;
        }

        const chapter = current.target.dataset.birthdayChapter;
        chapterLinks.forEach((link) => {
            if (link.dataset.chapterLink === chapter) {
                link.setAttribute("aria-current", "true");
            } else {
                link.removeAttribute("aria-current");
            }
        });
    }, { rootMargin: "-30% 0px -50%", threshold: [0, 0.15, 0.45] }) : null;
    document.querySelectorAll("[data-birthday-chapter]").forEach((chapter) => chapterObserver?.observe(chapter));

    const countdown = document.querySelector("[data-birthday-countdown]");
    const countdownFields = {
        days: document.querySelector("[data-countdown-days]"),
        hours: document.querySelector("[data-countdown-hours]"),
        minutes: document.querySelector("[data-countdown-minutes]"),
        seconds: document.querySelector("[data-countdown-seconds]")
    };

    const updateCountdown = () => {
        if (!countdown) {
            return;
        }
        const releaseAt = new Date(countdown.dataset.releaseDate).getTime();
        const remaining = Math.max(0, releaseAt - Date.now());
        const totalSeconds = Math.floor(remaining / 1000);
        const values = {
            days: Math.floor(totalSeconds / 86400),
            hours: Math.floor((totalSeconds % 86400) / 3600),
            minutes: Math.floor((totalSeconds % 3600) / 60),
            seconds: totalSeconds % 60
        };

        Object.entries(values).forEach(([key, value]) => {
            if (countdownFields[key]) {
                countdownFields[key].textContent = key === "days" ? String(value) : String(value).padStart(2, "0");
            }
        });

        if (remaining === 0) {
            const note = document.querySelector("[data-countdown-note]");
            if (note) {
                note.textContent = "Launch day has arrived. Time to redeem the mission.";
            }
        }
    };

    updateCountdown();
    window.setInterval(updateCountdown, 1000);

    if (!reduceMotion && finePointer) {
        let pointerFrame = 0;
        window.addEventListener("pointermove", (event) => {
            if (pointerFrame) {
                return;
            }
            pointerFrame = window.requestAnimationFrame(() => {
                root.style.setProperty("--birthday-pointer-x", ((event.clientX / window.innerWidth) - 0.5).toFixed(3));
                root.style.setProperty("--birthday-pointer-y", ((event.clientY / window.innerHeight) - 0.5).toFixed(3));
                pointerFrame = 0;
            });
        }, { passive: true });
    }

    if (!reduceMotion) {
        let scrollFrame = 0;
        window.addEventListener("scroll", () => {
            if (scrollFrame) {
                return;
            }
            scrollFrame = window.requestAnimationFrame(() => {
                const depth = Math.min(window.scrollY, window.innerHeight) * 0.055;
                root.style.setProperty("--birthday-scroll-depth", `${depth.toFixed(1)}px`);
                scrollFrame = 0;
            });
        }, { passive: true });
    }

    fireworksButton?.addEventListener("click", () => {
        launchFireworks();
        burstConfetti(42);
        navigator.vibrate?.([14, 34, 22]);
        if (liveRegion) liveRegion.textContent = reduceMotion ? "Happy Birthday, Dad!" : "Birthday fireworks launched.";
    });

    replayButton?.addEventListener("click", () => {
        unlocked = false;
        experience.classList.remove("is-unlocked");
        experience.dataset.state = "locked";
        holdButton.querySelector("strong").textContent = "Hold to unlock";
        if (holdCaption) {
            holdCaption.textContent = "Press and hold your birthday mission";
        }
        fireworksLayer?.replaceChildren();
        updateHoldProgress(0);
        playIntro();
    });

    window.setTimeout(playIntro, 80);
})();
