import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';
import assert from 'node:assert/strict';

const source=readFileSync(new URL('../../src/main/resources/static/js/notes/vault.js',import.meta.url),'utf8');
function setup({disabled=false,retained=false}={}) {
    const events=new Map(), unload=new Map();
    const element=()=>({dataset:{},textContent:'',disabled:false,checked:false,events:new Map(),
        addEventListener(name,callback){this.events.set(name,callback);},focus(){this.focused=true;}});
    const choices=Array.from({length:21},element); choices.forEach(input=>{input.disabled=disabled;});
    const selection=element(), count=element(), status=element(), editor=element(), workspace=element();
    editor.dataset={unsaved:'Draft retained',retained:String(retained)};
    editor.querySelector=()=>status;
    workspace.querySelectorAll=()=>choices;
    workspace.querySelector=()=>editor;
    const document={querySelector:()=>workspace,getElementById:id=>id==='vaultSelection'?selection:count,
        addEventListener(name,callback){events.set(name,callback);}};
    const context={document,window:{addEventListener(name,callback){unload.set(name,callback);}},Set};
    vm.runInNewContext(source,context); events.get('DOMContentLoaded')();
    return {choices,selection,count,status,editor,workspace,unload,events};
}

test('disabled provider selections remain disabled and duplicate page initialisation is ignored',()=>{
    const app=setup({disabled:true});
    assert.equal(app.choices.every(input=>input.disabled),true);
    assert.equal(app.count.textContent,'0 / 20');
    const handler=app.selection.events.get('submit'); app.events.get('DOMContentLoaded')();
    assert.equal(app.selection.events.get('submit'),handler);
    assert.equal(app.choices.every(input=>input.disabled),true);
});

test('selection bounds keep checked choices removable and rejected empty submissions focus a choice',()=>{
    const app=setup(); let prevented=false;
    app.selection.events.get('submit')({preventDefault(){prevented=true;}});
    assert.equal(prevented,true); assert.equal(app.choices[0].focused,true);
    app.choices.slice(0,20).forEach(input=>{input.checked=true;});
    app.choices[0].events.get('change')();
    assert.equal(app.count.textContent,'20 / 20');
    assert.equal(app.choices[20].disabled,true); assert.equal(app.choices[0].disabled,false);
    app.choices[0].checked=false; app.choices[0].events.get('change')();
    assert.equal(app.choices[20].disabled,false);
});

test('retained invalid/conflict drafts guard navigation until an actual submit',()=>{
    const app=setup({retained:true}); let prevented=false;
    app.unload.get('beforeunload')({preventDefault(){prevented=true;}}); assert.equal(prevented,true);
    app.editor.events.get('input')(); assert.equal(app.status.textContent,'Draft retained');
    app.editor.events.get('submit')(); prevented=false;
    app.unload.get('beforeunload')({preventDefault(){prevented=true;}}); assert.equal(prevented,false);
});
