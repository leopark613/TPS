import type { Document, Element, Frame } from './types';
import { hasReportDataset, QR_DEFAULTS, REPORT_DATASET_ID, supportedBindings, supportedQr } from './report';

export function newDocument(): Document {
  return {
    format: 'tps.document', schemaVersion: '0.1.0-draft', units: 'um', title: '새 양식', locale: 'ko-KR', timeZone: 'Asia/Seoul',
    requiredFeatures: [{ id: 'tps.text', version: '1' }],
    sections: [{ id: 'section1', page: { width: 210000, height: 297000, margins: { top: 10000, right: 10000, bottom: 10000, left: 10000 } },
      bands: [{ id: 'detail1', kind: 'detail', height: 277000, properties: {}, childBands: [], elements: [{ id: 'title1', type: 'tps.text', typeVersion: '1', frame: { x: 10000, y: 10000, width: 130000, height: 16000 }, properties: { text: '보고서 제목', fontSizePt: '20', color: '#17324d' }, bindings: [], children: [] }] }] }],
    datasets: [], assets: [], provenance: { origin: 'native', elementOrigins: [] }, extensions: {},
  };
}

export function elements(doc: Document): Element[] { return doc.sections[0]?.bands[0]?.elements ?? []; }

export function replaceElement(doc: Document, id: string, update: (element: Element) => Element): Document {
  // Deep clone preserves every unedited extension/property, binding and child.
  const result = structuredClone(doc);
  const band = result.sections[0]?.bands[0];
  if (band) band.elements = band.elements.map(el => el.id === id ? update(el) : el);
  return result;
}

export function addElement(doc: Document, type: 'tps.text' | 'tps.rectangle' | 'tps.qr', id: string): Document {
  const result = structuredClone(doc);
  const offset = Math.min(elements(doc).length, 10) * 3000;
  result.sections[0].bands[0].elements.push({ id, type, typeVersion: '1', frame: { x: 15000 + offset, y: 35000 + offset, width: type === 'tps.qr' ? 30000 : 80000, height: type === 'tps.text' ? 12000 : 30000 },
    properties: type === 'tps.text' ? { text: '새 텍스트', fontSizePt: '12', color: '#17324d' } : type === 'tps.qr' ? { ...QR_DEFAULTS } : { fill: '#eef2f6', stroke: '#2463a6' }, bindings: [], children: [] });
  if (!result.requiredFeatures.some(f => f.id === type && f.version === '1')) result.requiredFeatures.push({ id: type, version: '1' });
  return result;
}

export function removeElement(doc: Document, id: string): Document {
  const result = structuredClone(doc);
  result.sections[0].bands[0].elements = elements(result).filter(el => el.id !== id);
  result.provenance.elementOrigins = result.provenance.elementOrigins.filter(o => o.elementId !== id);
  return result;
}

export function canEditDocument(doc: Document): boolean {
  // Capability check for schema-valid documents. Keep in sync with DocumentValidator's basic subset.
  // Copying a saved document must not turn unsupported content into an editable native draft.
  if (doc.format !== 'tps.document' || doc.schemaVersion !== '0.1.0-draft' || doc.units !== 'um' || doc.sections.length !== 1) return false;
  const section = doc.sections[0];
  if (section.page.width > 2000000 || section.page.height > 2000000 || section.bands.length !== 1) return false;
  const band = section.bands[0];
  const connected = hasReportDataset(doc);
  return band.kind === 'detail' && band.childBands.length === 0 && (!('datasetId' in band) || (connected && band.datasetId === REPORT_DATASET_ID)) && (doc.datasets.length === 0 || connected) && doc.assets.length === 0 &&
    doc.requiredFeatures.every(f => ['tps.text', 'tps.rectangle', 'tps.qr'].includes(f.id) && f.version === '1') &&
    elements(doc).every(e => ['tps.text', 'tps.rectangle', 'tps.qr'].includes(e.type) && e.typeVersion === '1' && supportedBindings(e, connected) && (e.type !== 'tps.qr' || supportedQr(e)) && e.children.length === 0 && (e.frame.rotationMilliDegrees ?? 0) === 0);
}

export function moveFrame(frame: Frame, dx: number, dy: number): Frame {
  return { ...frame, x: Math.max(-9007199254740991, Math.min(9007199254740991, Math.round(frame.x + dx))), y: Math.max(-9007199254740991, Math.min(9007199254740991, Math.round(frame.y + dy))) };
}
export function safeColor(value: unknown, fallback: string): string { return typeof value === 'string' && /^#[0-9a-f]{6}$/i.test(value) ? value : fallback; }
export function safeFontSize(value: unknown): number { const n = Number(value); return Number.isFinite(n) && n > 0 ? Math.max(4, Math.min(n, 200)) : 12; }
export function mm(value: number): string { return (value / 1000).toLocaleString('en-US', { maximumFractionDigits: 3, useGrouping: false }); }
