import { describe, expect, it } from 'vitest';
import { canCopyEditor, captureSave, editorReducer, freshEditor, isDirty, LatestRequest } from './editor-state';
import { addElement, canEditDocument, newDocument, removeElement, replaceElement } from './document';
import type { TemplateView } from './types';

const view = (document = newDocument()): TemplateView => ({ templateId: 'template-1', name: document.title, ownerModule: 'RPS', headRevisionId: 'revision-1', headToken: '1', revisionNo: '1', activeRevisionId: null, createdAt: '2026-11-06T00:00:00Z', lastSavedAt: '2026-11-06T00:00:00Z', revisionId: 'revision-1', sequence: '1', bodyHash: 'ab'.repeat(32), headEtag: '"h:template-1:revision-1:1"', document, diagnostics: [], editable: true });

describe('save state safety', () => {
  it('keeps edits made after save started and advances only the saved generation', () => {
    let state = freshEditor('RPS', 'draft-1');
    state = editorReducer(state, { type: 'edit', document: { ...state.document, title: '저장할 제목' } });
    const snapshot = captureSave(state, 'request-1');
    state = editorReducer(state, { type: 'saving', snapshot });
    state = editorReducer(state, { type: 'edit', document: { ...state.document, title: '저장 중 추가 편집' } });
    state = editorReducer(state, { type: 'saved', snapshot, view: view(snapshot.document) });
    expect(state.document.title).toBe('저장 중 추가 편집'); expect(isDirty(state)).toBe(true);
    expect(state.view?.templateId).toBe('template-1'); expect(state.pending).toBeNull();
    expect(captureSave(state, 'request-2').etag).toBe('"h:template-1:revision-1:1"');
  });
  it('retries the same key and exact original snapshot after unknown outcome even if editor changes', () => {
    let state = freshEditor('RPS', 'draft-1'); const snapshot = captureSave(state, 'request-1');
    state = editorReducer(state, { type: 'saving', snapshot });
    state = editorReducer(state, { type: 'failed', snapshot, unknown: true, message: 'network' });
    state = editorReducer(state, { type: 'edit', document: { ...state.document, title: '다른 내용' } });
    const retry = captureSave(state, 'must-not-use');
    expect(retry).toBe(snapshot); expect(retry.key).toBe('request-1'); expect(retry.document.title).toBe('새 양식');
    expect(state.document.title).toBe('다른 내용');
  });
  it('stale-save response cannot replace a newly opened document', () => {
    let state = freshEditor('RPS', 'draft-1'); const snapshot = captureSave(state, 'request-1');
    state = editorReducer(state, { type: 'saving', snapshot });
    state = editorReducer(state, { type: 'new', module: 'LPS', id: 'draft-2' });
    const unchanged = editorReducer(state, { type: 'saved', snapshot, view: view() });
    expect(unchanged).toBe(state); expect(unchanged.module).toBe('LPS');
  });
  it('conflict and validation errors preserve edited content', () => {
    let state = editorReducer(freshEditor('RPS', 'draft-1'), { type: 'load', view: view(), draftId: 'draft-2' });
    state = editorReducer(state, { type: 'edit', document: { ...state.document, title: '내 편집' } });
    const snapshot = captureSave(state, 'request-2'); state = editorReducer(state, { type: 'saving', snapshot });
    state = editorReducer(state, { type: 'failed', snapshot, unknown: false, conflict: true, message: 'conflict' });
    expect(state.document.title).toBe('내 편집'); expect(state.conflict).toBe(true); expect(isDirty(state)).toBe(true);
    state = editorReducer(state, { type: 'copy', id: 'copy-1' });
    expect(state.view).toBeNull(); expect(captureSave(state, 'request-3').operation).toBe('CREATE'); expect(state.document.title).toContain('내 편집');
  });
  it('successful unedited save is clean, while unsaved drafts never imply a database insert', () => {
    let state = freshEditor('RPS', 'draft-1'); expect(state.view).toBeNull(); expect(isDirty(state)).toBe(true);
    const snapshot = captureSave(state, 'request-1'); state = editorReducer(state, { type: 'saving', snapshot });
    state = editorReducer(state, { type: 'saved', snapshot, view: view() });
    expect(isDirty(state)).toBe(false); expect(state.view?.activeRevisionId).toBeNull();
  });
  it('second list/load response wins regardless of arrival order', () => {
    const gate = new LatestRequest(); const old = gate.begin(); const current = gate.begin();
    expect(gate.isCurrent(current)).toBe(true); expect(gate.isCurrent(old)).toBe(false);
    gate.cancel(); expect(gate.isCurrent(current)).toBe(false);
  });
  it('blocks conflict-copy while the latest revision is loading, then permits it after load settles', () => {
    let state = editorReducer(freshEditor('RPS', 'draft'), { type: 'load', view: view(), draftId: 'loaded' });
    state = editorReducer(state, { type: 'edit', document: { ...state.document, title: 'conflicting edit' } });
    const snapshot = captureSave(state, 'request'); state = editorReducer(state, { type: 'saving', snapshot });
    state = editorReducer(state, { type: 'failed', snapshot, unknown: false, conflict: true, message: 'stale' });
    expect(canCopyEditor(state, false)).toBe(true);
    expect(canCopyEditor(state, true)).toBe(false);
    state = editorReducer(state, { type: 'load', view: view(), draftId: 'latest-loaded' });
    expect(canCopyEditor(state, false)).toBe(true);
    expect(state.document.title).toBe('새 양식');
  });
});

describe('document preservation and feature limits', () => {
  it('preserves custom properties/extensions and unedited provenance when changing text', () => {
    const doc = newDocument(); doc.extensions['vendor'] = { nested: ['keep', 7] }; doc.sections[0].bands[0].elements[0].properties['custom'] = { order: [3, 2, 1] };
    const changed = replaceElement(doc, 'title1', e => ({ ...e, properties: { ...e.properties, text: '바뀐 문구' } }));
    expect(changed.extensions).toEqual(doc.extensions); expect(changed.sections[0].bands[0].elements[0].properties.custom).toEqual({ order: [3, 2, 1] });
    expect(doc.sections[0].bands[0].elements[0].properties.text).toBe('보고서 제목');
  });
  it('makes unknown features, nested bands or data-bound documents read-only', () => {
    expect(canEditDocument(newDocument())).toBe(true);
    const doc = newDocument(); doc.requiredFeatures.push({ id: 'vendor.chart', version: '2' }); expect(canEditDocument(doc)).toBe(false);
    const bound = newDocument(); bound.sections[0].bands[0].elements[0].bindings.push({ test: true }); expect(canEditDocument(bound)).toBe(false);
    const nested = newDocument(); nested.sections[0].bands.push(structuredClone(nested.sections[0].bands[0])); expect(canEditDocument(nested)).toBe(false);
  });
  it('keeps frame units integral and removes provenance for deleted elements only', () => {
    let doc = addElement(newDocument(), 'tps.rectangle', 'rect1');
    doc.provenance.elementOrigins = [{ elementId: 'rect1', locator: 'synthetic' }, { elementId: 'title1', locator: 'title' }];
    expect(doc.requiredFeatures.some(f => f.id === 'tps.rectangle')).toBe(true);
    doc = removeElement(doc, 'rect1'); expect(doc.provenance.elementOrigins).toEqual([{ elementId: 'title1', locator: 'title' }]);
    expect(doc.sections[0].bands[0].elements).toHaveLength(1);
  });
  it('retains rotated documents read-only and cannot bypass that limit by copying', () => {
    const doc = newDocument(); doc.sections[0].bands[0].elements[0].frame.rotationMilliDegrees = 90000;
    doc.sections[0].bands[0].elements[0].properties['vendorRotationNote'] = { keep: true };
    expect(canEditDocument(doc)).toBe(false);
    const stored = { ...view(doc), editable: false };
    const state = editorReducer(freshEditor('RPS', 'original'), { type: 'load', view: stored, draftId: 'loaded' });
    const copied = editorReducer(state, { type: 'copy', id: 'attempted-copy' });
    expect(copied).toBe(state);
    expect(copied.view).toBe(stored);
    expect(copied.document).toEqual(doc);
    expect(copied.document.sections[0].bands[0].elements[0].frame.rotationMilliDegrees).toBe(90000);
  });
  it('accepts explicit zero rotation and copies supported historical revisions as new drafts', () => {
    const doc = newDocument(); doc.sections[0].bands[0].elements[0].frame.rotationMilliDegrees = 0;
    expect(canEditDocument(doc)).toBe(true);
    const state = editorReducer(freshEditor('RPS', 'original'), { type: 'load', view: { ...view(doc), editable: false, headEtag: null }, draftId: 'historical' });
    const copied = editorReducer(state, { type: 'copy', id: 'copy-supported' });
    expect(copied.view).toBeNull(); expect(copied.draftId).toBe('copy-supported');
    expect(copied.document.sections).toEqual(doc.sections);
    expect(canEditDocument(copied.document)).toBe(true);
  });
  it.each(['width', 'height'] as const)('matches the backend 2m %s boundary without changing oversized content', dimension => {
    const doc = newDocument(); doc.sections[0].page[dimension] = 2000000;
    expect(canEditDocument(doc)).toBe(true);
    doc.sections[0].page[dimension] = 2000001;
    expect(canEditDocument(doc)).toBe(false);
    const state = editorReducer(freshEditor('RPS', 'original'), { type: 'load', view: { ...view(doc), editable: false }, draftId: 'oversized' });
    expect(editorReducer(state, { type: 'copy', id: 'attempted-copy' })).toBe(state);
    expect(state.document.sections[0].page[dimension]).toBe(2000001);
  });
  it('excludes detail bands carrying a dataset reference from the basic editor', () => {
    const doc = newDocument(); doc.sections[0].bands[0].datasetId = 'businessData';
    expect(canEditDocument(doc)).toBe(false);
    expect(doc.sections[0].bands[0].datasetId).toBe('businessData');
  });
});
