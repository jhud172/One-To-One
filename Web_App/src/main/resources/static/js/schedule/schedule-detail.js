(() => {
    'use strict';
    const form=document.getElementById('schedule-detail-form');
    if(!form) return;
    const root=form.closest('.schedule-detail');
    const rows=form.querySelector('[data-detail-rows]');
    const undo=form.querySelector('[data-detail-undo]');
    const status=form.querySelector('[data-detail-status]');
    const history=[];
    let submitting=false;
    let serverDraft=root.dataset.changed==='true';
    function state() {
        return JSON.stringify([...new FormData(form)].filter(([key])=>!['addDay','addMovement','_csrf','revision'].includes(key)));
    }
    const initial=state();
    function snapshot() {
        history.push([...rows.children].map(row=>{
            const clone=row.cloneNode(true);
            row.querySelectorAll('select').forEach((select,index)=>{clone.querySelectorAll('select')[index].value=select.value;});
            return clone;
        }));
        if(history.length>20) history.shift();
    }
    function refresh() {
        const items=[...rows.children];
        const days=items.map(row=>row.querySelector('[data-day-select]').value);
        items.forEach((row,index)=>{
            row.querySelector('[data-row-index]').textContent=String(index+1);
            for(const field of ['day','movement']) {
                const id=`detail-${field}-${index}`;
                row.querySelector(`[data-${field}-select]`).id=id;
                row.querySelector(`[data-${field}-label]`).htmlFor=id;
            }
            row.querySelectorAll('[data-detail-command]').forEach(button=>{
                const command=button.dataset.detailCommand;
                button.value=`${command}:${index}`;
                button.disabled=command==='up' ? !days.slice(0,index).includes(days[index])
                    : command==='down' ? !days.slice(index+1).includes(days[index]) : false;
                button.setAttribute('aria-label',`${button.textContent.trim()}: ${row.querySelector('[data-row-heading]').textContent}`);
            });
        });
        form.querySelectorAll('[data-preview-day]').forEach(day=>{
            day.querySelector('[data-day-count]').textContent=String(days.filter(value=>value===day.dataset.previewDay).length);
        });
        const total=form.querySelector('[data-detail-total]');
        total.textContent=total.dataset.template.replace('COUNTPLACEHOLDER',String(items.length));
        const empty=form.querySelector('[data-detail-empty]');
        if(empty) empty.hidden=items.length!==0;
        undo.hidden=history.length===0;
        status.textContent=serverDraft || state()!==initial ? status.dataset.message : '';
    }
    form.addEventListener('click',event=>{
        const button=event.target.closest('[data-detail-command]');
        if(!button || button.disabled) return;
        event.preventDefault();
        const row=button.closest('.schedule-placement');
        const items=[...rows.children],index=items.indexOf(row);
        snapshot();
        if(button.dataset.detailCommand==='remove') row.remove();
        else {
            const step=button.dataset.detailCommand==='up' ? -1 : 1;
            const day=row.querySelector('[data-day-select]').value;
            for(let candidate=index+step;candidate>=0 && candidate<items.length;candidate+=step) {
                if(items[candidate].querySelector('[data-day-select]').value!==day) continue;
                // Swap same-day positions while retaining rows from other days between them.
                const marker=document.createComment('row-position');
                row.replaceWith(marker); items[candidate].replaceWith(row); marker.replaceWith(items[candidate]);
                break;
            }
        }
        refresh();
        const target=row.isConnected ? row : rows.children[Math.min(index,rows.children.length-1)];
        if(target) {target.classList.add('is-changed');target.querySelector('[data-row-heading]').focus();}
        else document.getElementById('detail-add-day').focus();
    });
    undo.addEventListener('click',()=>{
        const previous=history.pop(); if(!previous) return;
        rows.replaceChildren(...previous); refresh(); rows.querySelector('[data-row-heading]')?.focus();
    });
    form.addEventListener('change',event=>{
        const select=event.target.closest('[data-movement-select]');
        if(select) select.closest('.schedule-placement').querySelector('[data-row-heading]').textContent=select.selectedOptions[0].textContent;
        refresh();
    });
    form.addEventListener('submit',()=>{submitting=true;});
    window.addEventListener('beforeunload',event=>{
        if(!submitting && (serverDraft || state()!==initial)) {event.preventDefault();event.returnValue='';}
    });
    root.querySelectorAll('a[href]').forEach(link=>link.addEventListener('click',event=>{
        if((serverDraft || state()!==initial) && !window.confirm(root.dataset.leave)) event.preventDefault();
        else submitting=true;
    }));
    refresh();
})();
