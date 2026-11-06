"""TPS-IMPL-002: real local SQL + packaged API; exact owned DOC cleanup, no RPS writes."""
from __future__ import annotations
import argparse
import base64
import copy
import datetime as dt
import getpass
import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
from run_integration import Api, OwnedServer, Checks, document, uid, body_hash, ObserverFailure, safe_observer_diagnostic


def native_document():
    doc=document('TPS_IT 일반 보고서 데이터·QR')
    del doc['sections'][0]['bands'][0]['elements'][0]['properties']['customPreserved']
    doc['requiredFeatures'].append({'id':'tps.qr','version':'1'})
    doc['sections'][0]['bands'][0]['elements'].append({
        'id':'qr-report','type':'tps.qr','typeVersion':'1',
        'frame':{'x':10000,'y':40000,'width':40000,'height':40000},
        'properties':{'symbology':'QR','encoding':'UTF-8','errorCorrection':'M','quietZoneModules':4,
            'versionMode':'auto','defaultsContract':'tps.qr:1','payload':'000012|한글 보고서\nTPS'},
        'bindings':[],'children':[]})
    return doc


def binding(dataset, field, prop, null_policy='error'):
    return {'property':prop,'source':{'kind':'field','datasetId':dataset['id'],'fieldId':field},
            'evaluation':{'scope':'row','event':'beforePrint'},'nullPolicy':null_policy}


def execute(args):
    output=Path(args.output).resolve(); output.mkdir(parents=True,exist_ok=True)
    env=dict(os.environ)
    env['TPS_DB_USER']=env.get('TPS_DB_USER') or input('Local TPS SQL user: ').strip()
    env['TPS_DB_PASSWORD']=env.get('TPS_DB_PASSWORD') or getpass.getpass('Local TPS SQL password (not saved): ')
    if env.get('TPS_DB_TRUST_SERVER_CERTIFICATE')!='true': raise ValueError('Explicit local test TLS exception required')
    site='TPS_IT_QR_'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%d%H%M%S')+'_'+secrets.token_hex(2)
    password=secrets.token_urlsafe(32)
    env.update(TPS_DB_HOST='10.121.13.159',TPS_DB_PORT='1433',TPS_DB_NAME='TPS',TPS_SITE_ID=site,
               TPS_DEV_PASSWORD=password,TPS_ENABLED_MODULES='RPS,LPS',SPRING_PROFILES_ACTIVE='local')
    helper_root=Path(__file__).resolve().parent
    checks=Checks(); api=Api(args.port,password)
    server=OwnedServer(args.java,Path(args.jar).resolve(),args.port,env)
    templates,drafts=set(),set(); plan=output/'owned-cleanup-plan.txt'
    report={'kind':'real-local-sql-general-report-data-qr','siteId':site,'checks':checks.items,
            'jarSha256':hashlib.sha256(Path(args.jar).read_bytes()).hexdigest(),
            'scope':'No DDL or print API is invoked by this test; physical output is not tested.','encrypted':True}
    before=None

    def run_helper(name, cp, argv=(), stdin=None):
        r=subprocess.run([args.java,'-cp',str(cp),str(helper_root/name),*argv],env=env,input=stdin,
              capture_output=True,text=True,encoding='utf-8',timeout=60,
              creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
        if r.returncode: raise ObserverFailure(safe_observer_diagnostic(r.stderr))
        return r.stdout

    def write_plan():
        plan.write_text(site+'\n'+''.join('T='+v+'\n' for v in sorted(templates))+''.join('C='+v+'\n' for v in sorted(drafts)),encoding='utf-8')

    def observe(action='snapshot'):
        return json.loads(run_helper('LocalDbObserver.java',args.jdbc,[action,site if action=='snapshot' else str(plan)]))

    def create(doc, owner='RPS'):
        draft=uid(); drafts.add(draft); write_plan()
        status,view,_=api.request('POST','/templates',{'clientDraftId':draft,'ownerModule':owner,'document':doc},headers={'Idempotency-Key':uid()})
        if status==201:
            templates.add(view['templateId'].lower()); write_plan()
        return status,view

    def save(view,doc):
        status,result,_=api.request('PUT','/templates/'+view['templateId']+'/head',{'document':doc},
            headers={'Idempotency-Key':uid(),'If-Match':view['headEtag']})
        return status,result

    def preview(view,report_id=None,extra=None):
        data={'templateId':view['templateId'],'revisionId':view['revisionId'],'reportId':report_id}
        data.update(extra or {})
        status,result,_=api.request('POST','/rps/Preview',data)
        return status,result

    def decode(qr,payload):
        result=run_helper('QrDecodeProbe.java',args.zxing,stdin='\n'.join(qr['matrix']))
        return result.strip()==hashlib.sha256(payload.encode('utf-8')).hexdigest()

    try:
        before=observe(); report['before']=before
        checks.check('dedicated_site_initially_empty',all(v['rows']==0 for v in before['site'].values()))
        source_site=base64.b64decode(run_helper('ReportReferenceProbe.java',args.jdbc).strip()).decode('utf-8')
        # Explicit per-process local mapping. Source identity stays only in memory.
        env['TPS_RPS_REFERENCE_SITE_ID']=source_site
        server.start(api)
        checks.check('report_list_requires_authentication',api.request('GET','/rps/ReportList',auth=False)[0]==401)
        status,catalog,_=api.request('GET','/rps/ReportList')
        checks.check('fixed_local_report_query_succeeds',status==200 and 0<len(catalog['rows'])<=100)
        dataset=catalog['dataset']; row=catalog['rows'][0]
        checks.check('exact_query_tuple_and_string_field_contract',dataset['queryRef']=={'id':'ReportList','classId':'TPS_LOCAL_SAMPLE','version':'1'} and dataset['ordinal']==0 and {f['id'] for f in dataset['fields']}=={'reportId','reportName','reportClassId'})
        checks.check('report_response_exposes_only_allowed_fields',all(set(r)=={'reportId','reportName','reportClassId'} for r in catalog['rows']))
        doc=native_document(); doc['datasets']=[copy.deepcopy(dataset)]
        band=doc['sections'][0]['bands'][0]; band['datasetId']=dataset['id']
        band['elements'][0]['bindings']=[binding(dataset,'reportName','text')]
        band['elements'][1]['bindings']=[binding(dataset,'reportId','payload')]
        status,first=create(doc)
        checks.check('bound_general_report_saved_editable_without_activation',status==201 and first['editable'] and first['activeRevisionId'] is None)
        status,reopened,_=api.request('GET','/templates/'+first['templateId']+'/head')
        checks.check('binding_definition_reopens_exactly',status==200 and reopened['document']==doc and reopened['bodyHash']==body_hash(doc))
        status,res=preview(first,row['reportId'])
        checks.check('saved_revision_data_preview_succeeds',status==200 and res['revisionId']==first['revisionId'] and res['bodyHash']==first['bodyHash'])
        checks.check('server_resolves_text_and_qr_same_selected_row',res['elements'][0]['properties']['text']==row['reportName'] and res['elements'][1]['properties']['payload']==row['reportId'])
        checks.check('qr_matrix_independently_decodes_selected_value',decode(res['elements'][1]['qr'],row['reportId']))
        checks.check('data_result_hash_matches_only_selected_fields',res['resultHash']==body_hash(row))
        checks.check('missing_selected_row_rejected',preview(first,'TPS_IT_NO_SUCH_REPORT')[0]==404)
        checks.check('dataset_requires_row_selection',preview(first)[0] in (400,422))
        checks.check('client_sql_or_url_not_accepted',preview(first,row['reportId'],{'sql':'SELECT 1','url':'https://invalid.example'})[0]==400)

        # Two report templates plus one LPS template only; subsequent cases reuse immutable versions.
        static=native_document(); status,static_view=create(static)
        checks.check('static_qr_general_report_saved',status==201)
        status,res=preview(static_view)
        checks.check('unicode_leading_zero_newline_roundtrip',status==200 and decode(res['elements'][1]['qr'],'000012|한글 보고서\nTPS'))
        status,lps=create(native_document(),'LPS')
        checks.check('lps_common_static_qr_saved',status==201 and lps['ownerModule']=='LPS')
        checks.check('rps_preview_does_not_accept_lps_ownership',preview(lps)[0] in (403,422))

        changed=copy.deepcopy(static); changed['sections'][0]['bands'][0]['elements'][1]['properties']['payload']='새 버전 000099'
        status,second=save(static_view,changed)
        checks.check('qr_change_creates_immutable_revision',status==200 and second['revisionId']!=static_view['revisionId'])
        status,res=preview(static_view)
        checks.check('historical_preview_uses_original_payload',status==200 and decode(res['elements'][1]['qr'],'000012|한글 보고서\nTPS'))
        status,res=preview(second)
        checks.check('current_preview_uses_new_payload',status==200 and decode(res['elements'][1]['qr'],'새 버전 000099'))

        for name,code,mutate in [
            ('empty_qr','QR_PAYLOAD_EMPTY',lambda d:d['sections'][0]['bands'][0]['elements'][1]['properties'].update(payload='')),
            ('qr_capacity','QR_CAPACITY_EXCEEDED',lambda d:d['sections'][0]['bands'][0]['elements'][1]['properties'].update(payload='가'*3000)),
            ('unknown_visual_property','UNSUPPORTED_PREVIEW_PROPERTY',lambda d:d['sections'][0]['bands'][0]['elements'][0]['properties'].update(unsupportedVisual='test')),
        ]:
            altered=copy.deepcopy(static); mutate(altered)
            status,next_view=save(second,altered)
            if status==200:
                second=next_view
                ps,pe=preview(second)
                checks.check(name+'_explicit_preview_rejection',ps==422 and pe.get('code')==code)
            else: checks.check(name+'_explicit_save_rejection',status==422 and next_view.get('code')==code)

        wrong=copy.deepcopy(doc); wrong['datasets'][0]['queryRef']['version']='999'
        status,next_view=save(first,wrong)
        if status==200:
            first=next_view; ps,pe=preview(first,row['reportId'])
            checks.check('unknown_query_version_not_executed',ps==422 and pe.get('code')=='QUERY_NOT_SUPPORTED')
        else: checks.check('unknown_query_version_rejected_on_save',status==422 and next_view.get('code')=='QUERY_NOT_SUPPORTED')

        server.stop(); env.pop('TPS_RPS_REFERENCE_SITE_ID')
        server.start(api)
        status,empty,_=api.request('GET','/rps/ReportList')
        checks.check('default_source_site_never_falls_back_to_other_sites',status==200 and empty['rows']==[])
        server.stop(); env['TPS_RPS_REFERENCE_SITE_ID']=source_site; env['TPS_ENABLED_MODULES']='LPS'
        server.start(api)
        checks.check('disabled_rps_blocks_query',api.request('GET','/rps/ReportList')[0]==403)
        checks.check('disabled_rps_blocks_preview',preview(static_view)[0]==403)
        server.stop(); env['TPS_ENABLED_MODULES']='RPS,LPS'; env['TPS_SITE_ID']=site+'_X'
        server.start(api)
        checks.check('different_site_cannot_preview_document',preview(static_view)[0]==404)
        server.stop(); env['TPS_SITE_ID']=site

        after=observe(); report['after']=after
        checks.check('no_business_activation',after['activeCount']==0)
        checks.check('rps_rows_schema_identity_and_other_sites_preserved',before['rps']==after['rps'] and before['rpsCatalogHash']==after['rpsCatalogHash'] and before['otherSites']==after['otherSites'])
        checks.check('all_user_table_schemas_constraints_indexes_unchanged',before['databaseStructureHash']==after['databaseStructureHash'])
        report['result']='PASS'
    except Exception as e:
        report['result']='FAIL'; report['failureType']=type(e).__name__
        if isinstance(e,AssertionError): report['failedCheck']=str(e)
        if isinstance(e,ObserverFailure): report['diagnostic']=e.diagnostic
    finally:
        server.stop(); env['TPS_SITE_ID']=site; write_plan()
        if before is not None:
            try:
                report['cleanup']=observe('cleanup'); final=observe()
                checks.check('exact_test_rows_removed_and_originals_preserved',all(v['rows']==0 for v in final['site'].values()) and before['rps']==final['rps'] and before['otherSites']==final['otherSites'] and before['rpsCatalogHash']==final['rpsCatalogHash'] and before['databaseStructureHash']==final['databaseStructureHash'])
            except Exception as e: report['result']='FAIL'; report['cleanupFailureType']=type(e).__name__
        report['ownedProcessIds']=server.pids; report['passedChecks']=sum(c['passed'] for c in checks.items)
        (output/'report-api-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        env.pop('TPS_DB_PASSWORD',None); env.pop('TPS_DEV_PASSWORD',None)
    print(json.dumps({k:report.get(k) for k in ('result','passedChecks','failureType','failedCheck','cleanup')},ensure_ascii=False))
    return 0 if report['result']=='PASS' else 1


if __name__=='__main__':
    ap=argparse.ArgumentParser()
    for name in ('jar','java','jdbc','zxing','output'): ap.add_argument('--'+name,required=True)
    ap.add_argument('--port',type=int,default=18764)
    raise SystemExit(execute(ap.parse_args()))
