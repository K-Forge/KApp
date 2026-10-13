import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { OpenApiCatalogService } from '../../core/openapi/openapi-catalog.service';
import { ConsolePage, descriptionParagraphs } from './console.page';

describe('ConsolePage', () => {
  let page: ConsolePage;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConsolePage],
      providers: [provideHttpClient(), provideRouter([])],
    }).compileComponents();
    page = TestBed.createComponent(ConsolePage).componentInstance;
  });

  it('offers operations to call', () => {
    expect(page.operations().length).toBeGreaterThan(0);
  });

  // The gateway has no route for /internal/** — that is the point of the prefix. Listing those
  // here offers a button whose only outcome is a 404 that says nothing.
  it('leaves out the service-to-service endpoints the gateway does not route', () => {
    for (const service of page.services) {
      page.onServiceChange({ target: { value: service.id } } as unknown as Event);
      expect(page.operations().filter((op) => op.path.startsWith('/internal/'))).toEqual([]);
    }
  });

  it('still knows about them in the contracts, so this is a filter and not a gap', () => {
    const catalog = TestBed.inject(OpenApiCatalogService);
    const everything = catalog.services.flatMap((s) => catalog.operationsFor(s.id));
    expect(everything.some((op) => op.path.startsWith('/internal/'))).toBe(true);
  });
});

// The contracts wrap their prose at a hundred columns. Shown with those breaks kept, a phone
// cut every sentence twice.
describe('descriptionParagraphs', () => {
  it('reflows wrapped lines and keeps paragraphs apart', () => {
    const text =
      'Public endpoint: no token required, and it is\nexposed on purpose.\n\nMore than one key\nmay be present.';
    expect(descriptionParagraphs(text)).toEqual([
      'Public endpoint: no token required, and it is exposed on purpose.',
      'More than one key may be present.',
    ]);
  });

  it('keeps a list item and a table row on lines of their own', () => {
    const text = 'Two kinds:\n- a student\n- a professor\n| a | b |\n| 1 | 2 |';
    expect(descriptionParagraphs(text)).toEqual([text]);
  });
});
