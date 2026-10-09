import fs from 'node:fs';
import path from 'node:path';
import {parse} from 'parse5';

const write = process.argv.includes('--write');
const projectRoot = path.resolve(import.meta.dirname, '../..');
const templatesRoot = path.join(projectRoot, 'src/main/resources/templates');
const outputPath = path.join(projectRoot, 'src/main/resources/messages-ui.properties');
const excludedParents = new Set(['script', 'style', 'svg', 'path', 'code', 'pre', 'textarea']);
const attributeNames = ['placeholder', 'title', 'alt', 'aria-label'];
const languageIndependentLabels = new Set(['kcal', 'g', 'ml', 'YYYY-MM-DD']);
const messageByValue = new Map();
const sourceByValue = new Map();
let nextKey = 1;

const normalise = (value) => value.replace(/\s+/g, ' ').trim();
const propertyUnescape = (value) => value
    .replace(/\\([\\ !#:=])/g, '$1');

const existingOutput = fs.existsSync(outputPath)
    ? fs.readFileSync(outputPath, 'utf8')
    : '';
const existingKeys = new Set();
for (const line of existingOutput.split(/\r?\n/)) {
    const match = line.match(/^(ui\.(\d+))\s*=\s*(.*)$/);
    if (!match) {
        continue;
    }
    const [, key, numericKey, rawValue] = match;
    const value = normalise(propertyUnescape(rawValue));
    messageByValue.set(value, key);
    sourceByValue.set(value, 'existing full-site interface bundle');
    existingKeys.add(key);
    nextKey = Math.max(nextKey, Number.parseInt(numericKey, 10) + 1);
}
const hasEnglishCopy = (value) => /[A-Za-z]/.test(value);
const isTemplateExpression = (value) => /(?:\$|#|@|\*)\{|\[\[|\(\(/.test(value);
const isUsefulCopy = (value) => {
    const normalised = normalise(value);
    return normalised.length > 0
        && !languageIndependentLabels.has(normalised)
        && normalised.length <= 900
        && hasEnglishCopy(normalised)
        && !isTemplateExpression(normalised)
        && !/^(?:https?:\/\/|\/[-\w/{}]+|[.#][\w-]+|[\w.-]+\.(?:js|css|html|png|jpg|svg))$/i.test(normalised);
};

const propertyEscape = (value) => value
    .replace(/\\/g, '\\\\')
    .replace(/\r?\n/g, ' ')
    .replace(/^([ !#:=])/u, '\\$1');

const htmlFallback = (value) => value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;');

const keyFor = (value, source) => {
    const normalised = normalise(value);
    if (!messageByValue.has(normalised)) {
        const key = `ui.${String(nextKey).padStart(5, '0')}`;
        nextKey += 1;
        messageByValue.set(normalised, key);
        sourceByValue.set(normalised, source);
    }
    return messageByValue.get(normalised);
};

const attrs = (node) => new Map((node.attrs || []).map((attribute) => [attribute.name, attribute.value]));

const walk = (node, visitor, ancestors = []) => {
    visitor(node, ancestors);
    (node.childNodes || []).forEach((child) => walk(child, visitor, [...ancestors, node]));
    if (node.content) {
        walk(node.content, visitor, [...ancestors, node]);
    }
};

const files = [];
const collectFiles = (directory) => {
    fs.readdirSync(directory, {withFileTypes: true}).forEach((entry) => {
        const absolute = path.join(directory, entry.name);
        if (entry.isDirectory()) {
            collectFiles(absolute);
        } else if (entry.isFile() && entry.name.endsWith('.html')) {
            files.push(absolute);
        }
    });
};
collectFiles(templatesRoot);
files.sort();

const pendingFiles = [];

for (const absolutePath of files) {
    const relativePath = path.relative(templatesRoot, absolutePath).replaceAll('\\', '/');
    const source = fs.readFileSync(absolutePath, 'utf8');
    // This private gift is a standalone English document without the platform language selector.
    // Keep its personal copy separate from the multilingual product interface.
    if (relativePath === 'birthday/mission-vi.html' && /<html\b[^>]*\blang="en-GB"[^>]*\bdata-localisation="fixed"/.test(source)) {
        continue;
    }
    // Inspect fallback markup too; enabled scripting treats <noscript> as raw text.
    const document = parse(source, {sourceCodeLocationInfo: true, scriptingEnabled: false});
    const replacements = [];
    const tagInsertions = new Map();

    const addTagAttribute = (node, attribute) => {
        const location = node.sourceCodeLocation?.startTag;
        if (!location) {
            return;
        }
        const insertionOffset = source.lastIndexOf('>', location.endOffset - 1);
        if (insertionOffset < location.startOffset) {
            return;
        }
        const adjustedOffset = source[insertionOffset - 1] === '/' ? insertionOffset - 1 : insertionOffset;
        const existing = tagInsertions.get(adjustedOffset) || [];
        existing.push(attribute);
        tagInsertions.set(adjustedOffset, existing);
    };

    walk(document, (node, ancestors) => {
        if (node.nodeName === '#text' && node.sourceCodeLocation) {
            const parent = ancestors.at(-1);
            const parentAttributes = attrs(parent || {});
            if (!parent
                || excludedParents.has(parent.tagName)
                || parentAttributes.has('th:text')
                || parentAttributes.has('th:utext')) {
                return;
            }
            const value = normalise(node.value || '');
            if (!isUsefulCopy(value)) {
                return;
            }
            const key = keyFor(value, `${relativePath}:${node.sourceCodeLocation.startLine}`);
            const meaningfulChildren = (parent.childNodes || []).filter((child) =>
                child.nodeName !== '#text' || normalise(child.value || '').length > 0,
            );
            if (meaningfulChildren.length === 1 && meaningfulChildren[0] === node) {
                addTagAttribute(parent, `th:text="#{${key}}"`);
                return;
            }
            const raw = source.slice(node.sourceCodeLocation.startOffset, node.sourceCodeLocation.endOffset);
            const leading = raw.match(/^\s*/)?.[0] || '';
            const trailing = raw.match(/\s*$/)?.[0] || '';
            replacements.push({
                start: node.sourceCodeLocation.startOffset,
                end: node.sourceCodeLocation.endOffset,
                value: `${leading}<th:block th:text="#{${key}}">${htmlFallback(value)}</th:block>${trailing}`,
            });
            return;
        }

        if (!node.tagName || !node.sourceCodeLocation?.startTag) {
            return;
        }
        const nodeAttributes = attrs(node);
        attributeNames.forEach((attributeName) => {
            const value = nodeAttributes.get(attributeName);
            if (!value || !isUsefulCopy(value) || nodeAttributes.has(`th:${attributeName}`)) {
                return;
            }
            const attributeExpression = nodeAttributes.get('th:attr');
            if (attributeExpression && new RegExp(`(?:^|,)\\s*${attributeName}\\s*=`).test(attributeExpression)) {
                return;
            }
            const key = keyFor(value, `${relativePath}:${node.sourceCodeLocation.startLine}@${attributeName}`);
            if (attributeName === 'aria-label') {
                if (!nodeAttributes.has('th:attr')) {
                    addTagAttribute(node, `th:attr="aria-label=#{${key}}"`);
                } else {
                    const location = node.sourceCodeLocation.attrs['th:attr'];
                    replacements.push({start: location.startOffset, end: location.endOffset,
                        value: `th:attr="${attributeExpression.replaceAll('&', '&amp;').replaceAll('"', '&quot;')},aria-label=#{${key}}"`});
                }
            } else {
                addTagAttribute(node, `th:${attributeName}="#{${key}}"`);
            }
        });
    });

    // Translate literal page-title fallbacks passed to the shared base layout.
    for (const match of source.matchAll(/layout\(([^,\r\n]+),/g)) {
        const argument = match[1];
        const literal = argument.match(/'([^']*[A-Za-z][^']*)'/);
        if (!literal || !isUsefulCopy(literal[1])) {
            continue;
        }
        // A key passed to Spring's message helper is already localised. Treating the
        // key itself as visible copy would repeatedly generate bogus ui.* entries.
        if (argument.includes('#messages.msg(') && /^(?:ui|home|nav|language)\./.test(literal[1])) {
            continue;
        }
        const key = keyFor(literal[1], `${relativePath}:page-title`);
        const literalStart = match.index + match[0].indexOf(literal[0]);
        replacements.push({start: literalStart, end: literalStart + literal[0].length, value: `#{${key}}`});
    }

    tagInsertions.forEach((attributesToAdd, offset) => {
        replacements.push({start: offset, end: offset, value: ` ${attributesToAdd.join(' ')}`});
    });

    if (replacements.length > 0) {
        replacements.sort((a, b) => b.start - a.start || b.end - a.end);
        let transformed = source;
        replacements.forEach((replacement) => {
            transformed = transformed.slice(0, replacement.start)
                + replacement.value
                + transformed.slice(replacement.end);
        });
        pendingFiles.push({absolutePath, transformed, replacements: replacements.length});
    }
}

const propertyLines = existingOutput.length > 0
    ? existingOutput.trimEnd().split(/\r?\n/)
    : [
        '# Ordered full-site interface copy generated by tools/i18n/extract-template-messages.mjs.',
        '# Edit the English value here, then synchronise every locale bundle.',
        '',
    ];
if (process.argv.includes('--details')) {
    for (const [value, key] of messageByValue) {
        if (!existingKeys.has(key)) process.stderr.write(`${sourceByValue.get(value)}: ${value}\n`);
    }
}
for (const [value, key] of messageByValue) {
    if (existingKeys.has(key)) {
        continue;
    }
    propertyLines.push(`# ${sourceByValue.get(value)}`);
    propertyLines.push(`${key} = ${propertyEscape(value)}`);
}
propertyLines.push('');

if (write) {
    pendingFiles.forEach(({absolutePath, transformed}) => fs.writeFileSync(absolutePath, transformed, 'utf8'));
    fs.writeFileSync(outputPath, propertyLines.join('\n'), 'utf8');
    const addedMessages = [...messageByValue.values()].filter(key => !existingKeys.has(key)).length;
    process.stdout.write(`Externalised ${addedMessages} new strings across ${pendingFiles.length} templates.\n`);
} else if (pendingFiles.length > 0) {
    pendingFiles.forEach(({absolutePath, replacements}) => {
        process.stderr.write(`- ${path.relative(templatesRoot, absolutePath)} (${replacements})\n`);
    });
    process.stderr.write(
        `Unlocalised markup remains across ${pendingFiles.length} templates. Run with --write.\n`,
    );
    process.exitCode = 1;
} else {
    process.stdout.write('All eligible template copy is externalised.\n');
}
