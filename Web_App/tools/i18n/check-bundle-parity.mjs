import fs from 'node:fs';
import path from 'node:path';

const projectRoot = path.resolve(import.meta.dirname, '../..');
const resourcesRoot = path.join(projectRoot, 'src/main/resources');
const bundleNames = ['messages', 'messages-home', 'messages-ui'];
const localeCodes = ['cy', 'es', 'fr', 'de', 'it', 'pt', 'pl', 'nl', 'zh', 'ja', 'ko', 'ar', 'hi'];
const propertyPattern = /^([^#!\s][^=]*?)\s*=\s*(.*)$/;
const placeholderPattern = /\$\{[^}]+}|\{(?:\d+|[a-zA-Z_][a-zA-Z0-9_]*)}|%\d*\$?[a-zA-Z]/g;

const readBundle = (filePath) => {
    const values = new Map();
    const duplicates = [];
    fs.readFileSync(filePath, 'utf8').replace(/\r\n/g, '\n').split('\n').forEach((line) => {
        const match = line.match(propertyPattern);
        if (!match) {
            return;
        }
        const key = match[1].trim();
        if (values.has(key)) {
            duplicates.push(key);
        }
        values.set(key, match[2]);
    });
    return {values, duplicates};
};

const errors = [];

for (const bundleName of bundleNames) {
    const sourcePath = path.join(resourcesRoot, `${bundleName}.properties`);
    const source = readBundle(sourcePath);
    if (source.duplicates.length > 0) {
        errors.push(`${path.basename(sourcePath)} has duplicate keys: ${source.duplicates.join(', ')}`);
    }

    for (const localeCode of localeCodes) {
        const localePath = path.join(resourcesRoot, `${bundleName}_${localeCode}.properties`);
        if (!fs.existsSync(localePath)) {
            errors.push(`Missing bundle: ${path.basename(localePath)}`);
            continue;
        }
        const locale = readBundle(localePath);
        const missing = [...source.values.keys()].filter((key) => !locale.values.has(key));
        const extra = [...locale.values.keys()].filter((key) => !source.values.has(key));
        if (locale.duplicates.length > 0) {
            errors.push(`${path.basename(localePath)} has duplicate keys: ${locale.duplicates.join(', ')}`);
        }
        if (missing.length > 0) {
            errors.push(`${path.basename(localePath)} is missing ${missing.length} keys: ${missing.slice(0, 8).join(', ')}`);
        }
        if (extra.length > 0) {
            errors.push(`${path.basename(localePath)} has ${extra.length} extra keys: ${extra.slice(0, 8).join(', ')}`);
        }

        for (const [key, sourceValue] of source.values) {
            const localeValue = locale.values.get(key);
            if (localeValue === undefined) {
                continue;
            }
            const sourcePlaceholders = [...sourceValue.matchAll(placeholderPattern)].map((match) => match[0]).sort();
            const localePlaceholders = [...localeValue.matchAll(placeholderPattern)].map((match) => match[0]).sort();
            if (sourcePlaceholders.join('|') !== localePlaceholders.join('|')) {
                errors.push(`${path.basename(localePath)}:${key} does not preserve message placeholders.`);
            }
            if (/I18N(?:KEEP|SEP)/.test(localeValue)) {
                errors.push(`${path.basename(localePath)}:${key} contains an unresolved translation marker.`);
            }
            if (/ZXQV(?:TKN|SEP)/.test(localeValue)) {
                errors.push(`${path.basename(localePath)}:${key} contains an unresolved translation marker.`);
            }
        }
    }
}

if (errors.length > 0) {
    errors.forEach((error) => process.stderr.write(`- ${error}\n`));
    process.stderr.write(`Localisation parity failed with ${errors.length} issue(s).\n`);
    process.exitCode = 1;
} else {
    process.stdout.write(`All ${bundleNames.length * (localeCodes.length + 1)} localisation bundles have exact key and placeholder parity.\n`);
}
