import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { JsonViewComponent } from './json-view.component';

describe('copying what a JSON view shows', () => {
  const secure = Object.getOwnPropertyDescriptor(window, 'isSecureContext');
  const clipboard = Object.getOwnPropertyDescriptor(navigator, 'clipboard');

  afterEach(() => {
    if (secure) Object.defineProperty(window, 'isSecureContext', secure);
    else delete (window as { isSecureContext?: boolean }).isSecureContext;
    if (clipboard) Object.defineProperty(navigator, 'clipboard', clipboard);
    else delete (navigator as { clipboard?: unknown }).clipboard;
  });

  function view(value: unknown) {
    const fixture = TestBed.createComponent(JsonViewComponent);
    fixture.componentRef.setInput('value', value);
    return fixture.componentInstance;
  }

  it('copies through the Clipboard API on a secure page', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(window, 'isSecureContext', { value: true, configurable: true });
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    const json = view('eyJ.token.itself');

    await json.copy();

    expect(writeText).toHaveBeenCalledWith('eyJ.token.itself');
    expect(json.copyState()).toBe('copied');
  });

  it('says it could not copy, over plain http where there is no Clipboard API, rather than looking as if it had', async () => {
    Object.defineProperty(window, 'isSecureContext', { value: false, configurable: true });
    const json = view({ sub: 'someone' });

    await json.copy();

    // jsdom has no copy command either, so the old way fails too: the button must say so.
    expect(json.copyState()).toBe('failed');
  });
});
