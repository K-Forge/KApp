import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import { parseApiError } from './api-error.util';

describe('parseApiError', () => {
  it('passes through a well-formed ApiError body unchanged', () => {
    const body = {
      timestamp: '2026-09-01T10:00:00Z',
      status: 400,
      error: 'Bad Request',
      message: 'Validation failed',
      path: '/api/map/buildings',
      details: [{ field: 'code', issue: 'must not be blank' }],
    };
    const err = new HttpErrorResponse({ error: body, status: 400, statusText: 'Bad Request', url: 'http://localhost:8080/api/map/buildings' });

    const result = parseApiError(err);

    expect(result).toEqual(body);
  });

  it('keeps details undefined rather than inventing an empty array when absent', () => {
    const body = { timestamp: 't', status: 404, error: 'Not Found', message: 'no such building', path: '/x' };
    const err = new HttpErrorResponse({ error: body, status: 404 });

    expect(parseApiError(err).details).toBeUndefined();
  });

  it('falls back to the response path when the body omits its own path', () => {
    const body = { timestamp: 't', status: 409, error: 'Conflict', message: 'duplicate code' };
    const err = new HttpErrorResponse({ error: body, status: 409, url: 'http://localhost:8080/api/map/spaces' });

    expect(parseApiError(err).path).toBe('http://localhost:8080/api/map/spaces');
  });

  it('produces a distinct, actionable message for a status-0 network failure', () => {
    const err = new HttpErrorResponse({ error: new ProgressEvent('error'), status: 0, url: 'http://localhost:9999/auth/login' });

    const result = parseApiError(err);

    expect(result.status).toBe(0);
    expect(result.error).toBe('Network Error');
    expect(result.message).toContain('http://localhost:9999/auth/login');
  });

  it('synthesizes an ApiError from a non-JSON error body using the HTTP status', () => {
    const err = new HttpErrorResponse({
      error: '<html>502 Bad Gateway</html>',
      status: 502,
      statusText: 'Bad Gateway',
      url: 'http://localhost:8080/api/users',
    });

    const result = parseApiError(err);

    expect(result.status).toBe(502);
    expect(result.error).toBe('Bad Gateway');
    expect(result.message).toBe('<html>502 Bad Gateway</html>');
  });

  it('says the status in the portal’s words when the body says nothing', () => {
    const err = new HttpErrorResponse({ error: null, status: 405, statusText: 'Method Not Allowed', url: 'http://localhost:8080/api/catalog/pensums' });

    expect(parseApiError(err).message).toBe('The server answered 405 and gave no reason.');
  });

  it('reads a message field out of an unrecognized JSON error body', () => {
    const err = new HttpErrorResponse({ error: { reason: 'ignored', message: 'from a differently-shaped body' }, status: 500 });

    expect(parseApiError(err).message).toBe('from a differently-shaped body');
  });

  it('never throws and always returns a renderable ApiError for a completely unexpected error', () => {
    const result = parseApiError(new Error('boom'));

    expect(result.status).toBe(0);
    expect(result.message).toBe('boom');
  });

  it('handles a thrown non-Error value without crashing', () => {
    const result = parseApiError('just a string');

    expect(result.message).toBe('just a string');
  });
});
