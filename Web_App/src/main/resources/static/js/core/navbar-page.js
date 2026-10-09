(function () {
    const button = document.getElementById('siteNavButton');
    const menu = document.getElementById('siteNavMenu');
    if (!button || !menu) return;

    const mobileNavBreakpoint = window.matchMedia('(max-width: 1377px)');
    const header = button.closest('.navheader');
    const overlayManager = window.OneToOneOverlay;
    let lastScrollY = window.scrollY || 0;
    let scrollDirection = 0;
    let downwardTravel = 0;
    let ticking = false;

    const getFocusableMenuItems = () => Array.from(menu.querySelectorAll(
        'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
    )).filter((element) => element.getClientRects().length > 0);

    const syncPageInertState = (open) => {
        const inactiveTargets = document.querySelectorAll(
            'body > main, body > footer, #chatWidget, #quickActionsRoot, #platformPanelRoot'
        );

        inactiveTargets.forEach((target) => {
            if (open && !target.hasAttribute('inert')) {
                target.setAttribute('inert', '');
                target.dataset.siteNavInert = 'true';
            } else if (!open && target.dataset.siteNavInert === 'true') {
                target.removeAttribute('inert');
                delete target.dataset.siteNavInert;
            }
        });
    };

    const setOpen = (open, options = {}) => {
        const nextOpen = mobileNavBreakpoint.matches && open;
        const focusWasInsideMenu = menu.contains(document.activeElement);
        if (nextOpen && !options.fromOverlayManager) {
            overlayManager?.open('site-navigation');
        } else if (!nextOpen && !options.fromOverlayManager) {
            overlayManager?.release('site-navigation');
        }

        menu.classList.toggle('is-open', nextOpen);
        menu.setAttribute('aria-hidden', nextOpen ? 'false' : 'true');
        menu.toggleAttribute('inert', !nextOpen);
        button.setAttribute('aria-expanded', nextOpen ? 'true' : 'false');
        button.setAttribute('aria-label', nextOpen ? 'Close navigation' : 'Open navigation');
        document.body.classList.toggle('site-nav-open', nextOpen);
        syncPageInertState(nextOpen);

        if (header) {
            header.classList.toggle('navheader--menu-open', nextOpen);
        }

        if (nextOpen) {
            window.requestAnimationFrame(() => getFocusableMenuItems()[0]?.focus());
        } else if (options.restoreFocus && focusWasInsideMenu) {
            button.focus();
        }
    };

    const isOpen = () => button.getAttribute('aria-expanded') === 'true';
    const closeMenu = (options) => setOpen(false, options);

    overlayManager?.register('site-navigation', {
        close: (options) => closeMenu({ ...options, fromOverlayManager: true })
    });

    setOpen(false, { fromOverlayManager: true });

    button.addEventListener('click', (event) => {
        event.preventDefault();

        if (!mobileNavBreakpoint.matches) {
            closeMenu();
            return;
        }

        setOpen(!isOpen());
    });

    menu.addEventListener('click', (event) => {
        const actionable = event.target.closest('a, button');
        if (!actionable) return;
        closeMenu();
    });

    document.addEventListener('click', (event) => {
        if (!isOpen()) return;

        if (!menu.contains(event.target) && !button.contains(event.target)) {
            closeMenu();
        }
    });

    document.addEventListener('keydown', (event) => {
        if (!isOpen()) return;

        if (event.key === 'Escape') {
            closeMenu({ restoreFocus: true });
            return;
        }

        if (event.key === 'Tab') {
            const focusableItems = getFocusableMenuItems();
            if (!focusableItems.length) {
                event.preventDefault();
                button.focus();
                return;
            }

            const firstItem = focusableItems[0];
            const lastItem = focusableItems[focusableItems.length - 1];
            if (event.shiftKey && document.activeElement === firstItem) {
                event.preventDefault();
                lastItem.focus();
            } else if (!event.shiftKey && document.activeElement === lastItem) {
                event.preventDefault();
                firstItem.focus();
            }
        }
    });

    const syncLayout = () => {
        if (!mobileNavBreakpoint.matches) {
            closeMenu();
        }
    };

    if (typeof mobileNavBreakpoint.addEventListener === 'function') {
        mobileNavBreakpoint.addEventListener('change', syncLayout);
    } else if (typeof mobileNavBreakpoint.addListener === 'function') {
        mobileNavBreakpoint.addListener(syncLayout);
    }

    window.addEventListener('resize', syncLayout);

    const setHeaderVisible = (visible) => {
        if (!header) return;
        header.classList.toggle('navheader--hidden', !visible);
    };

    const syncScrollVisibility = () => {
        if (!header) return;
        const currentY = Math.max(window.scrollY || 0, 0);
        const delta = currentY - lastScrollY;
        const menuOpen = header.classList.contains('navheader--menu-open');
        const focused = header.contains(document.activeElement);
        const nextDirection = Math.sign(delta);

        if (nextDirection !== 0 && nextDirection !== scrollDirection) {
            scrollDirection = nextDirection;
            downwardTravel = 0;
        }

        if (currentY <= 20 || menuOpen || focused) {
            setHeaderVisible(true);
            downwardTravel = 0;
        } else if (delta < 0) {
            // Reveal on the first upward movement, including touch and trackpad scrolling.
            setHeaderVisible(true);
            downwardTravel = 0;
        } else if (delta > 0) {
            // A short downward dead zone prevents minor scroll jitter from hiding the bar.
            downwardTravel += delta;
            if (downwardTravel >= 12) {
                setHeaderVisible(false);
            }
        }

        header.classList.toggle('scrolled', currentY > 12);
        lastScrollY = currentY;
        ticking = false;
    };

    window.addEventListener('scroll', () => {
        if (!ticking) {
            window.requestAnimationFrame(syncScrollVisibility);
            ticking = true;
        }
    }, { passive: true });

    header?.addEventListener('focusin', () => setHeaderVisible(true));
    window.addEventListener('wheel', (event) => {
        if (event.deltaY < -2) setHeaderVisible(true);
    }, { passive: true });
    window.addEventListener('keydown', (event) => {
        if (['ArrowUp', 'PageUp', 'Home'].includes(event.key)) {
            setHeaderVisible(true);
        }
    });
    syncScrollVisibility();

    const mobileQuickActionsToggle = document.getElementById('mobileQuickActionsToggle');
    if (mobileQuickActionsToggle) {
        mobileQuickActionsToggle.addEventListener('click', (event) => {
            const quickActionsToggle = document.getElementById('quickActionsToggle');
            if (quickActionsToggle) {
                // The same menu click must not reach the shelf's outside-click handler.
                event.stopPropagation();
                closeMenu();
                if (typeof window.toggleQuickActionsShelf === 'function') {
                    window.toggleQuickActionsShelf(button);
                } else {
                    quickActionsToggle.click();
                }
            }
        });
    }

})();
