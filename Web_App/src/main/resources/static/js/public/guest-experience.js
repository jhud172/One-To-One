(() => {
    const roots = Array.from(document.querySelectorAll('[data-guest-scene]'));
    if (roots.length === 0) {
        return;
    }

    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const canReveal = !reduceMotion && 'IntersectionObserver' in window;
    const revealObserverKey = Symbol('guestRevealObserver');

    if (canReveal) {
        document.documentElement.classList.add('guest-motion-ready');
    }

    roots.forEach((root) => {
        const revealTargets = Array.from(root.querySelectorAll('[data-guest-reveal]'));
        root.addEventListener('focusin', (event) => {
            const target = event.target.closest('[data-guest-reveal]');
            if (target) target.classList.add('is-visible');
        });

        if (!canReveal) {
            revealTargets.forEach((target) => target.classList.add('is-visible'));
        } else if (revealTargets.length > 0) {
            let remainingTargets = revealTargets.length;
            const observer = new IntersectionObserver((entries, activeObserver) => {
                entries.forEach((entry) => {
                    if (!entry.isIntersecting) {
                        return;
                    }
                    entry.target.classList.add('is-visible');
                    activeObserver.unobserve(entry.target);
                    remainingTargets -= 1;

                    if (remainingTargets === 0) {
                        activeObserver.disconnect();
                        delete root[revealObserverKey];
                    }
                });
            }, {
                rootMargin: '0px 0px -10% 0px',
                threshold: 0.08
            });

            // Keep a live reference on the observed scene. A locally scoped
            // observer can otherwise be garbage-collected before the visitor
            // reaches a below-fold section on long public pages.
            root[revealObserverKey] = observer;
            revealTargets.forEach((target) => observer.observe(target));
        }

        if (reduceMotion || !window.matchMedia('(pointer: fine)').matches) {
            return;
        }

        let desiredX = 72;
        let desiredY = 18;
        let renderedX = desiredX;
        let renderedY = desiredY;
        let animationFrame = 0;

        const renderPointerLight = () => {
            renderedX += (desiredX - renderedX) * 0.12;
            renderedY += (desiredY - renderedY) * 0.12;
            root.style.setProperty('--guest-pointer-x', `${renderedX.toFixed(2)}%`);
            root.style.setProperty('--guest-pointer-y', `${renderedY.toFixed(2)}%`);

            if (Math.abs(desiredX - renderedX) > 0.05 || Math.abs(desiredY - renderedY) > 0.05) {
                animationFrame = window.requestAnimationFrame(renderPointerLight);
            } else {
                animationFrame = 0;
            }
        };

        const requestPointerRender = () => {
            if (animationFrame === 0) {
                animationFrame = window.requestAnimationFrame(renderPointerLight);
            }
        };

        root.addEventListener('pointermove', (event) => {
            desiredX = Math.min(100, Math.max(0, (event.clientX / window.innerWidth) * 100));
            desiredY = Math.min(100, Math.max(0, (event.clientY / window.innerHeight) * 100));
            requestPointerRender();
        }, { passive: true });

        root.addEventListener('pointerleave', () => {
            desiredX = 72;
            desiredY = 18;
            requestPointerRender();
        });
    });
})();
