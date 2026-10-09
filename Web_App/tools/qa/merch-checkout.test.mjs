import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';
import assert from 'node:assert/strict';

const source = readFileSync(new URL('../../src/main/resources/static/js/merch/merch-checkout-page.js', import.meta.url), 'utf8');
function page(providerDisabled = false) {
    const ready = [], pageshow = [], queued = [], events = new Map(), attributes = new Map();
    const button = { disabled: providerDisabled, dataset: { providerDisabled: String(providerDisabled) } };
    const form = { dataset: { unitMinor: '3699' }, querySelectorAll: () => [button],
        addEventListener: (name, callback) => events.set(name, callback),
        setAttribute: (name, value) => attributes.set(name, value), removeAttribute: name => attributes.delete(name) };
    const quantity = { value: '2', validity: { valid: true }, addEventListener: (name, callback) => events.set(name, callback) };
    const total = { textContent: '' }, panel = { hidden: true };
    const elements = { 'merch-checkout-form': form, 'merch-quantity': quantity, 'merch-order-total': total, 'merch-total-panel': panel };
    vm.runInNewContext(source, { document: { getElementById: id => elements[id], addEventListener: (name, callback) => ready.push(callback) },
        window: { addEventListener: (name, callback) => pageshow.push(callback) }, queueMicrotask: callback => queued.push(callback) });
    ready[0]();
    return { ready, pageshow, queued, events, attributes, button, quantity, total, panel };
}

test('checkout totals use exact pence and do not announce a valid price for invalid quantities', () => {
    const app = page(); assert.equal(app.panel.hidden, false); assert.equal(app.total.textContent, '£73.98');
    app.quantity.value = '3'; app.events.get('input')(); assert.equal(app.total.textContent, '£110.97');
    app.quantity.validity.valid = false; app.events.get('input')(); assert.equal(app.total.textContent, '—');
});

test('duplicate submit protection respects rejected payment events and resets on browser history return', () => {
    const app = page(); const event = { defaultPrevented: true, preventDefault() { this.defaultPrevented = true; } };
    app.events.get('submit')(event); assert.equal(app.attributes.has('aria-busy'), false);
    event.defaultPrevented = false; app.events.get('submit')(event); assert.equal(app.attributes.get('aria-busy'), 'true');
    app.queued.forEach(callback => callback()); assert.equal(app.button.disabled, true);
    app.events.get('submit')(event); assert.equal(event.defaultPrevented, true);
    app.ready[0](); assert.equal(app.pageshow.length, 1);
    app.pageshow[0](); assert.equal(app.button.disabled, false); assert.equal(app.attributes.has('aria-busy'), false);
    const unavailable = page(true); unavailable.pageshow[0](); assert.equal(unavailable.button.disabled, true);
});
