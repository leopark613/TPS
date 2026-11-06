import { useLayoutEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import type { TpsApi } from './api';
import type { EditorState } from './editor-state';
import { LatestRequest } from './editor-state';
import type { Document } from './types';
import { safeColor, safeFontSize } from './document';
import { canPreview, connectReportData, disconnectReportData, hasReportDataset, isReportDataset, previewContext, qrPath } from './report';
import type { ReportList, ReportPreview } from './report';

const message = (error: unknown) => error instanceof Error ? error.message : '결과를 확인하지 못했습니다. 다시 시도하세요.';

/** Local example query + saved-revision preview. Never persists rows, selections or preview data. */
export function ReportPanel({ api, state, loading, editable, onChange, children }: { api: TpsApi; state: EditorState; loading: boolean; editable: boolean; onChange: (doc: Document) => void; children: ReactNode }) {
  const [data, setData] = useState<{ draftId: string; result: ReportList } | null>(null);
  const [selected, setSelected] = useState<{ draftId: string; id: string }>({ draftId: state.draftId, id: '' });
  const [dataBusy, setDataBusy] = useState(false); const [previewBusy, setPreviewBusy] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState<{ key: string; preview: ReportPreview } | null>(null);
  const lists = useRef(new LatestRequest()); const previews = useRef(new LatestRequest()); const mounted = useRef(true);
  const context = previewContext(state, loading);
  const reportId = selected.draftId === state.draftId ? selected.id : '';
  const key = `${context}:${reportId}`;
  const current = useRef({ context, key }); current.current = { context, key };
  const connected = hasReportDataset(state.document);
  const currentData = data?.draftId === state.draftId ? data.result : null;
  const visible = result?.key === key ? result.preview : null;
  const allowed = canPreview(state, loading, reportId) && !dataBusy;

  useLayoutEffect(() => { mounted.current = true; return () => { mounted.current = false; lists.current.cancel(); previews.current.cancel(); }; }, []);
  useLayoutEffect(() => {
    previews.current.cancel(); setResult(null); setPreviewBusy(false); setError('');
  }, [key]);
  useLayoutEffect(() => {
    lists.current.cancel(); setData(null); setSelected({ draftId: state.draftId, id: '' }); setDataBusy(false);
  }, [state.draftId]);

  const clearPreview = () => { previews.current.cancel(); setResult(null); setPreviewBusy(false); setError(''); };
  const fetchData = async () => {
    if (loading || dataBusy || (!connected && !editable)) return;
    const ticket = lists.current.begin(); const requestContext = context; const draftId = state.draftId;
    clearPreview(); setDataBusy(true); setData(null); setSelected({ draftId, id: '' });
    try {
      const response = await api.reportList();
      if (!mounted.current || !lists.current.isCurrent(ticket) || current.current.context !== requestContext) return;
      if (!isReportDataset(response.dataset) || !Array.isArray(response.rows) || response.rows.some(row => typeof row.reportId !== 'string' || typeof row.reportName !== 'string' || (row.reportClassId !== null && typeof row.reportClassId !== 'string'))) throw new Error('지원하지 않는 데이터 응답입니다.');
      setData({ draftId, result: response });
      if (!connected) onChange(connectReportData(state.document, response.dataset));
    } catch (e) {
      if (mounted.current && lists.current.isCurrent(ticket) && current.current.context === requestContext) { setData(null); setError(message(e)); }
    } finally { if (mounted.current && lists.current.isCurrent(ticket)) setDataBusy(false); }
  };
  const preview = async () => {
    if (!allowed || previewBusy || !state.view) return;
    const ticket = previews.current.begin(); const requestKey = key; const saved = state.view;
    const row = connected ? reportId : null;
    setResult(null); setError(''); setPreviewBusy(true);
    try {
      const response = await api.reportPreview(saved.templateId, saved.revisionId, row);
      if (!mounted.current || !previews.current.isCurrent(ticket) || current.current.key !== requestKey) return;
      // Check identity before displaying a result, including the exact saved body and selected row.
      if (response.templateId !== saved.templateId || response.revisionId !== saved.revisionId || response.bodyHash !== saved.bodyHash || response.reportId !== row || response.units !== 'um' || !Array.isArray(response.elements) || !Array.isArray(response.diagnostics)) throw new Error('요청한 저장본과 미리보기 결과가 일치하지 않습니다. 다시 시도하세요.');
      for (const element of response.elements) {
        if (!['tps.text', 'tps.rectangle', 'tps.qr'].includes(element.type)) throw new Error('표시할 수 없는 요소가 포함되어 있습니다.');
        if (element.type === 'tps.qr') { if (!element.qr) throw new Error('QR 결과가 없습니다.'); qrPath(element.qr); }
      }
      setResult({ key: requestKey, preview: response });
    } catch (e) {
      if (mounted.current && previews.current.isCurrent(ticket) && current.current.key === requestKey) { setResult(null); setError(message(e)); }
    } finally { if (mounted.current && previews.current.isCurrent(ticket)) setPreviewBusy(false); }
  };

  return <>
    <section className="report-tools" aria-label="보고서 데이터와 미리보기">
      <div className="report-tool-actions">
        <button onClick={() => void fetchData()} disabled={loading || dataBusy || (!connected && !editable)}>{dataBusy ? '데이터 조회 중…' : connected ? '데이터 새로고침' : '데이터 연결'}</button>
        {connected && <label>미리볼 보고서<select aria-label="미리볼 보고서" value={reportId} disabled={loading || dataBusy} onChange={e => { clearPreview(); setSelected({ draftId: state.draftId, id: e.target.value }); }}><option value="">선택하세요</option>{currentData?.rows.map(row => <option key={row.reportId} value={row.reportId}>{row.reportName || row.reportId} ({row.reportId})</option>)}</select></label>}
        <button className="preview-button" onClick={() => void preview()} disabled={!allowed || previewBusy}>{previewBusy ? '미리보기 만드는 중…' : '저장본 미리보기'}</button>
        {visible && <button onClick={clearPreview}>배치 편집으로 돌아가기</button>}
      </div>
      <p className="report-note">{connected ? '로컬 보고서 목록 예제에 연결했습니다. 요소 속성에서 표시할 항목을 고르세요.' : '텍스트·QR에 보고서 정보를 연결할 수 있습니다. 직접 입력한 내용만 미리볼 수도 있습니다.'}</p>
      {connected && !currentData && !dataBusy && <p className="report-note">데이터 새로고침 후 미리볼 보고서를 선택하세요.</p>}
      {currentData?.truncated && <p className="report-note">보고서는 최대 100개까지 표시합니다.</p>}
      {currentData && !currentData.rows.length && <p className="report-note">조회 가능한 보고서가 없습니다. 연결된 원본 사이트 설정을 확인하세요.</p>}
      {(state.generation !== state.savedGeneration || !state.view) && <p className="report-note">미리보려면 먼저 양식을 저장하세요. 자동으로 저장하지 않습니다.</p>}
      {error && <p role="alert" className="error-text">{error}</p>}
      {connected && editable && <button className="quiet disconnect-data" disabled={loading || dataBusy} onClick={() => { clearPreview(); setData(null); setSelected({ draftId: state.draftId, id: '' }); onChange(disconnectReportData(state.document)); }}>데이터 연결 해제</button>}
    </section>
    {visible ? <PreviewCanvas preview={visible} sequence={state.view?.sequence ?? ''} /> : children}
  </>;
}

export function PreviewCanvas({ preview, sequence }: { preview: ReportPreview; sequence: string }) {
  const { page } = preview;
  return <div className="canvas-stage saved-preview" data-testid="saved-preview">
    <div className="paper-meta"><span>저장 버전 {sequence}</span><strong>선택한 한 행의 데이터 미리보기</strong></div>
    <svg className="paper" role="img" aria-label="저장본 데이터 미리보기" viewBox={`0 0 ${page.width} ${page.height}`}>
      <rect width={page.width} height={page.height} fill="white" />
      <g transform={`translate(${page.margins.left},${page.margins.top})`}>
        {preview.elements.map(element => {
          const f = element.frame; const font = safeFontSize(element.properties.fontSizePt) * 352.778;
          const size = element.qr ? element.qr.moduleCount + 8 : 0;
          return <g key={element.id} transform={`translate(${f.x},${f.y})`}>
            {element.type === 'tps.rectangle' ? <rect width={f.width} height={f.height} fill={safeColor(element.properties.fill, '#eef2f6')} stroke={safeColor(element.properties.stroke, '#2463a6')} strokeWidth={300} /> : element.type === 'tps.qr' && element.qr ?
              <svg data-testid="preview-qr" role="img" aria-label="QR 코드" width={f.width} height={f.height} viewBox={`0 0 ${size} ${size}`} preserveAspectRatio="xMidYMid meet"><rect width={size} height={size} fill="#fff" /><path d={qrPath(element.qr)} fill="#000" shapeRendering="crispEdges" /></svg> :
              <svg width={f.width} height={f.height} overflow="hidden"><text xmlSpace="preserve" style={{ whiteSpace: 'pre' }} x={0} y={font} fill={safeColor(element.properties.color, '#17324d')} fontSize={font}>{String(element.properties.text ?? '').split('\n').map((line, index) => <tspan key={index} x={0} dy={index === 0 ? 0 : font * 1.3}>{line || ' '}</tspan>)}</text></svg>}
          </g>;
        })}
      </g>
    </svg>
    <p className="canvas-help">저장한 양식과 선택한 데이터로 만든 결과입니다. <span>페이지 반복·PDF·프린터 출력은 이번 미리보기에 포함하지 않습니다.</span></p>
    {preview.diagnostics.length > 0 && <ul className="preview-diagnostics">{preview.diagnostics.map((d, i) => <li key={i}>{d.message}</li>)}</ul>}
  </div>;
}
