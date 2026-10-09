(function () {
    'use strict';
    document.querySelectorAll('.library-exercise-form, .library-workout-form, .library-programme-form, [data-library-draft]').forEach((form) => {
        const previewFields = form.querySelectorAll('[data-exercise-preview], [data-library-preview]');
        const fields = [...form.elements].filter((field) => field.matches('input:not([type=hidden]), textarea, select'));
        const snapshot = () => fields.map((field) =>
            field.type === 'checkbox' || field.type === 'radio' ? field.checked : field.value);
        const initialValues = snapshot();
        let submitting = false;
        function updatePreview() {
            previewFields.forEach((preview) => {
                const field = form.elements.namedItem(preview.dataset.libraryPreview || preview.dataset.exercisePreview);
                if (!field) return;
                preview.textContent = field.tagName === 'SELECT'
                    ? (field.value ? field.selectedOptions[0]?.textContent || field.value : '')
                    : field.value;
                if (preview.hasAttribute('tabindex')) preview.tabIndex = field.value ? 0 : -1;
            });
        }
        form.addEventListener('input', updatePreview);
        form.addEventListener('change', updatePreview);
        form.addEventListener('reset', () => requestAnimationFrame(updatePreview));
        form.addEventListener('submit', (event) => { submitting = !event.defaultPrevented; });
        window.addEventListener('beforeunload', (event) => {
            if (submitting || snapshot().every((value, index) => value === initialValues[index])) return;
            event.preventDefault();
            event.returnValue = '';
        });
        updatePreview();
    });
}());
