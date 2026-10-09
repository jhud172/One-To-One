import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';
import assert from 'node:assert/strict';

const source = readFileSync(new URL('../../src/main/resources/static/js/chat/coach-chat.js', import.meta.url), 'utf8');
const settle = () => new Promise(resolve => setImmediate(resolve));

function setup(fetchResponse) {
    const elements = new Map();
    class Element {
        constructor(id = '') { this.id = id; this.children = []; this.events = new Map(); this.dataset = {}; this.value = ''; this.attributes = {}; this.isConnected = true; this.classList = {add() {}, remove() {}, toggle() { return true; }}; }
        set innerHTML(value) { if (value) throw new Error('Untrusted HTML insertion'); this.children = []; }
        set textContent(value) { this.text = String(value); this.children = []; }
        get textContent() { return this.text || ''; }
        addEventListener(name, fn) { this.events.set(name, fn); }
        appendChild(child) { child.parent = this; this.children.push(child); if (child.id) elements.set(child.id, child); }
        replaceChildren() { this.children = []; }
        setAttribute(name, value) { this.attributes[name] = value; }
        remove() { if (this.parent) this.parent.children = this.parent.children.filter(child => child !== this); elements.delete(this.id); }
        focus() { document.activeElement = this; }
        showModal() { this.open = true; }
        close() { this.open = false; }
        async trigger(name, event = {}) { await this.events.get(name)?.(event); await settle(); }
    }
    for (const id of ['coachChat', 'coachConversationList', 'coachSearch', 'coachNewChat', 'coachMessages', 'coachInput', 'coachSend', 'coachStatus', 'limitModal', 'limitModalClose', 'metricsRefreshBtn', 'insights7DayPanel']) elements.set(id, new Element(id));
    const chip = new Element(); chip.dataset.chipText = 'Plan tomorrow';
    elements.get('coachChat').dataset = {premium: 'true', error: 'Could not load. Try again.', loading: 'Loading', newChat: 'New chat'};
    const document = {
        getElementById: id => elements.get(id),
        createElement: () => new Element(),
        querySelectorAll: () => [chip],
        addEventListener: (name, fn) => { if (name === 'DOMContentLoaded') fn(); }
    };
    const calls = [];
    const fetch = async (url, options) => { calls.push({url, options}); return fetchResponse(url, options, calls); };
    vm.runInNewContext(source, {document, window: {}, fetch});
    return {elements, chip, calls, document};
}
const ok = data => ({ok: true, status: 200, json: async () => data});

test('a failed first conversation preserves the draft and releases sending for a retry', async () => {
    let creates = 0;
    const {elements} = setup((url, options) => {
        if (url === '/chat/conversations' && !options) return ok([]);
        if (url === '/chat/conversations') { if (++creates === 1) throw new Error('offline'); return ok({id: 1}); }
        return ok({reply: 'Training guidance'});
    });
    await settle();
    elements.get('coachInput').value = 'My training question';
    await elements.get('coachSend').trigger('click');
    assert.equal(elements.get('coachInput').value, 'My training question');
    assert.equal(elements.get('coachSend').disabled, false);
    assert.equal(elements.get('coachInput').readOnly, false);
    await elements.get('coachSend').trigger('click');
    assert.equal(creates, 2);
    assert.equal(elements.get('coachInput').value, '');
});

test('titles and refreshed trend notes remain text; chips do not transmit a message', async () => {
    const payload = '<img src=x onerror=alert(1)>';
    const {elements, chip, calls} = setup(url => {
        if (url === '/chat/conversations') return ok([{id: 1, title: payload}]);
        if (url === '/chat/context') return ok({completionPct: 150, sevenDayTasksTotal: 2, trendNote: payload});
        return ok([]);
    });
    await settle();
    assert.equal(elements.get('coachConversationList').children[0].children[0].textContent, payload);
    await elements.get('metricsRefreshBtn').trigger('click');
    assert.equal(elements.get('insights7DayPanel').children[0].textContent, payload);
    await chip.trigger('click');
    assert.equal(elements.get('coachInput').value, 'Plan tomorrow');
    assert.equal(calls.some(call => call.options?.method === 'POST'), false);
});

test('a limit response keeps the editable draft and Cancel closes the native dialog', async () => {
    const {elements} = setup((url, options) => {
        if (url === '/chat/conversations' && !options) return ok([]);
        if (url === '/chat/conversations') return ok({id: 1});
        return {ok: false, status: 429};
    });
    await settle();
    elements.get('coachInput').value = 'Keep my question';
    await elements.get('coachSend').trigger('click');
    assert.equal(elements.get('limitModal').open, true);
    assert.equal(elements.get('coachInput').value, 'Keep my question');
    assert.equal(elements.get('coachSend').disabled, false);
    await elements.get('limitModalClose').trigger('click');
    assert.equal(elements.get('limitModal').open, false);
});
