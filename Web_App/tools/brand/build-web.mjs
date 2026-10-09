import { build } from 'esbuild';
import { readFile, appendFile } from 'node:fs/promises';
await build({
    entryPoints: ['src/main/frontend/logo-experience.js'],
    outfile: 'src/main/resources/static/js/public/logo-experience.bundle.js',
    bundle: true,
    minify: true,
    format: 'iife',
    target: ['es2020'],
    legalComments: 'linked'
});
await appendFile(
    'src/main/resources/static/js/public/logo-experience.bundle.js.LEGAL.txt',
    '\n' + (await readFile('node_modules/three/LICENSE', 'utf8'))
);
console.log('One To One 3D browser bundle built.');
