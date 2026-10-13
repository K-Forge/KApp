#!/usr/bin/env node
// Every word the portal shows goes through the translation, in Spanish and in English: this fails
// the build when it does not.
//
//   node scripts/check-i18n.mjs            the check (pnpm test runs it first)
//   node scripts/check-i18n.mjs --list     every finding, file by file
//
// In the components' inline templates it flags:
//   - text between tags that is not in an interpolation ({{ 'Save' | t }} is how text is written);
//   - title, placeholder, aria-label, alt, and the inputs that show text, written as plain attributes;
//   - quoted words in interpolations and in those bindings that are not piped through t.
// In all of the source, every text given to the translation - `'...' | t`, `t('...')`, or marked
// with `/* i18n */` in front - must have its Spanish in src/app/core/i18n/es.ts. English is the text
// itself.
//
// And every text of the API contracts the API console shows must have its Spanish in
// src/app/core/i18n/es-api.ts (see i18n-contracts.mjs); the contracts themselves stay English.
//
// A template line with `i18n-ignore` in it is skipped: for the rare text that is the same in both
// languages on purpose.
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { SHOWN, isText, isWords, templatesOf, tokenize } from './i18n-template.mjs';
import { contractTexts } from './i18n-contracts.mjs';

const ROOT = new URL('../src/app/', import.meta.url).pathname;
const ES_FILE = join(ROOT, 'core/i18n/es.ts');
const ES_API_FILE = join(ROOT, 'core/i18n/es-api.ts');
const list = process.argv.includes('--list');

function files(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return name === 'generated' ? [] : files(path);
    return name.endsWith('.ts') && !name.endsWith('.spec.ts') ? [path] : [];
  });
}

const unquote = (s) => s.replace(/\\(['"`\\])/g, '$1').replace(/\\n/g, '\n');

const keysOf = (path) =>
  new Set([...readFileSync(path, 'utf8').matchAll(/^\s*(['"])((?:\\.|(?!\1)[^\\\n])*)\1\s*:/gm)].map((m) => unquote(m[2])));
const spanish = keysOf(ES_FILE);

const findings = [];
const add = (file, line, what, text) => findings.push({ file: relative(ROOT, file), line, what, text: text.replace(/\s+/g, ' ').trim().slice(0, 150) });

const SHOWN_BINDING = new RegExp(`\\[(?:attr\\.)?(?:${SHOWN.join('|')}|ariaLabel)\\]="([^"]*)"`, 'g');
const PEOPLE_ONLY = new Set(['title', 'placeholder', 'aria-label', 'alt']);
const isSaid = (v) => /[A-Za-z]{2,}/.test(v) && !/^[A-Z_0-9-]+$/.test(v.trim()) && !/^(https?:|[/.#@])\S*$/.test(v.trim());
const SHOWN_PLAIN = new RegExp(`(?<=\\s)(${SHOWN.join('|')})="([^"{]*)"`, 'g');

for (const file of files(ROOT)) {
  if (file === ES_FILE || file === ES_API_FILE) continue;
  const source = readFileSync(file, 'utf8');
  const lineOf = (index) => source.slice(0, index).split('\n').length;

  // Texts given to the translation anywhere in the file must have their Spanish.
  const given = /(['"])((?:\\.|(?!\1)[^\\\n])*?)\1\s*\|\s*t\b|\bt\(\s*(['"`])((?:\\.|(?!\3)[^\\\n])*?)\3|\/\*\s*i18n\s*\*\/\s*(['"`])((?:\\.|(?!\5)[^\\\n])*?)\5/g;
  for (const m of source.matchAll(given)) {
    const text = unquote(m[2] ?? m[4] ?? m[6]);
    if (!spanish.has(text)) add(file, lineOf(m.index), 'no Spanish', text);
  }

  for (const tm of templatesOf(source)) {
    const skip = new Set(tm.text.split('\n').flatMap((l, i) => (l.includes('i18n-ignore') ? [i] : [])));
    const at = (offset) => lineOf(tm.start + offset);
    const skipped = (offset) => skip.has(tm.text.slice(0, offset).split('\n').length - 1);
    for (const k of tokenize(tm.text)) {
      if (skipped(k.start)) continue;
      if (k.type === 'text' && isText(k.value)) add(file, at(k.start), 'text not translated', k.value);
      const exprs = [];
      if (k.type === 'interp') exprs.push(k.value.slice(2, -2));
      if (k.type === 'tag') {
        for (const b of k.value.matchAll(SHOWN_BINDING)) exprs.push(b[1]);
        for (const a of k.value.matchAll(SHOWN_PLAIN)) {
          // A tooltip, a placeholder or a spoken label is never a class list or a key: any word shows.
          const said = PEOPLE_ONLY.has(a[1]) ? isSaid(a[2]) : isWords(a[2]);
          if (said && !(a[1] === 'label' && k.value.startsWith('<label'))) add(file, at(k.start), `${a[1]} not translated`, a[2]);
        }
      }
      for (const e of exprs) {
        for (const q of e.matchAll(/'((?:\\\\'|\\.|[^'])*)'(\s*\|\s*t\b)?/g)) {
          if (q[2]) continue;
          // A word shown as the result of a choice - `ok ? 'active' : 'never'` - or joined to others
          // with its own space - 'level ' + n - is for people even when it is one lowercase word.
          const before = e.slice(0, q.index).trimEnd();
          const shownAlone = /[?:]$/.test(before) && !/[=!<>]=?\s*$/.test(before) && /^[a-z][a-z ]+$/.test(q[1]);
          const joined = /^\s|\s$/.test(q[1]) && /[A-Za-z]{2,}/.test(q[1]);
          if (isWords(q[1]) || shownAlone || joined) add(file, at(k.start), 'not translated', q[1]);
        }
      }
    }
  }
}

const contractSpanish = keysOf(ES_API_FILE);
for (const { file, text } of contractTexts()) {
  if (!contractSpanish.has(text)) findings.push({ file: `docs/api/${file}`, line: '-', what: 'no Spanish in es-api.ts', text: text.replace(/\s+/g, ' ').slice(0, 150) });
}

if (findings.length) {
  const byFile = new Map();
  for (const f of findings) byFile.set(f.file, [...(byFile.get(f.file) ?? []), f]);
  for (const [file, fs] of [...byFile].sort()) {
    console.log(`\n${file} (${fs.length})`);
    for (const f of list ? fs : fs.slice(0, 5)) console.log(`  ${f.line}: ${f.what}: ${f.text}`);
    if (!list && fs.length > 5) console.log(`  … ${fs.length - 5} more (--list)`);
  }
  console.log(`\n${findings.length} text(s) the portal shows without its Spanish. See scripts/check-i18n.mjs.`);
  process.exit(1);
}
console.log('i18n: every text the portal shows has its Spanish');
