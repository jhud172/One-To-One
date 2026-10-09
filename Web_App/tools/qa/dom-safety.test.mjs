import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';
import assert from 'node:assert/strict';
import {parseFragment} from 'parse5';

const context = vm.createContext({window: {}});
vm.runInContext(readFileSync(new URL('../../src/main/resources/static/js/core/dom-safety.js', import.meta.url), 'utf8'), context);
const escapeHtml = context.window.OneToOneDom.escapeHtml;

test('untrusted preview copy remains literal text, including tag and entity payloads', () => {
    const payload = '<img src=x onerror="alert(1)"> & <script>alert(2)</script>';
    const fragment = parseFragment('<p>' + escapeHtml(payload) + '</p>');
    const paragraph = fragment.childNodes[0];
    assert.equal(paragraph.childNodes.length, 1);
    assert.equal(paragraph.childNodes[0].nodeName, '#text');
    assert.equal(paragraph.childNodes[0].value, payload);
});
test('workout names cannot escape a quoted sticker attribute', () => {
    const payload = '\" autofocus onfocus=\"alert(1)\" <img>';
    const fragment = parseFragment('<button data-sticker-name="' + escapeHtml(payload) + '">Workout</button>');
    const button = fragment.childNodes[0];
    assert.equal(button.attrs.length, 1);
    assert.equal(button.attrs[0].name, 'data-sticker-name');
    assert.equal(button.attrs[0].value, payload);
});
