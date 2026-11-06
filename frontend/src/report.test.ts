import { describe, expect, it } from 'vitest';
import { addElement, canEditDocument, elements, newDocument } from './document';
import { captureSave, editorReducer, freshEditor } from './editor-state';
import { canPreview, connectReportData, disconnectReportData, elementLabel, fieldBinding, hasReportDataset, isReportDataset, previewContext, qrPath, REPORT_DATASET, setFieldBinding } from './report';
import type { FieldBinding, QrMatrix } from './report';
import type { TemplateView } from './types';

const connected = () => connectReportData(addElement(newDocument(), 'tps.qr', 'qr1'), REPORT_DATASET);
const withBinding = () => { const doc = connected(); doc.sections[0].bands[0].elements[1] = setFieldBinding(elements(doc)[1], 'reportName'); return doc; };
const saved = () => {
  const doc = withBinding();
  const view: TemplateView = { templateId: 't', name: 'x', ownerModule: 'RPS', revisionId: 'r', headRevisionId: 'r', headToken: '1', revisionNo: '1', sequence: '1', bodyHash: 'ab', headEtag: 'h', activeRevisionId: null, createdAt: '', lastSavedAt: '', document: doc, diagnostics: [], editable: true };
  return editorReducer(freshEditor('RPS', 'new'), { type: 'load', view, draftId: 'd' });
};

describe('report document contract', () => {
  it('connects metadata without embedding any query rows and preserves unknown metadata', () => {
    const doc = newDocument(); doc.extensions.metadata = { keep: true };
    const result = connectReportData(doc, REPORT_DATASET);
    expect(hasReportDataset(result)).toBe(true); expect(canEditDocument(result)).toBe(true);
    expect(result.extensions).toEqual(doc.extensions); expect(doc.datasets).toHaveLength(0);
    expect(JSON.stringify(result)).not.toContain('rows');
  });
  it('treats field order as irrelevant but rejects duplicate or changed schemas', () => {
    const data = structuredClone(REPORT_DATASET); data.fields.reverse(); expect(isReportDataset(data)).toBe(true);
    data.fields[0] = structuredClone(data.fields[1]); expect(isReportDataset(data)).toBe(false);
  });
  it.each(['id', 'classId', 'version'] as const)('does not accept a different query %s', key => {
    const data = structuredClone(REPORT_DATASET); data.queryRef[key] += '2'; expect(isReportDataset(data)).toBe(false);
    expect(() => connectReportData(newDocument(), data)).toThrow();
  });
  it('rejects unknown field, path, ordinal and additional dataset settings', () => {
    const data = structuredClone(REPORT_DATASET); data.fields[0].path = ['reportId', 'nested']; expect(isReportDataset(data)).toBe(false);
    expect(isReportDataset({ ...REPORT_DATASET, ordinal: 1 })).toBe(false);
    expect(isReportDataset({ ...REPORT_DATASET, rows: [] })).toBe(false);
  });
  it('binds text and QR while keeping direct input intact and displays only field placeholders', () => {
    const doc = withBinding(); const qr = elements(doc)[1];
    expect(canEditDocument(doc)).toBe(true); expect(fieldBinding(qr)?.source.fieldId).toBe('reportName');
    expect(elementLabel(qr)).toBe('[보고서명]'); expect(qr.properties.payload).toBe('TPS');
    const direct = setFieldBinding(qr, ''); expect(direct.bindings).toEqual([]); expect(direct.properties.payload).toBe('TPS');
  });
  it('disconnect removes bindings and dataset references without erasing direct values', () => {
    const result = disconnectReportData(withBinding());
    expect(result.datasets).toEqual([]); expect(result.sections[0].bands[0].datasetId).toBeUndefined();
    expect(elements(result)[1].bindings).toEqual([]); expect(elements(result)[1].properties.payload).toBe('TPS'); expect(canEditDocument(result)).toBe(true);
  });
  it('supports explicit empty null policy while keeping unsupported policy read-only', () => {
    const doc = withBinding(); const qr = elements(doc)[1];
    doc.sections[0].bands[0].elements[1] = setFieldBinding(qr, 'reportClassId', 'empty');
    expect(canEditDocument(doc)).toBe(true);
    (elements(doc)[1].bindings[0] as unknown as { nullPolicy: string }).nullPolicy = 'literal'; expect(canEditDocument(doc)).toBe(false);
  });
  it('rejects duplicate binding targets, wrong fields, evaluation and rectangle bindings', () => {
    const doc = withBinding(); const qr = elements(doc)[1]; qr.bindings.push(structuredClone(qr.bindings[0])); expect(canEditDocument(doc)).toBe(false);
    qr.bindings.pop(); const b = qr.bindings[0] as FieldBinding; b.source.fieldId = 'unknown'; expect(canEditDocument(doc)).toBe(false);
    b.source.fieldId = 'reportName'; (b.evaluation as { scope: string }).scope = 'page'; expect(canEditDocument(doc)).toBe(false);
    b.evaluation.scope = 'row'; qr.type = 'tps.rectangle'; expect(canEditDocument(doc)).toBe(false);
  });
  it('keeps missing static payload editable for correction and accepts bound omission, but rejects changed behavior', () => {
    const staticQr = addElement(newDocument(), 'tps.qr', 'q');
    expect(canEditDocument(staticQr)).toBe(true); delete elements(staticQr)[1].properties.payload;
    expect(canEditDocument(staticQr)).toBe(true);
    const doc = withBinding(); const qr = elements(doc)[1]; delete qr.properties.payload; expect(canEditDocument(doc)).toBe(true);
    qr.properties.customNote = 'preserve'; expect(canEditDocument(doc)).toBe(true);
    qr.properties.errorCorrection = 'L'; expect(canEditDocument(doc)).toBe(false);
  });
  it('allows a dataset reference to be omitted but rejects unknown band references', () => {
    const doc = withBinding(); delete doc.sections[0].bands[0].datasetId; expect(canEditDocument(doc)).toBe(true);
    doc.sections[0].bands[0].datasetId = 'other'; expect(canEditDocument(doc)).toBe(false);
  });
});

describe('saved preview identity', () => {
  it('requires explicit saved version and row, never makes a fresh draft eligible', () => {
    expect(canPreview(freshEditor('RPS', 'new'), false, '')).toBe(false);
    const state = saved(); expect(canPreview(state, false, '')).toBe(false); expect(canPreview(state, false, 'row1')).toBe(true);
    expect(canPreview({ ...state, module: 'LPS' }, false, 'row1')).toBe(false);
    expect(canPreview(state, true, 'row1')).toBe(false);
  });
  it('permits a supported historical saved version but not any pending or dirty edit', () => {
    const state = saved(); state.view = { ...state.view!, editable: false, headEtag: null }; expect(canPreview(state, false, 'row')).toBe(true);
    const changed = editorReducer(state, { type: 'edit', document: { ...state.document, title: 'edited' } }); expect(canPreview(changed, false, 'row')).toBe(false);
    expect(canPreview({ ...state, pending: captureSave(state, 'p'), saveState: 'unknown' }, false, 'row')).toBe(false);
  });
  it('invalidates result identity on generation, view, load, save, and draft changes', () => {
    const state = saved(); const key = previewContext(state, false);
    for (const changed of [{ ...state, generation: 1 }, { ...state, draftId: 'another' }, { ...state, view: { ...state.view!, revisionId: 'r2' } }, { ...state, saveState: 'saving' as const }]) expect(previewContext(changed, false)).not.toBe(key);
    expect(previewContext(state, true)).not.toBe(key);
  });
});

describe('QR display contract', () => {
  const matrix = (): QrMatrix => ({ matrix: ['1' + '0'.repeat(20), ...Array(20).fill('0'.repeat(21))], moduleCount: 21, quietZoneModules: 4, version: 1, errorCorrection: 'M', encoding: 'UTF-8' });
  it('places modules four modules inward without inventing or decoding data', () => { expect(qrPath(matrix())).toBe('M4 4h1v1h-1z'); });
  it('rejects non-square, corrupted, or unbounded matrices', () => {
    const q = matrix(); q.matrix[0] = '1'; expect(() => qrPath(q)).toThrow();
    const bad = matrix(); bad.matrix[0] = 'x'.repeat(21); expect(() => qrPath(bad)).toThrow();
    expect(() => qrPath({ ...matrix(), moduleCount: 178 })).toThrow();
  });
});
