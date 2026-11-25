import { describeResponse } from './response-fields';
import { SERVICES } from './openapi-catalog';

const auth = SERVICES.find((s) => s.id === 'auth')!;

function operation(path: string, method = 'get') {
  return (auth.doc.paths as Record<string, Record<string, unknown>>)[path][method] as {
    responses?: Record<string, unknown>;
  };
}

describe('describeResponse', () => {
  // The report that prompted this: the console answered {"kty":"RSA","e":"AQAB",…} and left you
  // to guess. Every one of those names is explained in the contract already.
  it('explains the JWKS fields that nobody can be expected to know', () => {
    const fields = describeResponse(auth.doc, operation('/.well-known/jwks.json'));
    const byPath = new Map(fields.map((f) => [f.path, f]));

    expect([...byPath.keys()]).toEqual(
      expect.arrayContaining(['keys', 'keys[].kty', 'keys[].use', 'keys[].kid', 'keys[].alg', 'keys[].n', 'keys[].e']),
    );
    expect(byPath.get('keys[].kty')!.description).toContain('Key type');
    expect(byPath.get('keys[].n')!.description).toContain('modulus');
    expect(byPath.get('keys[].kid')!.description).toContain('Key identifier');
  });

  it('carries the type and whether the field is always present', () => {
    const byPath = new Map(
      describeResponse(auth.doc, operation('/.well-known/jwks.json')).map((f) => [f.path, f]),
    );
    expect(byPath.get('keys')!.type).toBe('array');
    expect(byPath.get('keys[].kty')!.required).toBe(true);
  });

  it('lists the values of an enumerated field', () => {
    const byPath = new Map(
      describeResponse(auth.doc, operation('/.well-known/jwks.json')).map((f) => [f.path, f]),
    );
    expect(byPath.get('keys[].alg')!.enumValues).toEqual(['RS256']);
  });

  it('says nothing at all for an operation with no response body', () => {
    const del = operation('/auth/admin/visitor-passes/{code}', 'delete');
    expect(describeResponse(auth.doc, del)).toEqual([]);
  });

  // Every endpoint, not just the one that was complained about.
  it('describes something for every operation that returns JSON', () => {
    const missing: string[] = [];
    for (const service of SERVICES) {
      for (const [path, item] of Object.entries(service.doc.paths)) {
        for (const [method, op] of Object.entries(item as Record<string, unknown>)) {
          if (!['get', 'post', 'put', 'patch'].includes(method)) continue;
          const responses = (op as { responses?: Record<string, unknown> }).responses ?? {};
          const returnsJson = Object.entries(responses).some(
            ([code, r]) =>
              /^2\d\d$/.test(code) &&
              !!(r as { content?: Record<string, unknown> })?.content?.['application/json'],
          );
          if (!returnsJson) continue;
          if (describeResponse(service.doc, op as never).length === 0) {
            missing.push(`${service.id} ${method.toUpperCase()} ${path}`);
          }
        }
      }
    }
    expect(missing).toEqual([]);
  });
});
