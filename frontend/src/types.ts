export type Module = 'RPS' | 'LPS';
export type Json = null | boolean | number | string | Json[] | { [key: string]: Json };
export type Properties = { [key: string]: Json };
export interface Frame { x: number; y: number; width: number; height: number; rotationMilliDegrees?: number }
export interface Element { id: string; type: string; typeVersion: string; frame: Frame; properties: Properties; bindings: Json[]; children: Element[] }
export interface Band { id: string; kind: string; height: number; datasetId?: string; properties: Properties; elements: Element[]; childBands: Band[] }
export interface Section { id: string; page: { width: number; height: number; margins: { top: number; right: number; bottom: number; left: number } }; bands: Band[] }
export interface Document {
  format: string; schemaVersion: string; units: string; title: string; locale: string; timeZone: string;
  requiredFeatures: { id: string; version: string }[]; sections: Section[]; datasets: Json[]; assets: Json[];
  provenance: { origin: string; elementOrigins: { elementId: string; locator: string }[]; [key: string]: Json };
  extensions: Properties;
}
export interface Diagnostic { severity: 'WARNING' | 'ERROR'; code: string; path: string; message: string }
export interface TemplateSummary { templateId: string; name: string; ownerModule: Module; headRevisionId: string; headToken: string; revisionNo: string; activeRevisionId: string | null; createdAt: string; lastSavedAt: string }
export interface TemplateView extends TemplateSummary { revisionId: string; sequence: string; bodyHash: string; headEtag: string | null; document: Document; diagnostics: Diagnostic[]; editable: boolean }
export interface RevisionSummary { revisionId: string; sequence: string; bodyHash: string; createdAt: string; createdBy: string }
export interface Session { actorId: string; siteId: string; enabledModules: Module[] }
export interface TemplatePage { items: TemplateSummary[]; page: number; size: number; total: number }
