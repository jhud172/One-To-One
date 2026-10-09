// Day view swipe navigation
document.addEventListener("DOMContentLoaded", () => {
    const dayContainer = document.querySelector('[data-testid="day-hub-header"]');
    if (!dayContainer) return;

    const mainContent = document.getElementById('day-main-content');
    const reduceMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    if (mainContent) {
        const enterDirection = sessionStorage.getItem('day-nav-enter-direction');
        if (enterDirection && !reduceMotion) {
            mainContent.classList.add(enterDirection === 'from-next' ? 'day-nav-enter-left' : 'day-nav-enter-right');
            sessionStorage.removeItem('day-nav-enter-direction');
            window.setTimeout(() => {
                mainContent.classList.remove('day-nav-enter-left', 'day-nav-enter-right');
            }, 260);
        }
    }

    let swipeEnabled = false;
    let touchStartX = 0;
    let touchEndX = 0;
    let touchStartY = 0;
    let touchEndY = 0;

    const minSwipeDistance = 50; // Minimum distance for a swipe
    const maxVerticalDistance = 100; // Maximum vertical movement allowed

    /** Animate exit then navigate */
    function navigateWithTransition(href, direction) {
        if (!mainContent || reduceMotion) {
            if (direction && !reduceMotion) {
                sessionStorage.setItem('day-nav-enter-direction', direction === 'next' ? 'from-next' : 'from-prev');
            }
            window.location.href = href;
            return;
        }

        mainContent.classList.remove('day-nav-leave-left', 'day-nav-leave-right');
        mainContent.classList.add(direction === 'next' ? 'day-nav-leave-left' : 'day-nav-leave-right');
        sessionStorage.setItem('day-nav-enter-direction', direction === 'next' ? 'from-next' : 'from-prev');

        setTimeout(() => { window.location.href = href; }, 180);
    }

    function handleSwipe() {
        const horizontalDistance = touchEndX - touchStartX;
        const verticalDistance = Math.abs(touchEndY - touchStartY);

        // Only process horizontal swipes (prevent interference with vertical scrolling)
        if (verticalDistance > maxVerticalDistance) return;

        if (Math.abs(horizontalDistance) < minSwipeDistance) return;

        if (horizontalDistance > 0) {
            // Swiped right - go to previous day
            const prevLink = document.querySelector('a[data-day-navigation="prev"]');
            if (prevLink) navigateWithTransition(prevLink.href, 'prev');
        } else {
            // Swiped left - go to next day
            const nextLink = document.querySelector('a[data-day-navigation="next"]');
            if (nextLink) navigateWithTransition(nextLink.href, 'next');
        }
    }

    // Touch events for mobile
    dayContainer.addEventListener('touchstart', (e) => {
        swipeEnabled = !e.target.closest('a, button, input, select, textarea, [contenteditable]');
        if (!swipeEnabled) return;
        touchStartX = e.changedTouches[0].screenX;
        touchStartY = e.changedTouches[0].screenY;
    });

    dayContainer.addEventListener('touchend', (e) => {
        if (!swipeEnabled) return;
        touchEndX = e.changedTouches[0].screenX;
        touchEndY = e.changedTouches[0].screenY;
        handleSwipe();
    });

    // Keyboard navigation
    document.addEventListener('keydown', (e) => {
        // Section tabs, dialogs and ordinary controls own their keyboard events.
        if (e.defaultPrevented || e.ctrlKey || e.metaKey || e.altKey || e.shiftKey
            || e.target.closest('a, button, input, textarea, select, [contenteditable], [role="tab"], [role="dialog"]')) return;

        if (e.key === 'ArrowLeft') {
            e.preventDefault();
            const prevLink = document.querySelector('a[data-day-navigation="prev"]');
            if (prevLink) navigateWithTransition(prevLink.href, 'prev');
        } else if (e.key === 'ArrowRight') {
            e.preventDefault();
            const nextLink = document.querySelector('a[data-day-navigation="next"]');
            if (nextLink) navigateWithTransition(nextLink.href, 'next');
        }
    });

    // Smooth transition on nav link clicks (prev/next day buttons)
    document.querySelectorAll('a[data-day-navigation="prev"], a[data-day-navigation="next"]').forEach(link => {
        link.addEventListener('click', (e) => {
            if (e.ctrlKey || e.metaKey || e.altKey || e.shiftKey || e.button !== 0) return;
            e.preventDefault();
            const direction = link.dataset.dayNavigation;
            navigateWithTransition(link.href, direction);
        });
    });
});
