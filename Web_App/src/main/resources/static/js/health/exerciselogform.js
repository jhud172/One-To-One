(() => {
    const form = document.querySelector('[data-exercise-log-form]');
    const preview = document.querySelector('[data-log-preview]');
    if (!form || !preview) return;
    const commentCount = form.querySelector('[data-log-comment-count]');
    const fields = [...form.elements].filter(field => field.matches('input:not([type=hidden]), textarea, select'));
    const snapshot = () => fields.map(field => field.type === 'radio' || field.type === 'checkbox' ? field.checked : field.value);
    const initialValues = snapshot();
    let submitting = false;
    const update = () => {
        const values = new FormData(form);
        preview.querySelectorAll('[data-preview-field]').forEach(output => {
            const field = output.dataset.previewField;
            const value = String(values.get(field) ?? '');
            output.textContent = value.trim() ? value : (field === 'comments' ? preview.dataset.emptyComments : '—');
        });
        if (commentCount) commentCount.textContent = commentCount.dataset.countFormat.replace('{0}', String(form.elements.namedItem('comments').value.length));
    };
    form.addEventListener('input', update);
    form.addEventListener('change', update);
    form.addEventListener('submit', event => { submitting = !event.defaultPrevented; });
    window.addEventListener('beforeunload', event => {
        if (submitting || snapshot().every((value, index) => value === initialValues[index])) return;
        event.preventDefault();
        event.returnValue = '';
    });
    update();
})();
