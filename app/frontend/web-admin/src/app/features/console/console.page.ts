import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { PageIntroComponent } from '../../shared/ui/page-intro/page-intro.component';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ApiConfigService } from '../../core/config/api-config.service';
import { OpenApiCatalogService } from '../../core/openapi/openapi-catalog.service';
import type { ConsoleOperation, ConsoleParam } from '../../core/openapi/console-operation.model';
import { JsonViewComponent } from '../../shared/ui/json-view/json-view.component';
import { RequestHistoryService } from './request-history.service';
import type { ConsoleHeader, ConsoleResult, HistoryEntry } from './console.model';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { apiText, t } from '../../core/i18n/i18n.service';

function operationKey(op: Pick<ConsoleOperation, 'method' | 'path'>): string {
  return `${op.method} ${op.path}`;
}

/**
 * The request builder: pick a service, pick an endpoint parsed straight from its OpenAPI spec,
 * fill parameters, fire it with the current session's token, and read back exactly what the
 * gateway sent - status, timing, headers, body. This is what lets a mobile developer reproduce a
 * failing call in seconds instead of rebuilding it by hand in curl.
 */
@Component({
  selector: 'app-console-page',
  imports: [TranslatePipe, JsonViewComponent, PageIntroComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="console-layout">
      <div class="stack console-main">
        <app-page-intro
          [title]="'API console' | t"
          [what]="'Call any service endpoint with your own token.' | t"
          [can]="[('Pick an operation' | t), ('Fill it from the contract’s examples' | t), ('Send it and read the answer' | t), ('See what you sent before' | t)]"
          [note]="'It uses your real token on the real gateway: a DELETE here deletes. Endpoints under /internal are not listed; they are on “Who can do what”.' | t"
        />

        <div class="card stack">
          <div class="field">
            <label for="service">{{ 'Service' | t }}</label>
            <select id="service" (change)="onServiceChange($event)">
              @for (service of services; track service.id) {
                <option [value]="service.id" [selected]="service.id === selectedServiceId()">{{ service.label | t }}</option>
              }
            </select>
          </div>

          <div class="field">
            <label for="operation">{{ 'Endpoint' | t }}</label>
            <select id="operation" (change)="onOperationChange($event)">
              @for (op of operations(); track operationKey(op)) {
                <option [value]="operationKey(op)" [selected]="operationKey(op) === selectedOpKey()">
                  {{ op.method.toUpperCase() }} {{ op.path }} — {{ said(op.summary) }}
                </option>
              }
            </select>
          </div>

          @if (selectedOperation(); as op) {
            @if (op.description) {
              <div class="text-muted op-description">
                @for (paragraph of descriptionParagraphs(); track $index) {
                  <p>{{ paragraph }}</p>
                }
              </div>
            }

            @if (op.pathParams.length) {
              <h3>{{ 'Path parameters' | t }}</h3>
              @for (param of op.pathParams; track param.name) {
                <div class="field">
                  <label [for]="'path-' + param.name">
                    {{ param.name }}
                    @if (param.required) {
                      <span class="text-faint">{{ '(required)' | t }}</span>
                    }
                  </label>
                  @if (param.schema.enum?.length) {
                    <select [id]="'path-' + param.name" (change)="onParamChange(param, $event)">
                      <option value="" disabled [selected]="!paramValue(param)">{{ 'choose…' | t }}</option>
                      @for (opt of param.schema.enum; track opt) {
                        <option [value]="opt" [selected]="opt === paramValue(param)">{{ opt }}</option>
                      }
                    </select>
                  } @else {
                    <input [id]="'path-' + param.name" type="text" [value]="paramValue(param)" (input)="onParamChange(param, $event)" />
                  }
                  @if (param.description) {
                    <span class="hint">{{ said(param.description) }}</span>
                  }
                </div>
              }
            }

            @if (op.queryParams.length) {
              <h3>{{ 'Query parameters' | t }}</h3>
              @for (param of op.queryParams; track param.name) {
                <div class="field">
                  <label [for]="'query-' + param.name">
                    {{ param.name }}
                    @if (param.required) {
                      <span class="text-faint">{{ '(required)' | t }}</span>
                    }
                  </label>
                  @if (param.schema.enum?.length) {
                    <select [id]="'query-' + param.name" (change)="onParamChange(param, $event)">
                      <option value="" [selected]="!paramValue(param)">{{ '(omit)' | t }}</option>
                      @for (opt of param.schema.enum; track opt) {
                        <option [value]="opt" [selected]="opt === paramValue(param)">{{ opt }}</option>
                      }
                    </select>
                  } @else {
                    <input
                      [id]="'query-' + param.name"
                      [type]="isNumeric(param) ? 'number' : 'text'"
                      [value]="paramValue(param)"
                      (input)="onParamChange(param, $event)"
                    />
                  }
                  @if (param.description) {
                    <span class="hint">{{ said(param.description) }}</span>
                  }
                </div>
              }
            }

            @if (op.requestBodySchema) {
              <div class="field">
                <label for="body">{{ 'Request body (JSON)' | t }}</label>
                <textarea id="body" rows="12" [value]="bodyText()" (input)="onBodyChange($event)"></textarea>
                @if (bodyParseError()) {
                  <span class="error">{{ bodyParseError() }}</span>
                }
              </div>
            }

            <div class="row">
              <button type="button" class="btn btn-primary" (click)="send()" [disabled]="sending()">
                {{ sending() ? ('Sending…' | t) : ('Send ' | t) + op.method.toUpperCase() }}
              </button>
              @if (validationError()) {
                <span class="error">{{ validationError() }}</span>
              }
            </div>
          } @else {
            <div class="empty-state">
              <p>{{ 'This service has no operations to show.' | t }}</p>
            </div>
          }
        </div>

        @if (sending()) {
          <div class="card empty-state">
            <p>{{ 'Waiting for a response…' | t }}</p>
          </div>
        } @else if (result(); as result) {
          <div class="card stack">
            <div class="row-between">
              <div class="row">
                <span class="badge" [class]="statusBadgeClass(result.status)">{{ result.status }} {{ result.statusText }}</span>
                <span class="text-muted">{{ '{timeMs} ms' | t: { timeMs: result.timeMs } }}</span>
              </div>
            </div>

            @if (result.headers.length) {
              <details>
                <summary>{{ 'Response headers ({length})' | t: { length: result.headers.length } }}</summary>
                <table class="scroll-x">
                  <tbody>
                    @for (header of result.headers; track header.key) {
                      <tr>
                        <td class="mono">{{ header.key }}</td>
                        <td class="mono">{{ header.value }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </details>
            }

            <app-json-view [value]="result.body" />

            <!-- What the field names mean. Read from the contract, which already explains every
                 one of them - kty, e, kid and the rest - and had never shown anybody. -->
            @if (selectedOperation()?.responseFields?.length) {
              <details class="fields" open>
                <summary>{{ 'What these fields mean' | t }}</summary>
                <div class="scroll-x">
                  <table class="field-table">
                    <thead>
                      <tr>
                        <th>{{ 'Field' | t }}</th>
                        <th>{{ 'Type' | t }}</th>
                        <th>{{ 'Meaning' | t }}</th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (field of selectedOperation()!.responseFields; track field.path) {
                        <tr>
                          <td class="mono">
                            {{ field.path }}@if (field.required) {<span class="field-req" [title]="'Always present' | t">*</span>}
                          </td>
                          <td class="text-muted">{{ field.type }}</td>
                          <td>
                            {{ said(field.description) || '—' }}
                            @if (field.enumValues?.length) {
                              <span class="text-muted">{{ 'One of: {value}.' | t: { value: field.enumValues!.join(', ') } }}</span>
                            }
                          </td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              </details>
            }
          </div>
        }
      </div>

      <aside class="card console-history">
        <div class="row-between">
          <h2 style="margin:0">{{ 'History' | t }}</h2>
          @if (history.entries().length) {
            <button type="button" class="btn btn-ghost btn-sm" (click)="history.clear()">{{ 'Clear' | t }}</button>
          }
        </div>

        @if (!history.entries().length) {
          <p class="text-faint">{{ 'Requests you send appear here, newest first.' | t }}</p>
        } @else {
          <ul class="history-list">
            @for (entry of history.entries(); track entry.id) {
              <li>
                <button type="button" class="history-entry" (click)="restore(entry)">
                  <span class="row-between">
                    <span class="badge" [class]="statusBadgeClass(entry.result.status)">{{ entry.result.status }}</span>
                    <span class="text-faint">{{ '{timeMs} ms' | t: { timeMs: entry.result.timeMs } }}</span>
                  </span>
                  <span class="mono history-path">{{ entry.method.toUpperCase() }} {{ entry.path }}</span>
                  <span class="text-faint">{{ relativeTime(entry.timestamp) }}</span>
                </button>
              </li>
            }
          </ul>
        }
      </aside>
    </div>
  `,
  styles: `
    .fields {
      margin-top: 0.85rem;
      border-top: 1px solid var(--border);
      padding-top: 0.75rem;
    }
    .fields summary {
      cursor: pointer;
      font-weight: 600;
      font-size: 0.8125rem;
      color: var(--text-muted);
    }
    .field-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.8125rem;
      margin-top: 0.6rem;
    }
    .field-table th,
    .field-table td {
      text-align: left;
      padding: 0.3rem 0.75rem 0.3rem 0;
      vertical-align: top;
      border-bottom: 1px solid var(--border);
    }
    .field-table th {
      font-size: 0.6875rem;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      color: var(--text-muted);
    }
    .field-table td:first-child {
      white-space: nowrap;
    }
    .field-req {
      color: var(--text-faint);
    }

    .console-layout {
      display: grid;
      grid-template-columns: 1fr 20rem;
      gap: 1.5rem;
      align-items: start;
    }
    .console-history {
      position: sticky;
      top: 0;
    }
    .op-description {
      margin: -0.5rem 0 0.5rem;
    }
    .op-description p {
      margin: 0 0 0.5rem;
      white-space: pre-line;
    }
    h3 {
      margin: 0.5rem 0 0;
      font-size: 0.8125rem;
      text-transform: uppercase;
      letter-spacing: 0.02em;
      color: var(--text-muted);
    }
    .history-list {
      list-style: none;
      margin: 0.75rem 0 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
    }
    .history-entry {
      width: 100%;
      text-align: left;
      display: flex;
      flex-direction: column;
      gap: 0.15rem;
      background: var(--bg-inset);
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
      padding: 0.5rem 0.6rem;
      font-size: 0.75rem;
    }
    .history-entry:hover {
      border-color: var(--border-strong);
    }
    .history-path {
      word-break: break-all;
    }
    @media (max-width: 960px) {
      .console-layout {
        grid-template-columns: 1fr;
      }
      .console-history {
        position: static;
      }
    }
  `,
})
export class ConsolePage {
  private readonly http = inject(HttpClient);
  private readonly apiConfig = inject(ApiConfigService);
  private readonly catalog = inject(OpenApiCatalogService);
  protected readonly history = inject(RequestHistoryService);

  readonly services = this.catalog.services;
  readonly operationKey = operationKey;

  readonly selectedServiceId = signal(this.services[0]?.id ?? '');
  /**
   * Everything the console can actually call.
   *
   * <p>`/internal/**` is excluded because the gateway has no route for it - that is the whole
   * point of the prefix. Offering those operations here means offering a button whose only
   * possible outcome is a 404 from the gateway, which reads like the endpoint is broken rather
   * than like it was never reachable from a browser. They stay on the "Who can do what" screen,
   * where SERVICE_ONLY is the interesting fact about them.
   */
  readonly operations = computed(() =>
    this.catalog
      .operationsFor(this.selectedServiceId())
      .filter((op) => !op.path.startsWith('/internal/')),
  );

  readonly selectedOpKey = signal<string | null>(null);
  readonly selectedOperation = computed(
    () => this.operations().find((op) => operationKey(op) === this.selectedOpKey()) ?? null,
  );
  /** What the contract says, in the portal's language. */
  said(text: string | null | undefined): string {
    return apiText(text);
  }

  readonly descriptionParagraphs = computed(() =>
    descriptionParagraphs(apiText(this.selectedOperation()?.description)),
  );

  readonly pathValues = signal<Record<string, string>>({});
  readonly queryValues = signal<Record<string, string>>({});
  readonly bodyText = signal('');
  readonly bodyParseError = signal<string | null>(null);
  readonly validationError = signal<string | null>(null);

  readonly sending = signal(false);
  readonly result = signal<ConsoleResult | null>(null);

  constructor() {
    this.selectOperation(this.operations()[0] ?? null);
  }

  onServiceChange(event: Event): void {
    const id = (event.target as HTMLSelectElement).value;
    this.selectedServiceId.set(id);
    this.selectOperation(this.operations()[0] ?? null);
  }

  onOperationChange(event: Event): void {
    const key = (event.target as HTMLSelectElement).value;
    this.selectOperation(this.operations().find((op) => operationKey(op) === key) ?? null);
  }

  private selectOperation(op: ConsoleOperation | null): void {
    this.selectedOpKey.set(op ? operationKey(op) : null);
    this.pathValues.set({});
    this.queryValues.set({});
    this.bodyText.set(op?.requestBodySchema ? JSON.stringify(op.requestBodyExample, null, 2) : '');
    this.bodyParseError.set(null);
    this.validationError.set(null);
    this.result.set(null);
  }

  paramValue(param: ConsoleParam): string {
    const store = param.in === 'path' ? this.pathValues() : this.queryValues();
    return store[param.name] ?? '';
  }

  isNumeric(param: ConsoleParam): boolean {
    return param.schema.type === 'integer' || param.schema.type === 'number';
  }

  onParamChange(param: ConsoleParam, event: Event): void {
    const value = (event.target as HTMLInputElement | HTMLSelectElement).value;
    const target = param.in === 'path' ? this.pathValues : this.queryValues;
    target.update((current) => ({ ...current, [param.name]: value }));
  }

  onBodyChange(event: Event): void {
    this.bodyText.set((event.target as HTMLTextAreaElement).value);
    this.bodyParseError.set(null);
  }

  statusBadgeClass(status: number): string {
    if (status >= 200 && status < 300) return 'badge-success';
    if (status >= 400) return 'badge-danger';
    if (status >= 300) return 'badge-warning';
    return 'badge-neutral';
  }

  relativeTime(timestamp: number): string {
    const seconds = Math.round((Date.now() - timestamp) / 1000);
    if (seconds < 60) return t('{n}s ago', { n: seconds });
    const minutes = Math.round(seconds / 60);
    if (minutes < 60) return t('{n}m ago', { n: minutes });
    const hours = Math.round(minutes / 60);
    return t('{n}h ago', { n: hours });
  }

  restore(entry: HistoryEntry): void {
    this.selectedServiceId.set(entry.serviceId);
    this.selectedOpKey.set(`${entry.method} ${entry.path}`);
    this.pathValues.set({ ...entry.pathValues });
    this.queryValues.set({ ...entry.queryValues });
    this.bodyText.set(entry.bodyText);
    this.bodyParseError.set(null);
    this.validationError.set(null);
    this.result.set(entry.result);
  }

  send(): void {
    const op = this.selectedOperation();
    if (!op || this.sending()) {
      return;
    }

    const missing = [...op.pathParams, ...op.queryParams].filter((p) => p.required && !this.paramValue(p).trim());
    if (missing.length) {
      const names = missing.map((p) => p.name).join(', ');
      this.validationError.set(missing.length > 1 ? t('Missing required parameters: {names}', { names }) : t('Missing required parameter: {names}', { names }));
      return;
    }

    let parsedBody: unknown;
    if (op.requestBodySchema) {
      const text = this.bodyText().trim();
      try {
        parsedBody = text ? JSON.parse(text) : undefined;
      } catch {
        this.bodyParseError.set(t('Body is not valid JSON.'));
        return;
      }
    }

    this.validationError.set(null);
    this.bodyParseError.set(null);

    const resolvedPath = op.path.replace(/\{(\w+)\}/g, (_match, name: string) => encodeURIComponent(this.pathValues()[name] ?? ''));
    const url = `${this.apiConfig.baseUrl()}${resolvedPath}`;

    let httpParams = new HttpParams();
    for (const qp of op.queryParams) {
      const value = this.queryValues()[qp.name];
      if (value) {
        httpParams = httpParams.set(qp.name, value);
      }
    }

    this.sending.set(true);
    this.result.set(null);
    const startedAt = performance.now();

    this.http
      .request(op.method.toUpperCase(), url, { params: httpParams, body: parsedBody, observe: 'response' })
      .subscribe({
        next: (res) => this.finish(op, startedAt, res as HttpResponse<unknown>, false),
        error: (err: unknown) => {
          if (err instanceof HttpErrorResponse) {
            this.finish(op, startedAt, err, true);
          } else {
            this.sending.set(false);
          }
        },
      });
  }

  private finish(op: ConsoleOperation, startedAt: number, res: HttpResponse<unknown> | HttpErrorResponse, isError: boolean): void {
    const timeMs = Math.round(performance.now() - startedAt);
    const headers: ConsoleHeader[] = headersOf(res.headers).map((key) => ({ key, value: res.headers.get(key) ?? '' }));

    // A status-0 error's `.error` is a raw ProgressEvent (no useful own properties, so it prints
    // as "{}") - the request never reached a server at all: wrong base URL, CORS, or nothing
    // listening. That is worth saying plainly rather than showing an empty object.
    const body =
      isError && res.status === 0
        ? t('Request failed before reaching a server. Check the gateway base URL and that the service is running.')
        : isError
          ? (res as HttpErrorResponse).error
          : (res as HttpResponse<unknown>).body;

    const result: ConsoleResult = { status: res.status, statusText: res.statusText || '', timeMs, headers, body, isError };
    this.result.set(result);
    this.sending.set(false);

    this.history.add({
      id: crypto.randomUUID(),
      timestamp: Date.now(),
      serviceId: op.serviceId,
      method: op.method,
      path: op.path,
      pathValues: { ...this.pathValues() },
      queryValues: { ...this.queryValues() },
      bodyText: this.bodyText(),
      result,
    });
  }
}

/**
 * A contract's description as paragraphs to reflow.
 *
 * <p>The YAML wraps its prose at a hundred columns, and shown with its line breaks kept that
 * wrapping came through as ragged half-lines on anything narrower than the file - a phone cut
 * every sentence twice. A blank line still ends a paragraph, and a line that opens a list or a
 * table row keeps its own line.
 */
export function descriptionParagraphs(text: string): string[] {
  return text
    .trim()
    .split(/\n\s*\n/)
    .map((block) =>
      block.split('\n').reduce((out, line) => {
        const trimmed = line.trim();
        if (!out) {
          return trimmed;
        }
        const ownLine = /^([-*>]|\d+\.)\s/.test(trimmed) || trimmed.startsWith('|');
        return ownLine ? `${out}\n${trimmed}` : `${out} ${trimmed}`;
      }, ''),
    )
    .filter((paragraph) => paragraph.length > 0);
}

function headersOf(headers: HttpHeaders): string[] {
  return headers.keys();
}
