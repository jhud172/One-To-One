import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';
import assert from 'node:assert/strict';
const source = readFileSync(new URL('../../src/main/resources/static/js/merch/merch-shop-page.js', import.meta.url), 'utf8');

function page(frontWidth = 0) {
    const ready = [], handlers = new Map(), classes = new Set(), placeholder = { hidden: true };
    const image = (src, width, secondary = false) => ({ complete: true, naturalWidth: width, hidden: false,
        dataset: { secondarySrc: '/secondary.jpg' }, classList: { contains: name => secondary && name === 'img-back' },
        handlers: new Map(), getAttribute: () => src, setAttribute: (name, value) => { src = value; },
        addEventListener(name, callback) { this.handlers.set(name, callback); } });
    const front = image('/front.jpg', frontWidth), secondary = image('', 0, true);
    const card = { dataset: {}, classList: { add: name => classes.add(name), remove: name => classes.delete(name) },
        querySelector: selector => selector === '.img-placeholder' ? placeholder : secondary,
        querySelectorAll: () => [front, secondary], addEventListener: (name, callback) => handlers.set(name, callback) };
    vm.runInNewContext(source, { document: { addEventListener: (name, callback) => ready.push(callback), querySelectorAll: () => [card] } });
    ready[0](); return { ready, handlers, classes, placeholder, front, secondary };
}

test('cached broken primary images expose a fallback and successful recovery hides it', () => {
    const app = page(); assert.equal(app.front.hidden, true); assert.equal(app.placeholder.hidden, false);
    app.front.naturalWidth = 100; app.front.handlers.get('load')();
    assert.equal(app.front.hidden, false); assert.equal(app.placeholder.hidden, true);
    const loaded = page(100); assert.equal(loaded.front.hidden, false); assert.equal(loaded.placeholder.hidden, true);
});

test('secondary media stays deferred until keyboard focus and failure never removes the primary fallback', () => {
    const app = page(); assert.equal(app.secondary.getAttribute('src'), '');
    app.handlers.get('focusin')(); assert.equal(app.secondary.getAttribute('src'), '/secondary.jpg');
    app.secondary.naturalWidth = 100; app.secondary.handlers.get('load')(); assert.equal(app.classes.has('merch-secondary-ready'), true);
    app.secondary.handlers.get('error')(); assert.equal(app.classes.has('merch-secondary-ready'), false);
    assert.equal(app.placeholder.hidden, false);
    const listener = app.handlers.get('focusin'); app.ready[0](); assert.equal(app.handlers.get('focusin'), listener);
});
