// Draft design checks only. Not a TPS validator, renderer, persistence or concurrency test.
const fs=require('fs'); const path=require('path'); const assert=require('assert');
const Ajv2020=require(process.argv[2]); const base=process.argv[3]||__dirname;
const schema=JSON.parse(fs.readFileSync(path.join(base,'schema.json'),'utf8'));
const ajv=new Ajv2020({strict:true,allErrors:true}); const validate=ajv.compile(schema);
const input=n=>JSON.parse(fs.readFileSync(path.join(base,'examples',n+'.json'),'utf8'));
function semantic(doc){
  const errors=[];const ids=new Set(),datasets=new Map();
  const unique=(id)=>{if(ids.has(id))errors.push('DUPLICATE_ID');ids.add(id);};
  for(const ds of doc.datasets){if(datasets.has(ds.id))errors.push('DUPLICATE_DATASET');const fieldIds=new Set();for(const f of ds.fields){if(fieldIds.has(f.id))errors.push('DUPLICATE_FIELD');fieldIds.add(f.id);}datasets.set(ds.id,fieldIds);}
  function element(e){unique(e.id);for(const b of e.bindings){const s=b.source;if(s.kind==='field'&&!datasets.get(s.datasetId)?.has(s.fieldId))errors.push('MISSING_FIELD');}for(const c of e.children)element(c);}
  function band(b){unique(b.id);if(b.datasetId&&!datasets.has(b.datasetId))errors.push('MISSING_DATASET');for(const e of b.elements)element(e);for(const c of b.childBands)band(c);}
  for(const s of doc.sections){unique(s.id);const p=s.page;if(p.margins.left+p.margins.right>=p.width||p.margins.top+p.margins.bottom>=p.height)errors.push('INVALID_PAGE_AREA');s.bands.forEach(band);}
  for(const o of doc.provenance.elementOrigins){if(!ids.has(o.elementId))errors.push('MISSING_ORIGIN_ELEMENT');}
  return errors;
}
const checks=[];
for(const n of ['report','excel']){const d=input(n);assert(validate(d),JSON.stringify(validate.errors));assert.deepStrictEqual(semantic(d),[]);assert.deepStrictEqual(JSON.parse(JSON.stringify(d)),d);checks.push(n+': structure, selected references, JSON round-trip');}
function bad(name,edit,kind,expected){const d=input('report');edit(d);if(kind==='schema')assert(!validate(d),name);else assert(semantic(d).includes(expected),name);checks.push(name);}
bad('reject unsupported schema version',d=>d.schemaVersion='99.0','schema');
bad('reject unexpected root field',d=>d.printJob={number:'1'},'schema');
bad('reject fractional physical coordinate',d=>d.sections[0].bands[0].elements[0].frame.x=0.25,'schema');
bad('reject zero width',d=>d.sections[0].page.width=0,'schema');
bad('detect duplicate element id',d=>d.sections[0].bands[0].elements[1].id='text-serial','semantic','DUPLICATE_ID');
bad('detect dangling field',d=>d.sections[0].bands[0].elements[0].bindings[0].source.fieldId='missing','semantic','MISSING_FIELD');
bad('detect missing band dataset',d=>d.sections[0].bands[0].datasetId='missing','semantic','MISSING_DATASET');
bad('detect unusable page area',d=>d.sections[0].page.margins.left=210000,'semantic','INVALID_PAGE_AREA');
bad('detect missing import target',d=>d.provenance.elementOrigins=[{elementId:'missing',locator:'Sheet1!A1'}],'semantic','MISSING_ORIGIN_ELEMENT');
const rows=input('data').rows;assert.strictEqual(rows[0].serial,'000012');assert.strictEqual(rows[1].serial,'한글-000013');checks.push('synthetic strings preserved');
console.log(JSON.stringify({time:new Date().toISOString(),scope:'Design artifact checks only',schemaDraft:'2020-12',checkCount:checks.length,checks,result:'PASS',notTested:['Full type-specific properties','Assets/font availability','Spring Boot/React','DB/CAS/idempotency','Rendering/QR decoding/printing','Performance/deployment']},null,2));
