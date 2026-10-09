import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';
import assert from 'node:assert/strict';
const source = readFileSync(new URL('../../src/main/resources/static/js/notes/notes.js', import.meta.url), 'utf8');
const settle = () => new Promise(resolve => setImmediate(resolve));
const response = payload => ({ok: true, status: 200, headers: {get: () => 'application/json'}, json: async () => Array.isArray(payload) ? payload : ({revision: 'a'.repeat(64), ...payload})});
function setup(saveResponse = () => response({id: 1, updatedAt: '2026-10-01T12:00'})) {
    const elements = new Map(), timers = new Map(), calls = [], navigations = [];
    let timerId = 0, quill;
    class Element {
        constructor() { this.children = []; this.events = new Map(); this.dataset = {}; this.value = ''; this.textContent = ''; this.classList = {toggle() {}}; }
        addEventListener(name, callback) { this.events.set(name, callback); }
        appendChild(child) { this.children.push(child); }
        replaceChildren() { this.children = []; }
        setAttribute(name, value) { this[name] = value; }
        querySelectorAll() { return []; }
        set innerHTML(_value) { throw new Error('Untrusted labels must not be interpreted as HTML'); }
        async trigger(name) { await this.events.get(name)?.({preventDefault() {}, target: this}); await settle(); }
    }
    for (const id of ['notes-app', 'folderList', 'noteList', 'noteTitle', 'saveStatus', 'noteSearch', 'noteMeta', 'notesRetry', 'deleteNoteBtn', 'notesDeleteForm', 'exportHtmlBtn', 'notesNativeEdit', 'addNoteBtn', 'notesFolderManage', 'notesSearchFolder', 'notesSearchForm', 'notesConflict', 'notesReviewSaved', 'notesSavedTitle', 'notesSavedContent', 'notesKeepDraft']) elements.set(id, new Element());
    elements.get('notes-app').dataset = {activeFolder: '10', activeNote: '1', activeColour: 'orange',
        activeRevision: 'b'.repeat(64), conflict: 'Review conflict', saved: 'Saved', failed: 'Draft retained', unsaved: 'Unsaved', saving: 'Saving', untitled: 'Untitled', empty: 'Empty'};
    class Quill {
        constructor() { quill = this; this.root = {innerHTML: '<p>Initial</p>'}; this.events = new Map(); this.clipboard = {dangerouslyPasteHTML: value => { this.root.innerHTML = value; }}; }
        enable() {}
        setContents() { this.root.innerHTML = ''; }
        on(name, callback) { this.events.set(name, callback); }
        edit(value) { this.root.innerHTML = value; this.events.get('text-change')?.({}, {}, 'user'); }
    }
    const malicious = '<img src=x onerror=bad()>', notes = [{id: 2, title: malicious, preview: malicious}];
    const fetch = async (url, options = {}) => {
        calls.push({url, options});
        if (options.method === 'POST' && url === '/notes/api/notes/1') return saveResponse();
        if (url === '/notes/api/folders') return response([{id: 10, name: malicious}, {id: 20, name: 'Other folder'}]);
        if (url === '/notes/api/notes/1') return response({id: 1, title: 'New saved note', content: '<p>New saved content</p>', plainContent: 'New saved content', folderId: 10});
        if (url === '/notes/api/notes/2') return response({id: 2, folderId: 10, title: 'Second note', content: '<p>Second</p>', colour: 'red'});
        if (url.startsWith('/notes/api/notes/page?')) return response({notes, page: 1, pageCount: 1, total: notes.length});
        return response(notes);
    };
    const document = {getElementById: id => elements.get(id), createElement: () => new Element(),
        addEventListener(name, callback) { if (name === 'DOMContentLoaded') callback(); }};
    const context = vm.createContext({document, window: {Quill, addEventListener() {}, location: {assign: path => navigations.push(path)}}, fetch, URLSearchParams,
        setTimeout: (callback, delay) => { const id = ++timerId; timers.set(id, {callback, delay}); return id; },
        clearTimeout: id => timers.delete(id)});
    vm.runInContext(source, context);
    return {elements, calls, timers, quill, navigations, reload: () => vm.runInContext(source, context), run: async delay => {
        const [id, timer] = [...timers].find(([, timer]) => timer.delay === delay);
        timers.delete(id); const result = timer.callback(); await settle(); return result;
    }};
}
test('searching cannot cancel a pending save and labels remain plain text', async () => {
    const app = setup(); await settle();
    app.elements.get('noteTitle').value = 'Changed title'; await app.elements.get('noteTitle').trigger('input');
    app.elements.get('noteSearch').value = 'Changed'; await app.elements.get('noteSearch').trigger('input');
    await app.run(300); await app.run(1000);
    const saved = app.calls.find(call => call.options.method === 'POST');
    assert.equal(JSON.parse(saved.options.body).title, 'Changed title');
    assert.equal(JSON.parse(saved.options.body).colour, 'orange');
    assert.equal(app.elements.get('folderList').children[0].textContent, '<img src=x onerror=bad()>');
    assert.equal(app.elements.get('noteList').children[0].children[0].textContent, '<img src=x onerror=bad()>');
});
test('failed autosave retains the draft and blocks switching until a successful retry', async () => {
    let attempts = 0;
    const app = setup(() => ++attempts === 1 ? {ok: false} : response({id: 1})); await settle();
    app.elements.get('noteTitle').value = 'Keep draft'; await app.elements.get('noteTitle').trigger('input');
    await app.elements.get('noteList').children[0].trigger('click');
    assert.equal(app.elements.get('noteTitle').value, 'Keep draft');
    assert.equal(app.calls.some(call => call.url === '/notes/api/notes/2'), false);
    assert.equal(app.elements.get('saveStatus').textContent, 'Draft retained');
    await app.elements.get('notesRetry').trigger('click');
    await app.elements.get('noteList').children[0].trigger('click');
    assert.equal(app.elements.get('noteTitle').value, 'Second note');
});
test('edits made during a save are persisted in a follow-up request', async () => {
    let resolveFirst, attempts = 0;
    const app = setup(() => ++attempts === 1 ? new Promise(resolve => { resolveFirst = resolve; }) : response({id: 1}));
    await settle(); app.quill.edit('<p>First draft</p>');
    const [, timer] = [...app.timers].find(([, timer]) => timer.delay === 1000);
    const saving = timer.callback(); await settle();
    app.quill.edit('<p>Latest draft</p>'); resolveFirst(response({id: 1})); await saving;
    const saves = app.calls.filter(call => call.options.method === 'POST');
    assert.equal(saves.length, 2);
    assert.equal(JSON.parse(saves[1].options.body).content, '<p>Latest draft</p>');
});

test('changing folders updates native creation, management and search targets', async () => {
    const app = setup(); await settle();
    await app.elements.get('folderList').children[1].trigger('click');
    assert.equal(app.elements.get('addNoteBtn').href, '/notes/folders/20/new');
    assert.equal(app.elements.get('notesFolderManage').href, '/notes/folders/20');
    assert.equal(app.elements.get('notesSearchFolder').value, 20);
});

test('wrong saved acknowledgement keeps the draft and blocks switching', async () => {
    const app = setup(() => response({id: 999})); await settle();
    app.elements.get('noteTitle').value = 'Keep uncertain draft';
    await app.elements.get('noteTitle').trigger('input');
    await app.elements.get('noteList').children[0].trigger('click');
    assert.equal(app.elements.get('noteTitle').value, 'Keep uncertain draft');
    assert.equal(app.elements.get('saveStatus').textContent, 'Draft retained');
    assert.equal(app.calls.some(call => call.url === '/notes/api/notes/2'), false);
});

test('stale drafts require saved-note review before an explicit retry with its revision', async () => {
    let attempts = 0;
    const app = setup(() => ++attempts === 1 ? {ok: false, status: 409} : response({id: 1}));
    await settle(); app.quill.edit('<p>My retained draft</p>');
    await app.run(1000);
    assert.equal(app.elements.get('saveStatus').textContent, 'Review conflict');
    await app.elements.get('notesRetry').trigger('click');
    assert.equal(attempts, 1);
    await app.elements.get('notesKeepDraft').trigger('click');
    assert.equal(attempts, 1);
    await app.elements.get('notesReviewSaved').trigger('click');
    assert.equal(app.elements.get('notesSavedContent').value, 'New saved content');
    assert.equal(app.quill.root.innerHTML, '<p>My retained draft</p>');
    await app.elements.get('notesKeepDraft').trigger('click');
    assert.equal(attempts, 2);
    const saves = app.calls.filter(call => call.options.method === 'POST');
    assert.equal(JSON.parse(saves[0].options.body).revision, 'b'.repeat(64));
    assert.equal(JSON.parse(saves[1].options.body).revision, 'a'.repeat(64));
});

test('a duplicate script load does not initialise another editor or repeat initial reads', async () => {
    const app = setup(); await settle();
    const calls = app.calls.length;
    app.reload(); await settle();
    assert.equal(app.calls.length, calls);
    app.quill.edit('<p>Single editor</p>'); await app.run(1000);
    assert.equal(app.calls.filter(call => call.options.method === 'POST').length, 1);
});

test('native editing and export save the current draft first and stay put when saving fails', async () => {
    const app = setup(); await settle();
    app.quill.edit('<p>Latest export</p>');
    await app.elements.get('exportHtmlBtn').trigger('click');
    assert.equal(JSON.parse(app.calls.find(call => call.options.method === 'POST').options.body).content, '<p>Latest export</p>');
    assert.deepEqual(app.navigations, ['/notes/export/1?format=html']);
    const failed = setup(() => ({ok: false, status: 400})); await settle();
    failed.quill.edit('<p>Retained edit draft</p>');
    await failed.elements.get('notesNativeEdit').trigger('click');
    assert.deepEqual(failed.navigations, []);
    assert.equal(failed.quill.root.innerHTML, '<p>Retained edit draft</p>');
});
