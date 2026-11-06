"""Real React -> Boot -> local TPS data binding, saved revision preview and screen QR decode."""
import argparse
import base64
import datetime as dt
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import traceback
from playwright.sync_api import sync_playwright, expect
from run_integration import Api, OwnedServer, Checks, ObserverFailure, safe_observer_diagnostic


def run(args):
    output=Path(args.output).resolve(); output.mkdir(parents=True,exist_ok=True)
    env=dict(os.environ)
    env['TPS_DB_USER']=env.get('TPS_DB_USER') or input('Local TPS SQL user: ').strip()
    env['TPS_DB_PASSWORD']=env.get('TPS_DB_PASSWORD') or getpass.getpass('Local TPS SQL password (not saved): ')
    if env.get('TPS_DB_TRUST_SERVER_CERTIFICATE')!='true': raise ValueError('Explicit local test TLS exception required')
    site='TPS_IT_QRUI_'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%d%H%M%S')+'_'+secrets.token_hex(2)
    password=secrets.token_urlsafe(32)
    env.update(TPS_DB_HOST='10.121.13.159',TPS_DB_PORT='1433',TPS_DB_NAME='TPS',TPS_SITE_ID=site,
        TPS_DEV_PASSWORD=password,TPS_ENABLED_MODULES='RPS,LPS',SPRING_PROFILES_ACTIVE='local')
    api=Api(args.port,password); server=OwnedServer(args.java,Path(args.jar).resolve(),args.port,env)
    helper=Path(__file__).resolve().parent
    checks=Checks(); templates,drafts=set(),set(); errors=[]; before=None
    plan=output/'owned-cleanup-plan.txt'; transient_png=output/'owned-transient-bound-qr.png'
    report={'kind':'real-browser-local-sql-data-qr','siteId':site,'checks':checks.items,
            'jarSha256':hashlib.sha256(Path(args.jar).read_bytes()).hexdigest()}

    def run_helper(name,cp,argv):
        r=subprocess.run([args.java,'-cp',str(cp),str(helper/name),*argv],env=env,
            capture_output=True,text=True,encoding='utf-8',timeout=60,
            creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
        if r.returncode: raise ObserverFailure(safe_observer_diagnostic(r.stderr))
        return r.stdout

    def write_plan():
        plan.write_text(site+'\n'+''.join('T='+v+'\n' for v in sorted(templates))+''.join('C='+v+'\n' for v in sorted(drafts)),encoding='utf-8')

    def observe(action='snapshot'):
        return json.loads(run_helper('LocalDbObserver.java',args.jdbc,[action,site if action=='snapshot' else str(plan)]))

    def on_request(req):
        if req.method=='POST' and req.url.endswith('/api/tps/templates'):
            drafts.add(req.post_data_json['clientDraftId']); write_plan()

    def login(browser):
        context=browser.new_context(viewport={'width':1600,'height':1100},locale='ko-KR')
        page=context.new_page(); page.on('request',on_request)
        page.on('pageerror',lambda e:errors.append(type(e).__name__))
        page.goto(f'http://127.0.0.1:{args.port}'); page.wait_for_load_state('networkidle')
        assert '디자이너 열기' in page.get_by_role('button').all_text_contents()
        page.get_by_label('개발 환경 연결 암호').fill(password)
        page.get_by_role('button',name='디자이너 열기').click()
        expect(page.get_by_label('양식 이름')).to_be_visible()
        return context,page

    def save(page):
        with page.expect_response(lambda r:r.request.method in ('POST','PUT') and '/templates' in r.url and r.status in (200,201)) as response:
            page.get_by_role('button',name='저장',exact=True).click()
        view=response.value.json(); templates.add(view['templateId'].lower()); write_plan()
        expect(page.get_by_text('저장했습니다. 업무 출력에는 아직 적용하지 않았습니다.',exact=True)).to_be_visible()
        return view

    def preview(page):
        with page.expect_response(lambda r:r.url.endswith('/api/tps/rps/Preview')) as response:
            page.get_by_role('button',name='저장본 미리보기',exact=True).click()
        checks.check('browser_preview_http_success_'+str(len(checks.items)),response.value.status==200)
        expect(page.get_by_test_id('saved-preview')).to_be_visible()
        return response.value.json()

    def decode_screen(page,payload):
        # This is actual browser-rendered SVG pixels, not a matrix reconstructed by the test.
        qr=page.get_by_test_id('preview-qr'); expect(qr).to_be_visible()
        try:
            qr.screenshot(path=str(transient_png),scale='css')
            digest=run_helper('QrDecodeProbe.java',args.zxing,['png',str(transient_png)]).strip()
            return digest==hashlib.sha256(payload.encode('utf-8')).hexdigest()
        finally:
            # Exact owned file only; no directory sweep, no business QR retained in evidence.
            if transient_png.parent==output and transient_png.name=='owned-transient-bound-qr.png': transient_png.unlink(missing_ok=True)

    try:
        before=observe(); report['before']=before
        checks.check('ui_test_site_initially_empty',all(v['rows']==0 for v in before['site'].values()))
        env['TPS_RPS_REFERENCE_SITE_ID']=base64.b64decode(run_helper('ReportReferenceProbe.java',args.jdbc,[]).strip()).decode('utf-8')
        server.start(api)
        with sync_playwright() as p:
            browser=p.chromium.launch(headless=True)
            context,page=login(browser)
            page.get_by_label('양식 이름').fill('TPS_IT 데이터와 QR 브라우저 검증')
            expect(page.get_by_role('button',name='저장본 미리보기',exact=True)).to_be_disabled()
            with page.expect_response(lambda r:r.url.endswith('/api/tps/rps/ReportList')) as response:
                page.get_by_role('button',name='데이터 연결',exact=True).click()
            catalog=response.value.json(); row=catalog['rows'][0]
            expect(page.get_by_label('미리볼 보고서')).to_be_visible()
            page.get_by_label('표시할 내용').select_option('reportName')
            page.get_by_role('button',name='QR 추가',exact=True).click()
            page.get_by_label('표시할 내용').select_option('reportId')
            page.get_by_label('미리볼 보고서').select_option(row['reportId'])
            first=save(page)
            checks.check('browser_saves_text_and_qr_bindings',len(first['document']['datasets'])==1 and len(first['document']['sections'][0]['bands'][0]['elements'])==2 and all(e['bindings'] for e in first['document']['sections'][0]['bands'][0]['elements']))
            result=preview(page)
            checks.check('browser_uses_saved_revision_and_selected_row',result['revisionId']==first['revisionId'] and result['reportId']==row['reportId'])
            checks.check('actual_svg_bound_qr_decodes',decode_screen(page,row['reportId']))
            page.get_by_label('미리볼 보고서').select_option('')
            expect(page.get_by_test_id('saved-preview')).to_have_count(0)
            checks.check('row_change_clears_prior_success',page.get_by_test_id('saved-preview').count()==0)
            checks.check('browser_does_not_persist_credentials',page.evaluate('localStorage.length===0 && sessionStorage.length===0'))
            context.close()

            context,page=login(browser)
            page.get_by_role('button',name=re.compile('^TPS_IT 데이터와 QR 브라우저 검증')).click()
            expect(page.get_by_label('편집 전환 확인')).to_be_visible()
            page.get_by_role('button',name='변경 버리고 이동',exact=True).click()
            expect(page.get_by_label('양식 이름')).to_have_value('TPS_IT 데이터와 QR 브라우저 검증')
            with page.expect_response(lambda r:r.url.endswith('/api/tps/rps/ReportList')):
                page.get_by_role('button',name='데이터 새로고침',exact=True).click()
            page.get_by_label('미리볼 보고서').select_option(row['reportId'])
            result=preview(page)
            checks.check('new_browser_reopens_binding_and_previews_original_revision',result['revisionId']==first['revisionId'] and decode_screen(page,row['reportId']))

            page.get_by_role('button',name='배치 편집으로 돌아가기',exact=True).click()
            page.get_by_role('button',name='데이터 연결 해제',exact=True).click()
            page.get_by_role('button',name=re.compile('^QR 요소')).click()
            payload='000012|한글 보고서\nTPS'
            page.get_by_label('QR 문구').fill(payload)
            page.get_by_label('양식 이름').fill('TPS_IT 일반 보고서 QR 합성 예제')
            second=save(page); result=preview(page)
            checks.check('actual_svg_unicode_leading_zero_newline_decodes',decode_screen(page,payload))
            checks.check('disconnect_keeps_layout_and_removes_dataset_bindings',second['document']['datasets']==[] and all(e['bindings']==[] for e in second['document']['sections'][0]['bands'][0]['elements']))
            page.screenshot(path=str(output/'general-report-qr-synthetic.png'),full_page=True)
            page.set_viewport_size({'width':390,'height':844})
            page.screenshot(path=str(output/'general-report-qr-synthetic-mobile.png'),full_page=True)
            checks.check('new_controls_do_not_overflow_mobile_page',page.evaluate('document.documentElement.scrollWidth<=window.innerWidth'))
            checks.check('no_browser_javascript_exceptions',not errors)
            context.close(); browser.close()
        after=observe(); report['after']=after
        checks.check('no_activation_and_original_data_schema_preserved',after['activeCount']==0 and before['rps']==after['rps'] and before['otherSites']==after['otherSites'] and before['rpsCatalogHash']==after['rpsCatalogHash'] and before['databaseStructureHash']==after['databaseStructureHash'])
        report['result']='PASS'
    except Exception as e:
        report['result']='FAIL'; report['failureType']=type(e).__name__
        report['failureLocation']=[{'file':Path(f.filename).name,'line':f.lineno,'function':f.name} for f in traceback.extract_tb(e.__traceback__)[-3:]]
        # Only our own fixed check names, never arbitrary browser messages/business payloads.
        if isinstance(e,AssertionError) and str(e) in [c['name'] for c in checks.items]: report['failedCheck']=str(e)
        if isinstance(e,ObserverFailure): report['diagnostic']=e.diagnostic
    finally:
        server.stop(); write_plan()
        if transient_png.parent==output: transient_png.unlink(missing_ok=True)
        if before is not None:
            try:
                report['cleanup']=observe('cleanup'); final=observe()
                checks.check('exact_ui_test_rows_removed_originals_preserved',all(v['rows']==0 for v in final['site'].values()) and before['rps']==final['rps'] and before['otherSites']==final['otherSites'] and before['rpsCatalogHash']==final['rpsCatalogHash'] and before['databaseStructureHash']==final['databaseStructureHash'])
            except Exception as e: report['result']='FAIL'; report['cleanupFailureType']=type(e).__name__
        report['ownedProcessIds']=server.pids; report['passedChecks']=sum(c['passed'] for c in checks.items)
        (output/'report-browser-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        env.pop('TPS_DB_PASSWORD',None); env.pop('TPS_DEV_PASSWORD',None)
    print(json.dumps({k:report.get(k) for k in ('result','passedChecks','failureType','failedCheck','cleanup')},ensure_ascii=False))
    return 0 if report['result']=='PASS' else 1


if __name__=='__main__':
    ap=argparse.ArgumentParser()
    for name in ('jar','java','jdbc','zxing','output'): ap.add_argument('--'+name,required=True)
    ap.add_argument('--port',type=int,default=18765)
    raise SystemExit(run(ap.parse_args()))
