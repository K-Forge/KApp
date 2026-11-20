#!/usr/bin/env node
// Fails when the portal would be served without its security headers (#145).
//
// nginx drops the server block's headers in any location that sets one of its own, and every
// location in nginx.conf does, so the headers are included location by location. A location added
// without that line would serve its responses with no Content-Security-Policy, and nothing else
// would notice. This checks every location includes the file, and that the policy still keeps
// scripts to the portal's own: the tokens in localStorage are readable by any script that runs.
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const nginx = readFileSync(join(root, 'nginx.conf'), 'utf8').replace(/#.*$/gm, '');
const headers = readFileSync(join(root, 'security-headers.conf'), 'utf8').replace(/#.*$/gm, '');
const INCLUDE = 'include /etc/nginx/security-headers.conf;';
const problems = [];

// Each location's body, by matching braces from the one that ends its line: a regex location's
// pattern can hold braces of its own ({8,}).
for (const match of nginx.matchAll(/location\s+(.+?)\s*\{[ \t]*$/gm)) {
  let depth = 1;
  let i = match.index + match[0].length;
  const start = i;
  while (depth > 0 && i < nginx.length) {
    if (nginx[i] === '{') depth++;
    if (nginx[i] === '}') depth--;
    i++;
  }
  if (!nginx.slice(start, i).includes(INCLUDE)) {
    problems.push(`nginx.conf: location ${match[1].trim()} does not include the security headers`);
  }
}

const policy = /Content-Security-Policy\s+"([^"]+)"/.exec(headers)?.[1];
if (!policy) {
  problems.push('security-headers.conf: no Content-Security-Policy');
} else {
  const scripts = /script-src ([^;]+)/.exec(policy)?.[1] ?? '';
  if (scripts.trim() !== "'self'") problems.push(`security-headers.conf: script-src is "${scripts.trim()}", not 'self'`);
  for (const directive of ['frame-ancestors', 'object-src', 'base-uri']) {
    if (!policy.includes(directive)) problems.push(`security-headers.conf: no ${directive}`);
  }
}
for (const header of ['X-Content-Type-Options', 'Referrer-Policy']) {
  if (!headers.includes(header)) problems.push(`security-headers.conf: no ${header}`);
}

if (problems.length) {
  console.error(problems.join('\n'));
  console.error(`\n${problems.length} problem(s) with the portal's security headers. See security-headers.conf.`);
  process.exit(1);
}
console.log('security headers: every location of nginx.conf sends them');
