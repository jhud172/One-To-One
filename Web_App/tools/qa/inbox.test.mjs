import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';
import assert from 'node:assert/strict';

const source = readFileSync(new URL('../../src/main/resources/static/js/messaging/inbox.js', import.meta.url), 'utf8');
const settle = () => new Promise(resolve => setImmediate(resolve));
function setup(sendResponse, messages = [], duplicate = false) {
    const elements = new Map();
    const intervals = [];
    class Element {
        constructor() { this.children = []; this.events = new Map(); this.dataset = {}; this.value = ''; this.scrollHeight = 0; this.scrollTop = 0; this.clientHeight = 0; this.classList = {add() {}, remove() {}, toggle() {}}; }
        addEventListener(name, callback) { this.events.set(name, callback); }
        appendChild(child) { this.children.push(child); }
        replaceChildren() { this.children = []; }
        querySelector() { return elements.get('sendButton'); }
        setAttribute() {}
        removeAttribute() {}
        async trigger(name) { await this.events.get(name)?.({preventDefault() {}}); await settle(); }
    }
    for (const id of ['inboxThreadRoot', 'inboxStatus', 'inboxSendForm', 'inboxBody', 'inboxAttachmentUrl', 'sendButton', 'inboxMessages', 'inboxMessagesEmpty', 'inboxReload']) elements.set(id, new Element());
    elements.get('inboxThreadRoot').dataset = {threadId: '1', threadStatus: 'OPEN', sendError: 'Draft retained', locked: 'Read only', sent: 'Sent'};
    const document = {hidden: false, documentElement: {lang: 'en'}, getElementById: id => elements.get(id), querySelector: () => null, querySelectorAll: () => [], createElement: () => new Element(), addEventListener(name, callback) { if (name === 'DOMContentLoaded') callback(); }};
    const calls = [];
    const fetch = async (url, options) => {
        calls.push({url, options});
        if (url.endsWith('/send')) return sendResponse();
        return {ok: true, json: async () => ({status: 'OPEN', currentUserId: 2, messages})};
    };
    vm.runInNewContext(source, {document, window: {addEventListener() {}}, location: {origin: 'http://localhost:8081'}, URL, fetch, setInterval: callback => intervals.push(callback)});
    if (duplicate) vm.runInNewContext(source, {document, window: {addEventListener() {}}, location: {origin: 'http://localhost:8081'}, URL, fetch, setInterval: callback => intervals.push(callback)});
    return {elements, calls, intervals, document};
}

test('an unsuccessful send keeps text and attachment drafts and allows a retry', async () => {
    let attempts = 0;
    const {elements} = setup(() => ++attempts === 1 ? {ok: false, json: async () => ({reason: 'INVALID_MESSAGE'})} : {ok: true, json: async () => ({id: 7})});
    await settle();
    elements.get('inboxBody').value = 'My coaching question';
    elements.get('inboxAttachmentUrl').value = 'https://example.com/demo';
    await elements.get('inboxSendForm').trigger('submit');
    assert.equal(elements.get('inboxBody').value, 'My coaching question');
    assert.equal(elements.get('inboxAttachmentUrl').value, 'https://example.com/demo');
    assert.equal(elements.get('sendButton').disabled, false);
    assert.equal(elements.get('inboxStatus').textContent, 'Draft retained');
    await elements.get('inboxSendForm').trigger('submit');
    assert.equal(attempts, 2);
    assert.equal(elements.get('inboxBody').value, '');
});

test('unsafe historical attachments are omitted and unchanged polls preserve message elements', async () => {
    const {elements, intervals, calls, document} = setup(() => ({ok: true}), [{id: 1, senderUserId: 2, bodyText: '<img src=x>', attachmentUrl: 'javascript:alert(1)'}]);
    await settle();
    const original = elements.get('inboxMessages').children[0];
    assert.equal(original.children[0].children.some(child => child.href), false);
    await intervals[0]();
    assert.equal(elements.get('inboxMessages').children[0], original);
    const before = calls.length;
    document.hidden = true;
    await intervals[0]();
    assert.equal(calls.length, before);
});

test('failed payment policy sends preserve the draft and show policy feedback', async () => {
    const {elements} = setup(() => ({ok: false, json: async () => ({reason: 'OFF_PLATFORM_PAYMENT'})}));
    elements.get('inboxThreadRoot').dataset.paymentError = 'Use platform payments';
    await settle();
    elements.get('inboxBody').value = 'Please review my link';
    await elements.get('inboxSendForm').trigger('submit');
    assert.equal(elements.get('inboxBody').value, 'Please review my link');
    assert.equal(elements.get('inboxStatus').textContent, 'Use platform payments');
});

test('an HTML or malformed success acknowledgement retains the draft until explicit history reload', async () => {
    const {elements,calls} = setup(() => ({ok: true, json: async () => { throw new Error('HTML login page'); }}));
    elements.get('inboxThreadRoot').dataset.unconfirmed = 'Check history before retrying';
    await settle();
    elements.get('inboxBody').value = 'Please retain this uncertain draft';
    await elements.get('inboxSendForm').trigger('submit');
    assert.equal(elements.get('inboxBody').value, 'Please retain this uncertain draft');
    assert.equal(elements.get('sendButton').disabled, true);
    assert.equal(elements.get('inboxStatus').textContent, 'Check history before retrying');
    await elements.get('inboxSendForm').trigger('submit');
    assert.equal(calls.filter(call => call.url.endsWith('/send')).length,1);
    await elements.get('inboxReload').trigger('click');
    assert.equal(elements.get('sendButton').disabled, false);
    assert.equal(elements.get('inboxBody').value, 'Please retain this uncertain draft');
});

test('a duplicate script load cannot create a second polling or send handler', async () => {
    const {elements,calls,intervals} = setup(() => ({ok: true,json: async () => ({id: 7})}),[],true);
    await settle();
    assert.equal(intervals.length,1);
    elements.get('inboxBody').value='One coaching reply';
    await elements.get('inboxSendForm').trigger('submit');
    assert.equal(calls.filter(call => call.url.endsWith('/send')).length,1);
    assert.equal(elements.get('inboxBody').value,'');
});
