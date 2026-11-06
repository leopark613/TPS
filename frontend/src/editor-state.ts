import type { Document, Module, TemplateView } from './types';
import { canEditDocument, newDocument } from './document';

export interface SaveSnapshot { key: string; operation: 'CREATE' | 'SAVE'; scopeId: string; ownerModule: Module; document: Document; etag: string | null; generation: number; draftId: string }
export interface EditorState { draftId: string; module: Module; document: Document; view: TemplateView | null; generation: number; savedGeneration: number; pending: SaveSnapshot | null; saveState: 'idle' | 'saving' | 'unknown'; conflict: boolean; message: string }
export function freshEditor(module: Module, id: string): EditorState { return { draftId: id, module, document: newDocument(), view: null, generation: 0, savedGeneration: -1, pending: null, saveState: 'idle', conflict: false, message: '' }; }
export function isDirty(state: EditorState): boolean { return state.generation !== state.savedGeneration; }
export function canCopyEditor(state: EditorState, loading: boolean): boolean {
  return !loading && state.saveState === 'idle' && !state.pending && canEditDocument(state.document);
}
export function captureSave(state: EditorState, key: string): SaveSnapshot {
  if (state.pending) return state.pending;
  return { key, operation: state.view ? 'SAVE' : 'CREATE', scopeId: state.view?.templateId ?? state.draftId, ownerModule: state.module, document: structuredClone(state.document), etag: state.view?.headEtag ?? null, generation: state.generation, draftId: state.draftId };
}
export type EditorAction = { type: 'new'; module: Module; id: string } | { type: 'load'; view: TemplateView; draftId: string } | { type: 'edit'; document: Document } |
  { type: 'saving'; snapshot: SaveSnapshot } | { type: 'saved'; snapshot: SaveSnapshot; view: TemplateView } | { type: 'failed'; snapshot: SaveSnapshot; unknown: boolean; conflict?: boolean; message: string } |
  { type: 'copy'; id: string } | { type: 'message'; message: string };
export function editorReducer(state: EditorState, action: EditorAction): EditorState {
  switch (action.type) {
    case 'new': return freshEditor(action.module, action.id);
    case 'load': return { ...freshEditor(action.view.ownerModule, action.draftId), document: structuredClone(action.view.document), view: action.view, savedGeneration: 0 };
    case 'edit': return { ...state, document: action.document, generation: state.generation + 1, message: '' };
    case 'saving': return action.snapshot.draftId === state.draftId ? { ...state, pending: action.snapshot, saveState: 'saving', message: '' } : state;
    case 'saved': {
      if (state.draftId !== action.snapshot.draftId || state.pending?.key !== action.snapshot.key) return state;
      const editedMeanwhile = state.generation !== action.snapshot.generation;
      return { ...state, view: action.view, document: editedMeanwhile ? state.document : structuredClone(action.view.document), savedGeneration: action.snapshot.generation, pending: null, saveState: 'idle', conflict: false, message: editedMeanwhile ? '저장했습니다. 이후 편집한 내용은 아직 저장하지 않았습니다.' : '저장했습니다. 업무 출력에는 아직 적용하지 않았습니다.' };
    }
    case 'failed':
      if (state.draftId !== action.snapshot.draftId || state.pending?.key !== action.snapshot.key) return state;
      return { ...state, pending: action.unknown ? action.snapshot : null, saveState: action.unknown ? 'unknown' : 'idle', conflict: action.conflict ?? false, message: action.message };
    case 'copy':
      if (!canEditDocument(state.document)) return state;
      return { ...state, draftId: action.id, view: null, document: { ...structuredClone(state.document), title: `${state.document.title.slice(0, 195)} 복사본` }, generation: 0, savedGeneration: -1, pending: null, saveState: 'idle', conflict: false, message: '새 양식으로 복사했습니다. 이름을 확인하고 저장하세요.' };
    case 'message': return { ...state, message: action.message };
  }
}

/** A response can update its screen only while its exact request generation is current. */
export class LatestRequest {
  private generation = 0;
  begin(): number { return ++this.generation; }
  isCurrent(ticket: number): boolean { return ticket === this.generation; }
  cancel(): void { ++this.generation; }
}
