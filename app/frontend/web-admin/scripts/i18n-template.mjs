// Reading an Angular inline template the way the i18n check needs it: tags, comments,
// interpolations, control flow and the text between them. Not a full parser - enough to tell the
// words people read from everything else.

/** Every inline template in a component's source: where its text starts and ends. */
export function templatesOf(source) {
  const out = [];
  for (const m of source.matchAll(/\btemplate:\s*`/g)) {
    const start = m.index + m[0].length;
    const end = source.indexOf('`', start);
    if (end > start) out.push({ start, end, text: source.slice(start, end) });
  }
  return out;
}

function tagEnd(t, i) {
  let quote = null;
  for (let j = i + 1; j < t.length; j++) {
    const c = t[j];
    if (quote) {
      if (c === quote) quote = null;
    } else if (c === '"' || c === "'") quote = c;
    else if (c === '>') return j + 1;
  }
  return t.length;
}

function balanced(t, i) {
  let depth = 0;
  let quote = null;
  for (let j = i; j < t.length; j++) {
    const c = t[j];
    if (quote) {
      if (c === '\\') j++;
      else if (c === quote) quote = null;
    } else if (c === '"' || c === "'") quote = c;
    else if (c === '(') depth++;
    else if (c === ')' && --depth === 0) return j + 1;
  }
  return t.length;
}

/**
 * The template as tokens: text, interp ({{ }}), tag, comment, flow (@if (...) {, @else {, @let
 * ...;) and close (a block's }). Each with its start and end in the template.
 */
export function tokenize(t) {
  const out = [];
  let i = 0;
  let textStart = 0;
  const push = (type, start, end) => out.push({ type, start, end, value: t.slice(start, end) });
  const flush = (to) => {
    if (to > textStart) push('text', textStart, to);
  };
  while (i < t.length) {
    if (t.startsWith('<!--', i)) {
      flush(i);
      const e = t.indexOf('-->', i);
      const end = e < 0 ? t.length : e + 3;
      push('comment', i, end);
      i = textStart = end;
    } else if (t[i] === '<' && /[a-zA-Z/]/.test(t[i + 1] ?? '')) {
      flush(i);
      const end = tagEnd(t, i);
      push('tag', i, end);
      i = textStart = end;
    } else if (t.startsWith('{{', i)) {
      flush(i);
      const e = t.indexOf('}}', i);
      const end = e < 0 ? t.length : e + 2;
      push('interp', i, end);
      i = textStart = end;
    } else if (t[i] === '@' && /[a-z]/.test(t[i + 1] ?? '')) {
      flush(i);
      let j = i + 1;
      while (j < t.length && /[a-z]/.test(t[j])) j++;
      const word = t.slice(i + 1, j);
      if (word === 'let') {
        j = t.indexOf(';', j) + 1 || t.length;
      } else {
        // "@else if (...)", then the condition, then the block's {.
        const rest = /^\s+if\b/.exec(t.slice(j));
        if (word === 'else' && rest) j += rest[0].length;
        while (/\s/.test(t[j] ?? '')) j++;
        if (t[j] === '(') j = balanced(t, j);
        while (/\s/.test(t[j] ?? '')) j++;
        if (t[j] === '{') j++;
      }
      push('flow', i, j);
      i = textStart = j;
    } else if (t[i] === '}') {
      flush(i);
      push('close', i, i + 1);
      i = textStart = i + 1;
    } else {
      i++;
    }
  }
  flush(t.length);
  return out;
}

/** Words for people: letters, and a space or a capital. Not an identifier, a class list or a route. */
export function isWords(s) {
  const t = s.trim();
  if (!/[A-Za-zÁÉÍÓÚáéíóúñÑ]{2,}/.test(t)) return false;
  if (/^[a-z][a-zA-Z0-9]*$/.test(t)) return false; // 'setback', 'kind'
  if (/^[a-z0-9-]+(:[a-z0-9-]+)*$/.test(t)) return false; // 'btn-sm', 'part:EC'
  const words = t.split(/\s+/);
  if (words.every((w) => /^[a-z0-9-]+$/.test(w)) && words.some((w) => w.includes('-'))) return false; // 'btn btn-sm'
  if (!/\s/.test(t) && /^[/.#@]|^https?:|^[a-z-]+\//.test(t)) return false; // a route, a URL, a selector
  if (/^[A-Z_0-9]+$/.test(t)) return false; // 'ROLE_ADMIN', 'EC'
  return /\s/.test(t) || /^[A-ZÁÉÍÓÚÑ]-?[a-záéíóúñ]/.test(t);
}

/** Text between tags is always shown: any word in it is for people, even a lone "not". */
export function isText(s) {
  return /[A-Za-zÁÉÍÓÚáéíóúñÑ]{2,}/.test(s);
}

/** Attributes people read, and the component inputs that show text. */
export const SHOWN = ['title', 'placeholder', 'aria-label', 'alt', 'what', 'note', 'can', 'emptyMessage', 'label'];
