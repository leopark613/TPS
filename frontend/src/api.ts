import type { Diagnostic, Module, RevisionSummary, Session, TemplatePage, TemplateView } from './types';
import type { SaveSnapshot } from './editor-state';

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public diagnostics: Diagnostic[] = []) { super(message); this.name = 'ApiError'; }
  get outcomeUnknown(): boolean { return this.status === 0 || this.status >= 500; }
}

export class TpsApi {
  private readonly auth: string;
  constructor(password: string) {
    const bytes = new TextEncoder().encode(`tps-developer:${password}`);
    this.auth = `Basic ${btoa(Array.from(bytes, byte => String.fromCharCode(byte)).join(''))}`;
  }
  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 20000);
    try {
      const headers = new Headers(init.headers);
      headers.set('Authorization', this.auth);
      headers.set('Accept', 'application/json');
      if (init.method && init.method !== 'GET') { headers.set('Content-Type', 'application/json'); headers.set('X-TPS-Client', 'designer'); }
      const response = await fetch(`/api/tps${path}`, { ...init, headers, signal: controller.signal, credentials: 'omit', cache: 'no-store' });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new ApiError(response.status, typeof body.code === 'string' ? body.code : 'REQUEST_FAILED', typeof body.message === 'string' ? body.message : response.status === 401 ? '암호를 확인하고 다시 연결하세요.' : '요청을 처리하지 못했습니다.', Array.isArray(body.diagnostics) ? body.diagnostics : []);
      return body as T;
    } catch (error) {
      if (error instanceof ApiError) throw error;
      throw new ApiError(0, 'NETWORK_ERROR', '서버 응답을 확인하지 못했습니다. 연결 상태를 확인하세요.');
    } finally { clearTimeout(timeout); }
  }
  session(): Promise<Session> { return this.request('/session'); }
  list(module: Module, page = 0): Promise<TemplatePage> { return this.request(`/templates?module=${module}&page=${page}&size=50`); }
  head(id: string): Promise<TemplateView> { return this.request(`/templates/${encodeURIComponent(id)}/head`); }
  revisions(id: string): Promise<{ items: RevisionSummary[] }> { return this.request(`/templates/${encodeURIComponent(id)}/revisions`); }
  revision(id: string, revisionId: string): Promise<TemplateView> { return this.request(`/templates/${encodeURIComponent(id)}/revisions/${encodeURIComponent(revisionId)}`); }
  save(snapshot: SaveSnapshot): Promise<TemplateView> {
    return snapshot.operation === 'CREATE' ? this.request('/templates', { method: 'POST', headers: { 'Idempotency-Key': snapshot.key }, body: JSON.stringify({ clientDraftId: snapshot.scopeId, ownerModule: snapshot.ownerModule, document: snapshot.document }) }) :
      this.request(`/templates/${encodeURIComponent(snapshot.scopeId)}/head`, { method: 'PUT', headers: { 'Idempotency-Key': snapshot.key, 'If-Match': snapshot.etag ?? '' }, body: JSON.stringify({ document: snapshot.document }) });
  }
  resolve(snapshot: SaveSnapshot): Promise<{ status: 'completed' | 'unknown'; result?: TemplateView }> {
    return this.request('/save-requests/resolve', { method: 'POST', body: JSON.stringify({ operation: snapshot.operation, scopeId: snapshot.scopeId, requestKey: snapshot.key }) });
  }
}
