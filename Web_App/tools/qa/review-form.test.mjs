import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';
import assert from 'node:assert/strict';

const source=readFileSync(new URL('../../src/main/resources/static/js/health/review-form-page.js',import.meta.url),'utf8');
function page(retained=false) {
    const ready=[], unload=[], handlers=new Map(), status={textContent:''};
    const form={dataset:{retained:String(retained),unsaved:'Unsaved review'},querySelector:()=>status,
        addEventListener(name,callback){handlers.set(name,callback);}};
    vm.runInNewContext(source,{document:{querySelector:()=>form,addEventListener(name,callback){ready.push(callback);}},
        window:{addEventListener(name,callback){unload.push(callback);}}});
    ready[0]();
    const guarded=()=>{let prevented=false;unload[0]({preventDefault(){prevented=true;}});return prevented;};
    return {form,handlers,status,ready,unload,guarded};
}

test('clean native reviews guard changed drafts and release the guard for submission',()=>{
    const app=page(); assert.equal(app.guarded(),false);
    app.handlers.get('input')(); assert.equal(app.status.textContent,'Unsaved review'); assert.equal(app.guarded(),true);
    app.handlers.get('submit')(); assert.equal(app.guarded(),false);
});

test('retained invalid/conflict drafts are guarded immediately with a single page listener',()=>{
    const app=page(true); assert.equal(app.guarded(),true);
    const submit=app.handlers.get('submit'); app.ready[0]();
    assert.equal(app.unload.length,1); assert.equal(app.handlers.get('submit'),submit);
});
