document.addEventListener('DOMContentLoaded', () => {
    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const supportsFinePointer = window.matchMedia('(hover: hover) and (pointer: fine)').matches;
    const usesCoarsePointer = window.matchMedia('(hover: none), (pointer: coarse)').matches;
    const supportsFinePointerMotion = supportsFinePointer && !prefersReducedMotion;

    const createDevModeNotificationController = () => {
        const notice = document.getElementById('devModeHomeNotice');
        if (!notice) {
            return null;
        }

        const card = notice.querySelector('[data-dev-mode-notification-card]');
        const closeButton = notice.querySelector('[data-dev-mode-notification-close]');
        const triggers = Array.from(document.querySelectorAll('[data-dev-mode-trigger]'));
        const navbar = document.querySelector('.navheader');
        const autoDismissMs = Number.parseInt(notice.dataset.autoDismissMs || '5000', 10);
        let autoDismissTimer = null;
        let entranceTimer = null;
        let finishTimer = null;
        let badgeBounceTimer = null;
        let dockBounceTimer = null;
        let lastNavbarHidden = null;

        const visibleTrigger = () => triggers.find((trigger) => {
            const rect = trigger.getBoundingClientRect();
            return rect.width > 0 && rect.height > 0;
        }) || triggers[0] || null;

        const clearTimers = () => {
            window.clearTimeout(autoDismissTimer);
            window.clearTimeout(entranceTimer);
            window.clearTimeout(finishTimer);
            window.clearTimeout(badgeBounceTimer);
            window.clearTimeout(dockBounceTimer);
            notice.classList.remove('is-repositioning');
        };

        const syncNavbarDock = ({ animate = true } = {}) => {
            const navbarHidden = !navbar || navbar.classList.contains('navheader--hidden');

            if (navbar) {
                const navbarHeight = Math.max(navbar.offsetHeight, navbar.getBoundingClientRect().height);
                notice.style.setProperty('--dev-notice-navbar-height', `${Math.ceil(navbarHeight)}px`);
            }

            notice.classList.toggle('is-navbar-detached', navbarHidden);

            const dockChanged = lastNavbarHidden !== null && navbarHidden !== lastNavbarHidden;
            lastNavbarHidden = navbarHidden;
            if (!animate || !dockChanged || notice.hidden || prefersReducedMotion) {
                return;
            }

            window.clearTimeout(dockBounceTimer);
            notice.classList.remove('is-repositioning');
            void notice.offsetWidth;
            notice.classList.add('is-repositioning');
            dockBounceTimer = window.setTimeout(() => {
                notice.classList.remove('is-repositioning');
            }, 520);
        };

        const setTriggerState = (expanded) => {
            triggers.forEach((trigger) => {
                trigger.setAttribute('aria-controls', notice.id);
                trigger.setAttribute('aria-expanded', String(expanded));
            });
        };

        const bounceTrigger = (trigger, className, duration) => {
            if (!trigger || prefersReducedMotion) {
                return;
            }

            trigger.classList.remove('is-launching-notice', 'is-receiving-notice');
            void trigger.offsetWidth;
            trigger.classList.add(className);
            badgeBounceTimer = window.setTimeout(() => trigger.classList.remove(className), duration);
        };

        const setMorphTarget = (preferredTrigger = null) => {
            const preferredRect = preferredTrigger?.getBoundingClientRect();
            const trigger = preferredRect?.width > 0 && preferredRect?.height > 0
                ? preferredTrigger
                : visibleTrigger();
            if (!trigger || !card) {
                return trigger;
            }

            const cardRect = card.getBoundingClientRect();
            const triggerRect = trigger.getBoundingClientRect();
            const targetX = triggerRect.left + (triggerRect.width / 2) - (cardRect.left + (cardRect.width / 2));
            const targetY = triggerRect.top + (triggerRect.height / 2) - (cardRect.top + (cardRect.height / 2));
            const scaleX = Math.max(0.06, Math.min(1, triggerRect.width / cardRect.width));
            const scaleY = Math.max(0.12, Math.min(1, triggerRect.height / cardRect.height));

            notice.style.setProperty('--dev-notice-target-x', `${targetX}px`);
            notice.style.setProperty('--dev-notice-target-y', `${targetY}px`);
            notice.style.setProperty('--dev-notice-target-scale-x', scaleX.toFixed(3));
            notice.style.setProperty('--dev-notice-target-scale-y', scaleY.toFixed(3));
            return trigger;
        };

        const finishDismissal = () => {
            notice.classList.remove('is-visible', 'is-entering', 'is-exiting', 'is-measuring', 'is-repositioning');
            notice.hidden = true;
            notice.setAttribute('aria-hidden', 'true');
            notice.setAttribute('inert', '');
            setTriggerState(false);
        };

        const dismiss = () => {
            if (notice.hidden || notice.classList.contains('is-exiting')) {
                return;
            }

            clearTimers();
            const trigger = setMorphTarget();
            if (notice.contains(document.activeElement) && trigger) {
                trigger.focus({ preventScroll: true });
            }
            notice.setAttribute('aria-hidden', 'true');
            notice.setAttribute('inert', '');
            notice.classList.remove('is-entering');
            notice.classList.add('is-exiting');
            setTriggerState(false);

            if (prefersReducedMotion) {
                finishDismissal();
                return;
            }

            badgeBounceTimer = window.setTimeout(() => {
                bounceTrigger(trigger, 'is-receiving-notice', 720);
            }, 360);
            finishTimer = window.setTimeout(finishDismissal, 620);
        };

        const show = ({ trigger = null } = {}) => {
            clearTimers();
            notice.hidden = false;
            notice.removeAttribute('inert');
            notice.setAttribute('aria-hidden', 'false');
            notice.classList.remove('is-visible', 'is-entering', 'is-exiting', 'is-measuring');
            notice.classList.add('is-measuring');
            syncNavbarDock({ animate: false });

            const activeTrigger = setMorphTarget(trigger);

            if (card) {
                void card.offsetWidth;
            }

            notice.classList.remove('is-measuring');
            notice.classList.add('is-visible');
            if (!prefersReducedMotion) {
                notice.classList.add('is-entering');
                entranceTimer = window.setTimeout(() => notice.classList.remove('is-entering'), 680);
                bounceTrigger(activeTrigger, 'is-launching-notice', 520);
            }

            setTriggerState(true);
            autoDismissTimer = window.setTimeout(dismiss, autoDismissMs);
        };

        closeButton?.addEventListener('click', dismiss);
        triggers.forEach((trigger) => {
            trigger.addEventListener('click', (event) => {
                event.preventDefault();
                show({ trigger });
            });
        });

        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape' && !notice.hidden) {
                dismiss();
            }
        });

        window.addEventListener('resize', () => {
            syncNavbarDock({ animate: false });
            if (!notice.hidden) {
                setMorphTarget();
            }
        }, { passive: true });

        if (navbar) {
            const navbarObserver = new MutationObserver(() => syncNavbarDock());
            navbarObserver.observe(navbar, { attributes: true, attributeFilter: ['class'] });

            if ('ResizeObserver' in window) {
                const navbarResizeObserver = new ResizeObserver(() => syncNavbarDock({ animate: false }));
                navbarResizeObserver.observe(navbar);
            }
        }

        syncNavbarDock({ animate: false });
        setTriggerState(false);
        return { show };
    };

    const devModeNotification = createDevModeNotificationController();
    const showDevModeNotification = () => {
        if (!devModeNotification || usesCoarsePointer) {
            return;
        }
        window.setTimeout(() => devModeNotification.show(), 120);
    };

    // Page actions are available immediately; sculpture motion starts on activation.
    showDevModeNotification();

    const createTabController = ({ tabSelector, panelSelector, keyName, activateOnHover = false }) => {
        const tabs = Array.from(document.querySelectorAll(tabSelector));
        const panels = Array.from(document.querySelectorAll(panelSelector));
        if (!tabs.length || !panels.length) {
            return;
        }

        const activate = (tab, moveFocus = false) => {
            const value = tab.dataset[keyName];
            tabs.forEach((candidate) => {
                const isSelected = candidate === tab;
                candidate.classList.toggle('is-active', isSelected);
                candidate.setAttribute('aria-selected', String(isSelected));
                candidate.tabIndex = isSelected ? 0 : -1;
            });
            panels.forEach((panel) => {
                const isSelected = panel.dataset[keyName.replace('Tab', 'Panel')] === value;
                panel.classList.toggle('is-active', isSelected);
                panel.hidden = !isSelected;
            });
            if (moveFocus) {
                tab.focus();
            }
        };

        tabs.forEach((tab, index) => {
            tab.addEventListener('click', () => activate(tab));
            if (activateOnHover) {
                tab.addEventListener('pointerenter', () => {
                    if (supportsFinePointer) {
                        activate(tab);
                    }
                });
            }
            tab.addEventListener('keydown', (event) => {
                if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) {
                    return;
                }
                event.preventDefault();
                let nextIndex = index;
                if (event.key === 'ArrowRight') nextIndex = (index + 1) % tabs.length;
                if (event.key === 'ArrowLeft') nextIndex = (index - 1 + tabs.length) % tabs.length;
                if (event.key === 'Home') nextIndex = 0;
                if (event.key === 'End') nextIndex = tabs.length - 1;
                activate(tabs[nextIndex], true);
            });
        });
    };

    createTabController({
        tabSelector: '[data-preview-tab]',
        panelSelector: '[data-preview-panel]',
        keyName: 'previewTab'
    });
    createTabController({
        tabSelector: '[data-workspace-tab]',
        panelSelector: '[data-workspace-panel]',
        keyName: 'workspaceTab',
        activateOnHover: true
    });

    const workspaceScenes = Array.from(document.querySelectorAll('[data-workspace-scene]'));
    const supportsWorkspaceDepth = supportsFinePointerMotion;
    if (supportsWorkspaceDepth) {
        workspaceScenes.forEach((scene) => {
            scene.addEventListener('pointermove', (event) => {
                const bounds = scene.getBoundingClientRect();
                const x = Math.max(0, Math.min(1, (event.clientX - bounds.left) / bounds.width));
                const y = Math.max(0, Math.min(1, (event.clientY - bounds.top) / bounds.height));
                scene.style.setProperty('--workspace-image-x', `${((0.5 - x) * 9).toFixed(1)}px`);
                scene.style.setProperty('--workspace-image-y', `${((0.5 - y) * 6).toFixed(1)}px`);
            });
            scene.addEventListener('pointerleave', () => {
                scene.style.setProperty('--workspace-image-x', '0px');
                scene.style.setProperty('--workspace-image-y', '0px');
            });
        });
    }

    const finalSurface = document.querySelector('[data-final-cta]');
    const finalCta = finalSurface?.querySelector('[data-final-cta-panel]');
    const supportsFinalDepth = finalSurface && finalCta && supportsFinePointerMotion;
    if (supportsFinalDepth) {
        const neutralPose = {
            rotateX: 0,
            rotateY: 0,
            lightX: 78,
            lightY: 18
        };
        const targetPose = {...neutralPose};
        const currentPose = {...neutralPose};
        let finalFrame = null;
        let previousFinalFrameTime = 0;
        let finalPointer = null;
        let finalIsEngaged = false;

        const applyFinalPose = () => {
            finalCta.style.setProperty('--final-rotate-x', `${currentPose.rotateX.toFixed(3)}deg`);
            finalCta.style.setProperty('--final-rotate-y', `${currentPose.rotateY.toFixed(3)}deg`);
            finalCta.style.setProperty('--final-light-x', `${currentPose.lightX.toFixed(2)}%`);
            finalCta.style.setProperty('--final-light-y', `${currentPose.lightY.toFixed(2)}%`);
        };

        const finalPoseIsSettled = () => (
            Math.abs(targetPose.rotateX - currentPose.rotateX) < 0.003
            && Math.abs(targetPose.rotateY - currentPose.rotateY) < 0.003
            && Math.abs(targetPose.lightX - currentPose.lightX) < 0.04
            && Math.abs(targetPose.lightY - currentPose.lightY) < 0.04
        );

        const animateFinalPose = (frameTime) => {
            const elapsed = previousFinalFrameTime
                ? Math.min((frameTime - previousFinalFrameTime) / 1000, 0.05)
                : 1 / 60;
            previousFinalFrameTime = frameTime;
            const responsiveness = finalIsEngaged ? 12.5 : 7.5;
            const blend = 1 - Math.exp(-responsiveness * elapsed);

            Object.keys(currentPose).forEach((key) => {
                currentPose[key] += (targetPose[key] - currentPose[key]) * blend;
            });
            applyFinalPose();

            if (finalPoseIsSettled()) {
                Object.assign(currentPose, targetPose);
                applyFinalPose();
                finalFrame = null;
                previousFinalFrameTime = 0;
                return;
            }
            finalFrame = window.requestAnimationFrame(animateFinalPose);
        };

        const queueFinalAnimation = () => {
            if (finalFrame === null) {
                finalFrame = window.requestAnimationFrame(animateFinalPose);
            }
        };

        const resetFinalPose = () => {
            finalIsEngaged = false;
            finalPointer = null;
            Object.assign(targetPose, neutralPose);
            queueFinalAnimation();
        };

        const syncFinalTargetToPointer = () => {
            if (!finalIsEngaged || !finalPointer) {
                return;
            }

            const bounds = finalSurface.getBoundingClientRect();
            const pointerIsInside = finalPointer.x >= bounds.left
                && finalPointer.x <= bounds.right
                && finalPointer.y >= bounds.top
                && finalPointer.y <= bounds.bottom;
            if (!pointerIsInside || bounds.width <= 0 || bounds.height <= 0) {
                resetFinalPose();
                return;
            }

            const x = Math.max(0, Math.min(1, (finalPointer.x - bounds.left) / bounds.width));
            const y = Math.max(0, Math.min(1, (finalPointer.y - bounds.top) / bounds.height));
            targetPose.rotateX = (0.5 - y) * 2.4;
            targetPose.rotateY = (x - 0.5) * 3.4;
            targetPose.lightX = 38 + (x * 54);
            targetPose.lightY = 8 + (y * 54);
            queueFinalAnimation();
        };

        const trackFinalPointer = (event) => {
            finalIsEngaged = true;
            finalPointer = {x: event.clientX, y: event.clientY};
            syncFinalTargetToPointer();
        };

        finalSurface.addEventListener('pointerenter', trackFinalPointer);
        finalSurface.addEventListener('pointermove', trackFinalPointer);
        finalSurface.addEventListener('pointerleave', resetFinalPose);
        finalSurface.addEventListener('pointercancel', resetFinalPose);
        window.addEventListener('scroll', syncFinalTargetToPointer, {passive: true});
        window.addEventListener('resize', syncFinalTargetToPointer, {passive: true});
        window.addEventListener('blur', resetFinalPose);
        document.addEventListener('visibilitychange', () => {
            if (document.hidden) {
                resetFinalPose();
            }
        });
    }

    const createProductDemoController = () => {
        const stage = document.querySelector('[data-product-stage]');
        if (!stage) {
            return;
        }

        const previewTabs = Array.from(stage.querySelectorAll('[data-preview-tab]'));
        const railButtons = Array.from(stage.querySelectorAll('[data-product-nav]'));
        const resetButton = stage.querySelector('[data-demo-reset]');
        const sessionControl = stage.querySelector('[data-session-control]');
        const completeControl = stage.querySelector('[data-complete-exercise]');
        const progressTrack = stage.querySelector('[data-session-progress-track]');
        const progressFill = stage.querySelector('[data-session-progress]');
        const readiness = stage.querySelector('[data-demo-readiness]');
        const remaining = stage.querySelector('[data-session-remaining]');
        const sessionLabel = stage.querySelector('[data-session-label]');
        const syncLabel = stage.querySelector('[data-demo-sync]');
        const demoFeedback = stage.querySelector('[data-demo-feedback]');
        const exercises = Array.from(stage.querySelectorAll('[data-demo-exercise]'));
        const coachDrawer = stage.querySelector('[data-coach-drawer]');
        const coachClose = stage.querySelector('[data-coach-close]');
        const coachMessage = stage.querySelector('[data-coach-message]');
        const coachSend = stage.querySelector('[data-coach-send]');
        const coachFeedback = stage.querySelector('[data-coach-feedback]');
        const translated = (name, fallback) => stage.dataset[name] || fallback;
        const formatTranslated = (template, ...values) => template.replace(
            /\{(\d+)}/g,
            (match, index) => values[Number(index)] ?? match
        );
        const numberValues = (name, fallback) => translated(name, fallback)
            .split(',')
            .map((value) => Number.parseInt(value.trim(), 10))
            .filter(Number.isFinite);
        const textValues = (name, fallback) => translated(name, fallback).split('|');
        const completeMarkerMarkup = exercises[0]?.querySelector(':scope > span')?.innerHTML || '';
        const initialExercises = exercises.map((exercise) => ({
            className: exercise.className,
            markerClass: exercise.firstElementChild?.className || '',
            markerMarkup: exercise.firstElementChild?.innerHTML || '',
            status: exercise.querySelector('[data-exercise-status]')?.textContent || ''
        }));
        let activeExerciseIndex = 1;
        let completedExercises = 1;
        let sessionRunning = false;
        let sessionComplete = false;

        const setRailState = (value) => {
            railButtons.forEach((button) => {
                button.classList.toggle('is-active', button.dataset.productNav === value);
            });
        };

        const closeCoachDrawer = ({ restoreNavigation = true } = {}) => {
            if (!coachDrawer || coachDrawer.hidden) {
                return;
            }
            coachDrawer.hidden = true;
            if (restoreNavigation) {
                const selectedTab = previewTabs.find((tab) => tab.getAttribute('aria-selected') === 'true');
                setRailState(selectedTab?.dataset.previewTab || 'today');
            }
        };

        const openCoachDrawer = () => {
            if (!coachDrawer) {
                return;
            }
            coachDrawer.hidden = false;
            setRailState('coach');
            coachMessage?.focus();
        };

        previewTabs.forEach((tab) => {
            tab.addEventListener('click', () => {
                closeCoachDrawer({ restoreNavigation: false });
                setRailState(tab.dataset.previewTab);
            });
        });

        railButtons.forEach((button) => {
            button.addEventListener('click', () => {
                const destination = button.dataset.productNav;
                if (destination === 'coach') {
                    openCoachDrawer();
                    return;
                }
                closeCoachDrawer({ restoreNavigation: false });
                previewTabs.find((tab) => tab.dataset.previewTab === destination)?.click();
                setRailState(destination);
            });
        });

        coachClose?.addEventListener('click', () => {
            closeCoachDrawer();
            railButtons.find((button) => button.classList.contains('is-active'))?.focus();
        });

        coachSend?.addEventListener('click', () => {
            const message = coachMessage?.value.trim() || '';
            if (!message) {
                if (coachFeedback) {
                    coachFeedback.textContent = translated(
                        'i18nNoteRequired',
                        'Add a short session note before sending.'
                    );
                }
                coachMessage?.focus();
                return;
            }
            if (coachFeedback) {
                coachFeedback.textContent = translated('i18nNoteSent', 'Update sent to Coach Alex.');
            }
            if (syncLabel) {
                syncLabel.textContent = translated('i18nSyncSent', 'Coach update sent');
            }
            if (coachMessage) {
                coachMessage.value = '';
            }
        });

        const setExerciseState = (exercise, index, state) => {
            const marker = exercise.firstElementChild;
            const status = exercise.querySelector('[data-exercise-status]');
            exercise.classList.toggle('is-complete', state === 'complete');
            exercise.classList.toggle('is-current', state === 'current');

            if (marker) {
                marker.className = state === 'complete' ? 'exercise-list__state' : 'exercise-list__index';
                marker.innerHTML = state === 'complete'
                    ? completeMarkerMarkup
                    : String(index + 1).padStart(2, '0');
            }
            if (status) {
                status.textContent = state === 'complete'
                    ? (index === 0 ? initialExercises[0].status : translated('i18nCompleted', 'Completed'))
                    : (state === 'current'
                        ? translated('i18nCurrent', 'In progress')
                        : translated('i18nNext', 'Up next'));
            }
        };

        const renderSessionState = () => {
            const progressValues = numberValues('i18nProgressValues', '0,38,70,100');
            const readinessValues = numberValues('i18nReadinessValues', '82,86,90,94');
            const remainingValues = textValues(
                'i18nRemainingValues',
                '45 min remaining|32 min remaining|18 min remaining|Session complete'
            );
            const progress = progressValues[completedExercises] ?? 100;

            exercises.forEach((exercise, index) => {
                if (index < completedExercises) {
                    setExerciseState(exercise, index, 'complete');
                } else if (!sessionComplete && index === activeExerciseIndex) {
                    setExerciseState(exercise, index, 'current');
                } else {
                    setExerciseState(exercise, index, 'upcoming');
                }
            });

            if (progressFill) {
                progressFill.style.width = `${progress}%`;
            }
            progressTrack?.setAttribute('aria-valuenow', String(progress));
            if (readiness) {
                readiness.textContent = String(readinessValues[completedExercises] ?? 94);
            }
            if (remaining) {
                remaining.textContent = remainingValues[completedExercises]
                    || translated('i18nSessionComplete', 'Session complete');
            }

            stage.classList.toggle('is-demo-running', sessionRunning);
            sessionControl?.classList.toggle('is-running', sessionRunning);
            sessionControl?.setAttribute('aria-pressed', String(sessionRunning));
            if (sessionControl) {
                sessionControl.disabled = sessionComplete;
            }
            if (completeControl) {
                completeControl.disabled = !sessionRunning || sessionComplete;
                const exerciseName = exercises[activeExerciseIndex]?.querySelector('strong')?.textContent;
                completeControl.textContent = exerciseName
                    ? formatTranslated(
                        translated('i18nCompletePattern', 'Complete {0}'),
                        exerciseName
                    )
                    : formatTranslated(
                        translated('i18nCompletePattern', 'Complete {0}'),
                        translated('i18nExercise', 'exercise').toLocaleLowerCase(document.documentElement.lang)
                    );
            }

            if (sessionLabel) {
                sessionLabel.textContent = sessionComplete
                    ? translated('i18nSessionComplete', 'Session complete')
                    : (sessionRunning
                        ? translated('i18nPause', 'Pause session')
                        : (completedExercises > 1
                            ? translated('i18nResume', 'Resume session')
                            : translated('i18nStart', 'Start session')));
            }
            if (syncLabel) {
                syncLabel.textContent = sessionComplete
                    ? translated('i18nCoachNotified', 'Coach notified')
                    : (sessionRunning
                        ? translated('i18nSessionLive', 'Session live')
                        : (completedExercises > 1
                            ? translated('i18nProgressSaved', 'Progress saved')
                            : translated('i18nSyncInitial', 'Synced with coach')));
            }
        };

        sessionControl?.addEventListener('click', () => {
            if (sessionComplete) {
                return;
            }
            sessionRunning = !sessionRunning;
            if (demoFeedback) {
                const exerciseName = exercises[activeExerciseIndex]?.querySelector('strong')?.textContent
                    || translated('i18nExercise', 'Exercise');
                demoFeedback.textContent = sessionRunning
                    ? formatTranslated(
                        translated(
                            'i18nExerciseActive',
                            '{0} is active. Complete it when the working sets are finished.'
                        ),
                        exerciseName
                    )
                    : translated('i18nSessionPaused', 'Session paused. Your progress is saved in this demo.');
            }
            renderSessionState();
        });

        completeControl?.addEventListener('click', () => {
            if (!sessionRunning || sessionComplete) {
                return;
            }
            const completedName = exercises[activeExerciseIndex]?.querySelector('strong')?.textContent
                || translated('i18nExercise', 'Exercise');
            completedExercises += 1;
            if (completedExercises >= exercises.length) {
                activeExerciseIndex = -1;
                sessionRunning = false;
                sessionComplete = true;
                if (demoFeedback) {
                    demoFeedback.textContent = translated(
                        'i18nSessionFinished',
                        'Session complete. Coach Alex has been notified and your progress view is updated.'
                    );
                }
            } else {
                activeExerciseIndex = completedExercises;
                const nextName = exercises[activeExerciseIndex]?.querySelector('strong')?.textContent
                    || translated('i18nNextExercise', 'the next exercise');
                if (demoFeedback) {
                    demoFeedback.textContent = formatTranslated(
                        translated('i18nExerciseNext', '{0} complete. {1} is ready.'),
                        completedName,
                        nextName
                    );
                }
            }
            renderSessionState();
        });

        const resetDemo = () => {
            activeExerciseIndex = 1;
            completedExercises = 1;
            sessionRunning = false;
            sessionComplete = false;
            exercises.forEach((exercise, index) => {
                const snapshot = initialExercises[index];
                exercise.className = snapshot.className;
                if (exercise.firstElementChild) {
                    exercise.firstElementChild.className = snapshot.markerClass;
                    exercise.firstElementChild.innerHTML = snapshot.markerMarkup;
                }
                const status = exercise.querySelector('[data-exercise-status]');
                if (status) {
                    status.textContent = snapshot.status;
                }
            });
            if (demoFeedback) {
                demoFeedback.textContent = translated(
                    'i18nFeedbackInitial',
                    'Start the session, then complete each exercise to update your live plan.'
                );
            }
            if (coachFeedback) {
                coachFeedback.textContent = translated(
                    'i18nDemoLocal',
                    'This demo message stays on this page.'
                );
            }
            if (coachMessage) {
                coachMessage.value = '';
            }
            closeCoachDrawer({ restoreNavigation: false });
            previewTabs.find((tab) => tab.dataset.previewTab === 'today')?.click();
            setRailState('today');
            renderSessionState();
        };

        resetButton?.addEventListener('click', resetDemo);

        const supportsStageTilt = supportsFinePointerMotion;
        const setStagePose = (x, y) => {
            stage.style.setProperty('--stage-rotate-x', `${(-y * 6.4).toFixed(2)}deg`);
            stage.style.setProperty('--stage-rotate-y', `${(x * 9.6).toFixed(2)}deg`);
            stage.style.setProperty('--stage-light-x', `${50 + (x * 34)}%`);
            stage.style.setProperty('--stage-light-y', `${35 + (y * 28)}%`);
        };

        if (supportsStageTilt) {
            stage.addEventListener('pointermove', (event) => {
                if (event.buttons !== 1
                    || event.target.closest('button, a, input, textarea, [role="tab"]')) {
                    return;
                }
                const bounds = stage.getBoundingClientRect();
                setStagePose(
                    ((event.clientX - bounds.left) / bounds.width) - 0.5,
                    ((event.clientY - bounds.top) / bounds.height) - 0.5
                );
            });
            stage.addEventListener('pointerup', () => setStagePose(0, 0));
            stage.addEventListener('pointercancel', () => setStagePose(0, 0));
            stage.addEventListener('pointerleave', () => setStagePose(0, 0));
            stage.addEventListener('blur', () => setStagePose(0, 0));
        }

        stage.addEventListener('keydown', (event) => {
            if (event.key === 'Escape' && coachDrawer && !coachDrawer.hidden) {
                closeCoachDrawer();
                return;
            }
            if (event.target !== stage || !supportsStageTilt) {
                return;
            }
            const poses = {
                ArrowLeft: [-0.45, 0],
                ArrowRight: [0.45, 0],
                ArrowUp: [0, -0.4],
                ArrowDown: [0, 0.4]
            };
            if (!poses[event.key]) {
                return;
            }
            event.preventDefault();
            setStagePose(...poses[event.key]);
        });

        renderSessionState();
    };

    createProductDemoController();

    const createHomeChapterNavigation = () => {
        const chapters = Array.from(document.querySelectorAll('[data-home-chapter]'));
        const chapterLinks = Array.from(document.querySelectorAll('[data-home-chapter-next]'));
        const toTopTrigger = document.querySelector('[data-home-to-top-trigger]');
        const toTop = document.querySelector('[data-home-to-top]');
        if (!chapters.length || !toTopTrigger || !toTop) {
            return;
        }

        document.documentElement.classList.add('home-chapters-ready');

        const scrollToChapter = (event) => {
            const targetId = event.currentTarget.getAttribute('href');
            const target = targetId?.startsWith('#') ? document.querySelector(targetId) : null;
            if (!target) {
                return;
            }

            event.preventDefault();
            target.scrollIntoView({
                behavior: prefersReducedMotion ? 'auto' : 'smooth',
                block: 'start'
            });
        };

        chapterLinks.forEach((link) => link.addEventListener('click', scrollToChapter));
        toTop.addEventListener('click', (event) => {
            scrollToChapter(event);
            toTop.classList.remove('is-visible');
            toTop.setAttribute('aria-hidden', 'true');
            toTop.tabIndex = -1;
        });

        let updateQueued = false;
        const updateToTop = () => {
            updateQueued = false;
            const navigationOffset = Number.parseFloat(
                window.getComputedStyle(document.documentElement).scrollPaddingTop
            ) || 0;
            const triggerIsRendered = toTopTrigger.getClientRects().length > 0;
            const visibleTriggerBottom = triggerIsRendered
                ? toTopTrigger.getBoundingClientRect().bottom
                : chapters[0].getBoundingClientRect().bottom;
            const hasPassedHowItWorks = visibleTriggerBottom <= navigationOffset;
            toTop.classList.toggle('is-visible', hasPassedHowItWorks);
            toTop.setAttribute('aria-hidden', String(!hasPassedHowItWorks));
            toTop.tabIndex = hasPassedHowItWorks ? 0 : -1;
        };

        const queueToTopUpdate = () => {
            if (updateQueued) {
                return;
            }
            updateQueued = true;
            window.requestAnimationFrame(updateToTop);
        };

        updateToTop();
        window.addEventListener('scroll', queueToTopUpdate, { passive: true });
        window.addEventListener('resize', queueToTopUpdate, { passive: true });
    };

    createHomeChapterNavigation();

    const createStandardExperience = (controller) => {
        const tabs = Array.from(controller.querySelectorAll('[data-standard-tab]'));
        const panels = Array.from(controller.querySelectorAll('[data-standard-panel]'));
        const proof = controller.querySelector('[data-standard-proof]');
        if (!tabs.length || !panels.length || !proof) return;

        let activeKey = tabs.find((tab) => tab.classList.contains('is-current'))?.dataset.standardTab || tabs[0].dataset.standardTab;

        const activate = (key, shouldFocus = false) => {
            const nextTab = tabs.find((tab) => tab.dataset.standardTab === key);
            const nextPanel = panels.find((panel) => panel.dataset.standardPanel === key);
            if (!nextTab || !nextPanel) return;

            activeKey = key;
            tabs.forEach((tab) => {
                const isActive = tab === nextTab;
                tab.classList.toggle('is-current', isActive);
                tab.setAttribute('aria-selected', String(isActive));
                tab.tabIndex = isActive ? 0 : -1;
            });
            panels.forEach((panel) => {
                const isActive = panel === nextPanel;
                panel.hidden = !isActive;
                panel.classList.toggle('is-active', isActive);
            });
            proof.dataset.activeProof = key;
            if (shouldFocus) nextTab.focus();
        };

        tabs.forEach((tab, index) => {
            tab.addEventListener('click', () => activate(tab.dataset.standardTab));
            tab.addEventListener('pointerenter', () => {
                if (supportsFinePointer) {
                    activate(tab.dataset.standardTab);
                }
            });
            tab.addEventListener('keydown', (event) => {
                let nextIndex = index;
                if (event.key === 'ArrowDown' || event.key === 'ArrowRight') nextIndex = (index + 1) % tabs.length;
                else if (event.key === 'ArrowUp' || event.key === 'ArrowLeft') nextIndex = (index - 1 + tabs.length) % tabs.length;
                else if (event.key === 'Home') nextIndex = 0;
                else if (event.key === 'End') nextIndex = tabs.length - 1;
                else return;
                event.preventDefault();
                activate(tabs[nextIndex].dataset.standardTab, true);
            });
        });

        const supportsProofDepth = supportsFinePointerMotion;
        if (supportsProofDepth) {
            proof.addEventListener('pointermove', (event) => {
                const bounds = proof.getBoundingClientRect();
                const x = Math.max(0, Math.min(1, (event.clientX - bounds.left) / bounds.width));
                const y = Math.max(0, Math.min(1, (event.clientY - bounds.top) / bounds.height));
                proof.style.setProperty('--standard-pointer-x', `${(x * 100).toFixed(1)}%`);
                proof.style.setProperty('--standard-pointer-y', `${(y * 100).toFixed(1)}%`);
                proof.style.transform = `perspective(1300px) rotateX(${((0.5 - y) * 2.6).toFixed(2)}deg) rotateY(${((x - 0.5) * 3.8).toFixed(2)}deg) translateY(-2px)`;
            });
            proof.addEventListener('pointerleave', () => {
                proof.style.removeProperty('transform');
                proof.style.setProperty('--standard-pointer-x', '58%');
                proof.style.setProperty('--standard-pointer-y', '36%');
            });
        }

        activate(activeKey);
    };

    document.querySelectorAll('[data-standard-controller]').forEach(createStandardExperience);

    // SCROLL REVEAL
    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add('in-view');
                // Optional: unobserve after revealing
                // observer.unobserve(entry.target);
            }
        });
    }, { threshold: 0.1 });

    document.querySelectorAll('.reveal-up').forEach((el) => observer.observe(el));

    const feedbackType = document.getElementById('feedback-type');
    const allowReply = document.getElementById('allow-email-reply');
    const queryReplyHint = document.getElementById('query-reply-hint');

    function syncReplyHint() {
        if (!feedbackType || !allowReply || !queryReplyHint) return;
        const isQuery = feedbackType.value === 'QUERY';
        queryReplyHint.classList.toggle('hidden', !isQuery);
        if (isQuery) {
            allowReply.required = true;
        } else {
            allowReply.required = false;
        }
    }

    if (feedbackType) {
        feedbackType.addEventListener('change', syncReplyHint);
        syncReplyHint();
    }
});
