// The texts of the API contracts that the API console shows: each operation's summary and
// description, its parameters' descriptions, and the descriptions of the fields in its JSON
// responses. check-i18n.mjs requires each one to have its Spanish in src/app/core/i18n/es-api.ts.
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { parse } from 'yaml';

// The same contracts, in the same place, that generate-openapi.mjs bundles into the portal.
const SPECS_DIR = new URL('../../../../docs/api/', import.meta.url).pathname;
const SPECS = ['auth', 'user', 'semaphore', 'schedule', 'map'];

/** Every contract text the console can show, once, with the contract it comes from. */
export function contractTexts() {
  const out = [];
  const seen = new Set();
  for (const id of SPECS) {
    const file = `${id}.openapi.yaml`;
    const doc = parse(readFileSync(join(SPECS_DIR, file), 'utf8'));
    const add = (text) => {
      const said = typeof text === 'string' ? text.trim() : '';
      if (said && !seen.has(said)) {
        seen.add(said);
        out.push({ file, text: said });
      }
    };
    const deref = (x) => {
      if (!x?.$ref) return x;
      let target = doc;
      for (const key of x.$ref.replace('#/', '').split('/')) target = target?.[key];
      return target;
    };
    const walk = (schema, refs = new Set()) => {
      if (!schema || typeof schema !== 'object') return;
      if (schema.$ref) {
        if (refs.has(schema.$ref)) return;
        refs.add(schema.$ref);
        return walk(deref(schema), refs);
      }
      add(schema.description);
      for (const child of Object.values(schema.properties ?? {})) walk(child, refs);
      if (schema.items) walk(schema.items, refs);
      for (const key of ['allOf', 'oneOf', 'anyOf']) for (const part of schema[key] ?? []) walk(part, refs);
    };
    for (const item of Object.values(doc.paths ?? {})) {
      for (const op of Object.values(item)) {
        if (!op || typeof op !== 'object' || !op.responses) continue;
        add(op.summary);
        add(op.description);
        for (const param of [...(item.parameters ?? []), ...(op.parameters ?? [])]) add(deref(param)?.description);
        for (const response of Object.values(op.responses)) walk(deref(response)?.content?.['application/json']?.schema);
      }
    }
  }
  return out;
}
