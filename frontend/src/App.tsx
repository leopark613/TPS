import { useEffect, useReducer, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError, TpsApi } from './api';
import { addElement, canEditDocument, elements, mm, removeElement, replaceElement, safeColor } from './document';
import { canCopyEditor, captureSave, editorReducer, freshEditor, isDirty, LatestRequest } from './editor-state';
import type { SaveSnapshot } from './editor-state';
import type { Document, Element, Module, RevisionSummary, Session, TemplatePage } from './types';
import { Canvas } from './Canvas';
import { ReportPanel } from './ReportPanel';
import { elementLabel, fieldBinding, hasReportDataset, REPORT_FIELDS, setFieldBinding } from './report';

const uuid = () => crypto.randomUUID();
const date = (value?: string) => value ? new Date(value.endsWith('Z') || /[+-]\d\d:\d\d$/.test(value) ? value : `${value}Z`).toLocaleString('ko-KR', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '';
const errorMessage = (error: unknown) => error instanceof Error ? error.message : '요청을 처리하지 못했습니다.';

export function App() {
  const [connection, setConnection] = useState<{ api: TpsApi; session: Session } | null>(null);
  return connection ? <Workbench api={connection.api} session={connection.session} onLogout={() => setConnection(null)} /> : <Login onConnect={(api, session) => setConnection({ api, session })} />;
}

function Login({ onConnect }: { onConnect: (api: TpsApi, session: Session) => void }) {
  const [password, setPassword] = useState(''); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setBusy(true); setError('');
    const api = new TpsApi(password);
    try { const session = await api.session(); setPassword(''); onConnect(api, session); }
    catch (e) { setError(errorMessage(e)); } finally { setBusy(false); }
  };
  return <main className="login"><div className="login-mark" aria-hidden="true"><div className="mini-sheet"><span>TPS</span><i /><i /><i /></div></div><section className="login-content"><div className="brand"><strong>TPS</strong><span>Thira Report System</span></div><h1>양식 디자이너</h1><p>보고서와 라벨 양식을 만들고,<br />저장한 버전을 다시 열어 편집하세요.</p><form onSubmit={submit}><label htmlFor="password">개발 환경 연결 암호</label><input id="password" type="password" autoComplete="off" value={password} onChange={e => setPassword(e.target.value)} required autoFocus disabled={busy} /><button className="primary" disabled={busy || !password}>{busy ? '연결 중…' : '디자이너 열기'}</button>{error && <p className="error-text" role="alert">{error}</p>}</form><p className="login-note">로컬 개발 환경입니다. 암호는 이 화면을 닫으면 지워집니다.</p></section></main>;
}

function Workbench({ api, session, onLogout }: { api: TpsApi; session: Session; onLogout: () => void }) {
  const initialModule = session.enabledModules[0] ?? 'RPS';
  const [state, dispatch] = useReducer(editorReducer, undefined, () => freshEditor(initialModule, uuid()));
  const stateRef = useRef(state); stateRef.current = state;
  const [selected, setSelected] = useState<string | null>('title1');
  const [catalog, setCatalog] = useState<TemplatePage>({ items: [], page: 0, size: 50, total: 0 });
  const [catalogError, setCatalogError] = useState(''); const [catalogBusy, setCatalogBusy] = useState(false);
  const [loading, setLoading] = useState(false); const [loadError, setLoadError] = useState('');
  const [revisions, setRevisions] = useState<RevisionSummary[]>([]); const [historyError, setHistoryError] = useState('');
  const [pendingNavigation, setPendingNavigation] = useState<{ text: string; run: () => void } | null>(null);
  const [inspectorTab, setInspectorTab] = useState<'properties' | 'history'>('properties');
  const [diagnostics, setDiagnostics] = useState<string[]>([]);
  const loads = useRef(new LatestRequest()); const catalogs = useRef(new LatestRequest()); const histories = useRef(new LatestRequest());
  const mounted = useRef(true); const saving = useRef(false);
  const dirty = isDirty(state); const supported = canEditDocument(state.document); const readOnly = !supported || state.view?.editable === false;
  const locked = loading || readOnly;
  const copyAllowed = canCopyEditor(state, loading);
  const currentElement = elements(state.document).find(e => e.id === selected);

  useEffect(() => { mounted.current = true; return () => { mounted.current = false; loads.current.cancel(); catalogs.current.cancel(); histories.current.cancel(); }; }, []);
  useEffect(() => {
    const beforeUnload = (event: BeforeUnloadEvent) => { if (isDirty(stateRef.current) || stateRef.current.pending) { event.preventDefault(); event.returnValue = ''; } };
    window.addEventListener('beforeunload', beforeUnload); return () => window.removeEventListener('beforeunload', beforeUnload);
  }, []);

  const refreshCatalog = async (module: Module, page = 0) => {
    const ticket = catalogs.current.begin(); setCatalogBusy(true); setCatalogError('');
    try { const result = await api.list(module, page); if (mounted.current && catalogs.current.isCurrent(ticket)) setCatalog(result); }
    catch (e) { if (mounted.current && catalogs.current.isCurrent(ticket)) setCatalogError(errorMessage(e)); }
    finally { if (mounted.current && catalogs.current.isCurrent(ticket)) setCatalogBusy(false); }
  };
  useEffect(() => { void refreshCatalog(state.module); }, [state.module, api]); // module switch owns the catalog generation

  const refreshHistory = async (id: string) => {
    const ticket = histories.current.begin(); setHistoryError('');
    try { const result = await api.revisions(id); if (mounted.current && histories.current.isCurrent(ticket)) setRevisions(result.items); }
    catch (e) { if (mounted.current && histories.current.isCurrent(ticket)) setHistoryError(errorMessage(e)); }
  };
  const load = async (id: string, revisionId?: string) => {
    const ticket = loads.current.begin(); histories.current.cancel(); setRevisions([]); setLoading(true); setLoadError(''); setDiagnostics([]); setPendingNavigation(null);
    try {
      const view = await (revisionId ? api.revision(id, revisionId) : api.head(id));
      if (!mounted.current || !loads.current.isCurrent(ticket)) return;
      dispatch({ type: 'load', view, draftId: uuid() }); setSelected(null); void refreshHistory(id);
    } catch (e) { if (mounted.current && loads.current.isCurrent(ticket)) setLoadError(errorMessage(e)); }
    finally { if (mounted.current && loads.current.isCurrent(ticket)) setLoading(false); }
  };
  const navigate = (text: string, run: () => void) => {
    if (saving.current || stateRef.current.pending) { dispatch({ type: 'message', message: '먼저 저장 결과를 확인하거나 같은 저장을 다시 시도하세요. 편집 내용은 유지하고 있습니다.' }); return; }
    if (isDirty(stateRef.current)) setPendingNavigation({ text, run }); else run();
  };
  const newTemplate = (module = state.module) => navigate('현재 편집 내용을 버리고 새 양식을 여시겠습니까?', () => {
    loads.current.cancel(); histories.current.cancel(); setLoading(false); setRevisions([]); setSelected('title1'); setLoadError(''); setDiagnostics([]); dispatch({ type: 'new', module, id: uuid() });
  });
  const edit = (document: Document) => { if (!locked) dispatch({ type: 'edit', document }); };
  const updateElement = (update: (element: Element) => Element) => { if (selected) edit(replaceElement(state.document, selected, update)); };
  const add = (type: 'tps.text' | 'tps.rectangle' | 'tps.qr') => { const id = `e${uuid().replaceAll('-', '')}`; edit(addElement(state.document, type, id)); setSelected(id); };

  const performSave = async (snapshot: SaveSnapshot, resolveOnly = false) => {
    if (saving.current) return;
    saving.current = true; dispatch({ type: 'saving', snapshot }); setDiagnostics([]);
    try {
      const resolved = resolveOnly ? await api.resolve(snapshot) : undefined;
      if (resolved?.status === 'unknown' || (resolved?.status === 'completed' && !resolved.result)) {
        if (mounted.current) dispatch({ type: 'failed', snapshot, unknown: true, message: '완료된 저장을 아직 찾지 못했습니다. 같은 저장을 다시 시도하세요. 현재 편집은 유지됩니다.' });
        return;
      }
      const view = resolved?.result ?? await api.save(snapshot);
      if (!mounted.current) return;
      dispatch({ type: 'saved', snapshot, view });
      void refreshCatalog(snapshot.ownerModule); void refreshHistory(view.templateId);
    } catch (e) {
      if (!mounted.current) return;
      const conflict = e instanceof ApiError && e.status === 412;
      const unknown = !(e instanceof ApiError) || e.outcomeUnknown;
      dispatch({ type: 'failed', snapshot, unknown, conflict, message: conflict ? '다른 곳에서 새 버전을 저장했습니다. 현재 편집은 유지됩니다. 최신본을 확인하거나 다른 이름으로 저장하세요.' : unknown ? '저장 결과를 확인하지 못했습니다. 같은 저장을 다시 시도하거나 결과를 확인하세요. 현재 편집은 유지됩니다.' : errorMessage(e) });
      if (e instanceof ApiError) setDiagnostics(e.diagnostics.map(d => `${d.message} (${d.path})`));
    } finally { saving.current = false; }
  };
  const save = () => {
    if (readOnly || loading || saving.current || state.conflict || !session.enabledModules.includes(state.module)) return;
    void performSave(captureSave(stateRef.current, uuid()));
  };
  const copy = () => {
    if (saving.current || !canCopyEditor(stateRef.current, loading)) return;
    loads.current.cancel(); histories.current.cancel(); dispatch({ type: 'copy', id: uuid() }); setRevisions([]); setDiagnostics([]); setLoadError('');
  };
  const displayStatus = state.saveState === 'saving' ? '저장 중…' : state.saveState === 'unknown' ? '저장 확인 필요' : readOnly ? '읽기 전용' : dirty ? '저장하지 않은 변경' : '저장됨';

  return <div className="app-shell">
    <header className="topbar"><div className="brand"><strong>TPS</strong><span>Thira Report System</span></div><label className="mode-picker">작업 영역<select aria-label="작업 영역" value={state.module} onChange={e => newTemplate(e.target.value as Module)}>{session.enabledModules.map(m => <option key={m} value={m}>{m === 'RPS' ? 'RPS 보고서' : 'LPS 라벨'}</option>)}</select></label><div className="top-spacer"/><span className="local-indicator">로컬 개발</span><button className="quiet" onClick={() => navigate('저장하지 않은 편집을 버리고 연결을 종료하시겠습니까?', onLogout)}>연결 종료</button></header>
    <aside className="catalog"><div className="panel-heading"><h2>내 양식</h2><button className="icon-button" aria-label="양식 목록 새로고침" onClick={() => void refreshCatalog(state.module, catalog.page)} disabled={catalogBusy}>↻</button></div><button className="new-template" onClick={() => newTemplate()}>＋ 새 양식</button><div className="catalog-label"><span>{state.module === 'RPS' ? '일반 보고서' : '라벨 양식'}</span><span>{catalog.total}개</span></div><div className="template-list" aria-label="저장한 양식 목록" aria-busy={catalogBusy}>{catalogError && <p role="alert" className="error-text">{catalogError}</p>}{!catalogBusy && !catalog.items.length && !catalogError && <div className="catalog-empty">저장한 양식이 없습니다.<br />새 양식에서 시작하세요.</div>}{catalog.items.map(item => <button className={`template-item ${state.view?.templateId === item.templateId ? 'active' : ''}`} key={item.templateId} onClick={() => navigate('현재 편집 내용을 버리고 선택한 양식을 여시겠습니까?', () => void load(item.templateId))}><span className="sheet-icon" aria-hidden="true"/><span className="template-copy"><strong>{item.name}</strong><small>버전 {item.revisionNo} <span>{date(item.lastSavedAt)}</span></small></span></button>)}</div>{catalog.total > 50 && <div className="pagination"><button disabled={catalog.page === 0 || catalogBusy} onClick={() => void refreshCatalog(state.module, catalog.page - 1)}>이전</button><span>{catalog.page + 1}</span><button disabled={(catalog.page + 1) * catalog.size >= catalog.total || catalogBusy} onClick={() => void refreshCatalog(state.module, catalog.page + 1)}>다음</button></div>}<div className="catalog-footer">{state.module === 'RPS' ? '보고서' : '라벨'} 양식은 이 작업 영역에 저장됩니다.</div></aside>
    <main className="workspace">
      <section className="document-header"><div className="document-heading"><label className="sr-only" htmlFor="document-title">양식 이름</label><input id="document-title" className="document-title" aria-label="양식 이름" value={state.document.title} onChange={e => edit({ ...state.document, title: e.target.value })} maxLength={200} disabled={locked} /><div className="document-status"><span className={`save-status ${dirty && !readOnly ? 'unsaved' : ''}`} role="status">{displayStatus}</span>{state.view && <span>버전 {state.view.sequence}</span>}<span className="inactive-badge">업무 미적용</span></div></div><button className="primary save-button" onClick={save} disabled={locked || state.saveState === 'saving' || state.conflict || (!dirty && !state.pending)}>{state.saveState === 'saving' ? '저장 중…' : state.pending ? '같은 저장 다시 시도' : '저장'}</button></section>
      {pendingNavigation && <section className="notice navigation-notice" aria-label="편집 전환 확인"><span>{pendingNavigation.text}</span><div><button onClick={() => setPendingNavigation(null)}>계속 편집</button><button className="danger-text" onClick={() => { const action = pendingNavigation.run; setPendingNavigation(null); action(); }}>변경 버리고 이동</button></div></section>}
      {(state.message || loadError || loading) && <section className={`notice ${state.conflict || loadError || state.saveState === 'unknown' ? 'warning' : ''}`} role={state.conflict || loadError ? 'alert' : 'status'}><span>{loading ? '양식을 불러오는 중…' : loadError || state.message}</span>{state.pending && state.saveState === 'unknown' && <button onClick={() => void performSave(state.pending!, true)}>저장 결과 확인</button>}{state.conflict && <div><button onClick={() => navigate('현재 편집 내용을 버리고 최신 버전을 여시겠습니까?', () => void load(state.view!.templateId))}>최신본 열기</button><button onClick={copy} disabled={!copyAllowed}>다른 이름으로 저장</button></div>}</section>}
      {diagnostics.length > 0 && <ul className="validation-errors" role="alert">{diagnostics.map((d, i) => <li key={i}>{d}</li>)}</ul>}
      {readOnly && <section className="notice readonly-notice"><span>{state.view?.headEtag === null ? '이전 저장 버전입니다. 내용을 확인할 수 있습니다.' : '현재 편집기에서 지원하지 않는 내용이 있어 읽기 전용으로 열었습니다.'}</span>{state.view && <button onClick={() => navigate('현재 편집 내용을 버리고 최신본을 여시겠습니까?', () => void load(state.view!.templateId))}>최신본 열기</button>}</section>}
      {(state.view?.diagnostics?.length ?? 0) > 0 && <details className="diagnostic-details"><summary>문서 확인 안내 {state.view!.diagnostics.length}건</summary><ul>{state.view!.diagnostics.map((d, i) => <li key={i}>{d.message}</li>)}</ul></details>}
      <div className="toolbar" role="toolbar" aria-label="양식 그리기 도구"><button onClick={() => add('tps.text')} disabled={locked || elements(state.document).length >= 500}><b aria-hidden="true">T</b> 텍스트 추가</button><button onClick={() => add('tps.rectangle')} disabled={locked || elements(state.document).length >= 500}><span className="rectangle-symbol" aria-hidden="true"/> 사각형 추가</button><button onClick={() => add('tps.qr')} disabled={locked || elements(state.document).length >= 500}>QR 추가</button><span className="toolbar-divider"/><button disabled={locked || !selected} onClick={() => { if (selected) edit(removeElement(state.document, selected)); setSelected(null); }}>선택 삭제</button><div className="top-spacer"/><button onClick={copy} disabled={!copyAllowed}>복사하여 새 양식</button></div>
      <div className="editor-body">{state.module === 'RPS' ? <ReportPanel api={api} state={state} loading={loading} editable={!locked && state.saveState === 'idle' && !state.pending} onChange={edit}><Canvas document={state.document} selected={selected} onSelect={setSelected} onChange={edit} readOnly={locked} /></ReportPanel> : <Canvas document={state.document} selected={selected} onSelect={setSelected} onChange={edit} readOnly={locked} />}</div>
      <footer className="workspace-footer"><span>요소 {elements(state.document).length}개</span><span>저장은 편집본을 보관합니다. 업무 적용은 별도 기능입니다.</span></footer>
    </main>
    <aside className="inspector"><div className="inspector-tabs" role="tablist" aria-label="문서 정보"><button role="tab" aria-selected={inspectorTab === 'properties'} onClick={() => setInspectorTab('properties')}>속성</button><button role="tab" aria-selected={inspectorTab === 'history'} onClick={() => setInspectorTab('history')}>저장 이력</button></div>{inspectorTab === 'properties' ? <div className="properties-panel"><h2>{currentElement ? currentElement.type === 'tps.text' ? '텍스트' : currentElement.type === 'tps.qr' ? 'QR 코드' : '사각형' : '양식 설정'}</h2>{currentElement ? <ElementInspector element={currentElement} update={updateElement} disabled={locked} connected={state.module === 'RPS' && hasReportDataset(state.document)} /> : <div className="no-selection"><span className="selection-glyph" aria-hidden="true">⌖</span><p>캔버스에서 요소를 선택하면<br />내용과 위치를 바꿀 수 있습니다.</p><dl><dt>페이지</dt><dd>{mm(state.document.sections[0].page.width)} × {mm(state.document.sections[0].page.height)} mm</dd><dt>저장 영역</dt><dd>{state.module}</dd></dl></div>}<div className="element-order"><h3>페이지 요소</h3>{elements(state.document).map((el, i) => <button key={el.id} className={selected === el.id ? 'chosen' : ''} onClick={() => setSelected(el.id)}><span>{el.type === 'tps.text' ? 'T' : el.type === 'tps.qr' ? 'QR' : '□'}</span>{el.type === 'tps.rectangle' ? `사각형 ${i + 1}` : elementLabel(el)}</button>)}</div></div> : <div className="history-panel"><h2>저장 이력</h2><p>저장할 때마다 새 버전이 남습니다.<br />이전 버전은 읽기 전용입니다.</p>{historyError && <p role="alert" className="error-text">{historyError}</p>}{!state.view && <p className="empty-history">첫 저장 후 이력이 표시됩니다.</p>}{revisions.map(revision => <button className={`revision-item ${state.view?.revisionId === revision.revisionId ? 'active' : ''}`} key={revision.revisionId} onClick={() => navigate('현재 편집 내용을 버리고 이 저장 버전을 여시겠습니까?', () => void load(state.view!.templateId, revision.revisionId))}><span className="revision-dot"/><span><strong>버전 {revision.sequence}</strong><small>{date(revision.createdAt)}</small><small>{revision.createdBy}</small></span>{revision.revisionId === state.view?.headRevisionId && <em>최신</em>}</button>)}{revisions.length === 100 && <p>최근 100개 저장 버전을 표시합니다.</p>}</div>}<details className="raw-document"><summary>문서 내용 보기</summary><pre aria-label="문서 JSON">{JSON.stringify(state.document, null, 2)}</pre></details></aside>
  </div>;
}

function ElementInspector({ element, update, disabled, connected }: { element: Element; update: (fn: (element: Element) => Element) => void; disabled: boolean; connected: boolean }) {
  const binding = fieldBinding(element);
  const property = (key: string, value: string) => update(el => ({ ...el, properties: { ...el.properties, [key]: value } }));
  const dimension = (key: 'x' | 'y' | 'width' | 'height', value: string) => {
    if (!value.trim()) return;
    const integer = Math.round(Number(value) * 1000);
    if (!Number.isSafeInteger(integer) || (['width', 'height'].includes(key) && integer < 1)) return;
    update(el => ({ ...el, frame: { ...el.frame, [key]: integer } }));
  };
  return <fieldset className="element-fields" disabled={disabled}>
    {['tps.text', 'tps.qr'].includes(element.type) && <><label htmlFor="element-binding">표시할 내용</label><select id="element-binding" aria-label="표시할 내용" value={binding?.source.fieldId ?? ''} onChange={e => update(el => setFieldBinding(el, e.target.value))}><option value="">직접 입력</option>{connected && REPORT_FIELDS.map(f => <option key={f.id} value={f.id}>{f.label}</option>)}</select>{binding && <><label htmlFor="binding-null">데이터가 비어 있을 때</label><select id="binding-null" aria-label="데이터가 비어 있을 때" value={binding.nullPolicy} onChange={e => update(el => setFieldBinding(el, binding.source.fieldId, e.target.value as 'error' | 'empty'))}><option value="error">확인 필요로 알림</option><option value="empty">빈 문구로 표시</option></select><p className="field-hint">배치 화면에는 항목 이름을 표시합니다. 저장본 미리보기에서 실제 내용을 확인하세요.{element.type === 'tps.qr' ? ' QR 내용이 비면 미리보기를 만들 수 없습니다.' : ''}</p></>}</>}
    {element.type === 'tps.qr' && <><label htmlFor="qr-payload">QR 문구</label><textarea id="qr-payload" aria-label="QR 문구" rows={4} value={typeof element.properties.payload === 'string' ? element.properties.payload : ''} disabled={Boolean(binding)} onChange={e => property('payload', e.target.value)} /><p className="field-hint">한글·공백·줄바꿈을 그대로 사용합니다. 배치용 QR 표시는 읽을 수 있는 코드가 아닙니다.</p></>}
    {element.type === 'tps.text' && <><label htmlFor="element-text">문구</label><textarea id="element-text" aria-label="요소 문구" rows={4} disabled={Boolean(binding)} value={typeof element.properties.text === 'string' ? element.properties.text : ''} onChange={e => property('text', e.target.value)} /><div className="property-row"><label>글자 크기 (pt)<input aria-label="글자 크기" type="number" step="0.5" min="4" max="200" value={String(element.properties.fontSizePt ?? '12')} onChange={e => { if (Number(e.target.value) >= 4 && Number(e.target.value) <= 200) property('fontSizePt', e.target.value); }} /></label><label>글자 색<input aria-label="글자 색" type="color" value={safeColor(element.properties.color, '#17324d')} onChange={e => property('color', e.target.value)} /></label></div></>}
    {element.type === 'tps.rectangle' && <div className="property-row"><label>채우기<input aria-label="사각형 채우기" type="color" value={safeColor(element.properties.fill, '#eef2f6')} onChange={e => property('fill', e.target.value)} /></label><label>테두리<input aria-label="사각형 테두리" type="color" value={safeColor(element.properties.stroke, '#2463a6')} onChange={e => property('stroke', e.target.value)} /></label></div>}
    <h3>위치와 크기 <small>mm</small></h3><div className="dimension-grid">{(['x', 'y', 'width', 'height'] as const).map(key => <label key={key}>{({ x: '가로 위치', y: '세로 위치', width: '너비', height: '높이' })[key]}<input aria-label={({'x':'가로 위치','y':'세로 위치','width':'너비','height':'높이'})[key]} type="number" step="0.1" value={mm(element.frame[key])} onChange={e => dimension(key, e.target.value)} /></label>)}</div><p className="field-hint">위치는 페이지 여백 안쪽을 기준으로 합니다.</p>
  </fieldset>;
}
