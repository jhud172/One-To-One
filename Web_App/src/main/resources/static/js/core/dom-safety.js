(() => {
    'use strict';
    const entities = {'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'};
    window.OneToOneDom = Object.freeze({
        escapeHtml(value) {
            return String(value ?? '').replace(/[&<>"']/g, character => entities[character]);
        }
    });
})();
