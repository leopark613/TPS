import type { Document, Element, Json, Properties, Section, Frame, Diagnostic } from './types';
import type { EditorState } from './editor-state';

export type QueryRef = { id: string; classId: string; version: string };
export type Dataset = { id: string; queryRef: QueryRef; ordinal: number; fields: { id: string; path: string[]; type: string }[] };
export type FieldBinding = { property: string; source: { kind: 'field'; datasetId: string; fieldId: string }; evaluation: { scope: 'row'; event: 'beforePrint' }; nullPolicy: 'error' | 'empty' };
export type ReportRow = { reportId: string; reportName: string; reportClassId: string | null };
export type ReportList = { dataset: Dataset; rows: ReportRow[]; truncated: boolean; label: string };
export type QrMatrix = { matrix: string[]; moduleCount: number; quietZoneModules: 4; version: number; errorCorrection: 'M'; encoding: 'UTF-8' };
export type PreviewElement = { id: string; type: string; frame: Frame; properties: Properties; qr?: QrMatrix };
export type ReportPreview = { templateId: string; revisionId: string; bodyHash: string; queryRef: QueryRef | null; executedAt: string; reportId: string | null; resultHash: string; units: 'um'; page: Section['page']; elements: PreviewElement[]; diagnostics: Diagnostic[] };

export const REPORT_DATASET_ID = 'rps-report-list';
export const REPORT_FIELDS = [{ id: 'reportId', label: '보고서 ID' }, { id: 'reportName', label: '보고서명' }, { id: 'reportClassId', label: '분류 ID' }] as const;
export const REPORT_DATASET: Dataset = { id: REPORT_DATASET_ID, queryRef: { id: 'ReportList', classId: 'TPS_LOCAL_SAMPLE', version: '1' }, ordinal: 0, fields: REPORT_FIELDS.map(f => ({ id: f.id, path: [f.id], type: 'string' })) };
export const QR_DEFAULTS: Properties = { symbology: 'QR', encoding: 'UTF-8', errorCorrection: 'M', quietZoneModules: 4, versionMode: 'auto', defaultsContract: 'tps.qr:1', payload: 'TPS' };
const object = (value: Json | undefined): value is { [key: string]: Json } => value !== null && typeof value === 'object' && !Array.isArray(value);
const keysAre = (value: { [key: string]: Json }, keys: string[]) => Object.keys(value).length === keys.length && keys.every(k => k in value);

export function isReportDataset(value: Json): boolean {
  if (!object(value) || !keysAre(value, ['id', 'queryRef', 'ordinal', 'fields']) || value.id !== REPORT_DATASET_ID || value.ordinal !== 0 || !object(value.queryRef) || !Array.isArray(value.fields)) return false;
  const query = value.queryRef;
  return keysAre(query, ['id', 'classId', 'version']) && query.id === 'ReportList' && query.classId === 'TPS_LOCAL_SAMPLE' && query.version === '1' && value.fields.length === 3 &&
    REPORT_FIELDS.every(f => value.fields instanceof Array && value.fields.filter(field => object(field) && keysAre(field, ['id', 'path', 'type']) && field.id === f.id && field.type === 'string' && Array.isArray(field.path) && field.path.length === 1 && field.path[0] === f.id).length === 1);
}
export function hasReportDataset(doc: Document): boolean { return doc.datasets.length === 1 && isReportDataset(doc.datasets[0]); }
export function bindingProperty(element: Element): 'text' | 'payload' | null { return element.type === 'tps.text' ? 'text' : element.type === 'tps.qr' ? 'payload' : null; }
export function isFieldBinding(value: Json, property: string): value is FieldBinding {
  if (!object(value) || !keysAre(value, ['property', 'source', 'evaluation', 'nullPolicy']) || value.property !== property || !object(value.source) || !object(value.evaluation)) return false;
  const source = value.source;
  return keysAre(source, ['kind', 'datasetId', 'fieldId']) && source.kind === 'field' && source.datasetId === REPORT_DATASET_ID && REPORT_FIELDS.some(f => f.id === source.fieldId) &&
    keysAre(value.evaluation, ['scope', 'event']) && value.evaluation.scope === 'row' && value.evaluation.event === 'beforePrint' && ['error', 'empty'].includes(String(value.nullPolicy));
}
export function fieldBinding(element: Element): FieldBinding | null {
  const prop = bindingProperty(element);
  return prop && element.bindings.length === 1 && isFieldBinding(element.bindings[0], prop) ? element.bindings[0] : null;
}
export function supportedBindings(element: Element, connected: boolean): boolean { return element.bindings.length === 0 || (connected && fieldBinding(element) !== null); }
export function supportedQr(element: Element): boolean {
  return Object.entries(QR_DEFAULTS).filter(([key]) => key !== 'payload').every(([key, value]) => element.properties[key] === value) &&
    (typeof element.properties.payload === 'string' || element.properties.payload === undefined);
}
export function setFieldBinding(element: Element, fieldId: string, nullPolicy: 'error' | 'empty' = 'error'): Element {
  const property = bindingProperty(element);
  if (!property || (fieldId && !REPORT_FIELDS.some(f => f.id === fieldId))) return element;
  const clone = structuredClone(element);
  clone.bindings = clone.bindings.filter(b => !object(b) || b.property !== property);
  if (fieldId) clone.bindings.push({ property, source: { kind: 'field', datasetId: REPORT_DATASET_ID, fieldId }, evaluation: { scope: 'row', event: 'beforePrint' }, nullPolicy });
  else if (typeof clone.properties[property] !== 'string') clone.properties[property] = '';
  return clone;
}
export function connectReportData(doc: Document, dataset: Dataset): Document {
  if (!isReportDataset(dataset)) throw new Error('지원하지 않는 데이터 연결입니다.');
  const clone = structuredClone(doc);
  clone.datasets = [structuredClone(dataset)]; clone.sections[0].bands[0].datasetId = REPORT_DATASET_ID;
  return clone;
}
export function disconnectReportData(doc: Document): Document {
  const clone = structuredClone(doc); clone.datasets = []; delete clone.sections[0].bands[0].datasetId;
  clone.sections[0].bands[0].elements = clone.sections[0].bands[0].elements.map(e => setFieldBinding(e, ''));
  return clone;
}
export function elementLabel(element: Element): string {
  const binding = fieldBinding(element);
  if (binding) return `[${REPORT_FIELDS.find(f => f.id === binding.source.fieldId)?.label ?? binding.source.fieldId}]`;
  return element.type === 'tps.qr' ? 'QR 자리' : typeof element.properties.text === 'string' ? element.properties.text : '';
}
export function previewContext(state: EditorState, loading: boolean): string { return JSON.stringify([state.draftId, state.module, state.generation, state.savedGeneration, state.view?.templateId, state.view?.revisionId, state.saveState, Boolean(state.pending), loading]); }
export function canPreview(state: EditorState, loading: boolean, reportId: string): boolean {
  return state.module === 'RPS' && !loading && state.view !== null && state.generation === state.savedGeneration && state.saveState === 'idle' && !state.pending && (!state.document.datasets.length || Boolean(reportId));
}
/** Reject malformed QR response shapes rather than displaying a partial/misleading code. */
export function qrPath(qr: QrMatrix): string {
  if (!Number.isInteger(qr.moduleCount) || qr.moduleCount < 21 || qr.moduleCount > 177 || (qr.moduleCount - 21) % 4 !== 0 || qr.quietZoneModules !== 4 || qr.matrix.length !== qr.moduleCount || qr.matrix.some(row => row.length !== qr.moduleCount || !/^[01]+$/.test(row))) throw new Error('QR 결과를 표시할 수 없습니다. 다시 미리보세요.');
  return qr.matrix.flatMap((row, y) => Array.from(row, (bit, x) => bit === '1' ? `M${x + 4} ${y + 4}h1v1h-1z` : '')).join('');
}
