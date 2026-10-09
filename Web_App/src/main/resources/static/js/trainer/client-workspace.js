(() => {
    'use strict';
    document.querySelectorAll('[data-programme-previews]').forEach(group => {
        const select = group.closest('form')?.querySelector('select');
        if (!select) return;
        const previews = [...group.querySelectorAll('[data-programme-id]')];
        const prompt = group.querySelector('[data-preview-prompt]');
        function update() {
            let selected = false;
            previews.forEach(preview => {
                const matches = preview.dataset.programmeId === select.value;
                preview.hidden = !matches;
                if (!matches) preview.open = false;
                selected ||= matches;
            });
            if (prompt) prompt.hidden = selected || previews.length === 0;
        }
        select.addEventListener('change', update);
        update();
    });
})();
