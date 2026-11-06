"""Real browser -> packaged TPS -> local SQL Server, synthetic data only."""
import argparse
import datetime as dt
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
from playwright.sync_api import sync_playwright, expect
from run_integration import Api, OwnedServer


def run(args):
    output = Path(args.output).resolve(); output.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ)
    env['TPS_DB_USER'] = env.get('TPS_DB_USER') or input('Local TPS SQL user: ')
    env['TPS_DB_PASSWORD'] = env.get('TPS_DB_PASSWORD') or getpass.getpass('Local TPS SQL password (not saved): ')
    if env.get('TPS_DB_TRUST_SERVER_CERTIFICATE') != 'true':
        raise ValueError('Explicit local test certificate exception required')
    site = 'TPS_IT_UI_' + dt.datetime.now(dt.timezone.utc).strftime('%Y%m%d%H%M%S') + '_' + secrets.token_hex(2)
    password = secrets.token_urlsafe(32)
    env.update(TPS_DB_HOST='10.121.13.159', TPS_DB_PORT='1433', TPS_DB_NAME='TPS', TPS_SITE_ID=site,
               TPS_DEV_PASSWORD=password, TPS_ENABLED_MODULES='RPS,LPS', SPRING_PROFILES_ACTIVE='local')
    api = Api(args.port, password)
    server = OwnedServer(args.java, Path(args.jar).resolve(), args.port, env)
    helper = Path(__file__).with_name('LocalDbObserver.java')
    plan = output / 'owned-cleanup-plan.txt'
    templates, drafts, checks, errors = set(), set(), [], []
    report = dict(siteId=site, kind='real-browser-packaged-jar-local-sql', checks=checks,
                  jarSha256=hashlib.sha256(Path(args.jar).read_bytes()).hexdigest())
    before = None

    def write_plan():
        plan.write_text(site+'\n'+''.join('T='+v+'\n' for v in sorted(templates))+''.join('C='+v+'\n' for v in sorted(drafts)), encoding='utf-8')

    def observe(action='snapshot'):
        p = subprocess.run([args.java, '-cp', args.jdbc, str(helper), action, site if action=='snapshot' else str(plan)],
                           env=env, capture_output=True, text=True, encoding='utf-8', timeout=60,
                           creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        if p.returncode: raise RuntimeError('Observer '+action+' failed')
        return json.loads(p.stdout)

    def check(name, value=True):
        checks.append(dict(name=name, passed=bool(value)))
        assert value, name

    def on_request(req):
        if req.method == 'POST' and req.url.endswith('/api/tps/templates'):
            drafts.add(req.post_data_json['clientDraftId']); write_plan()

    def login(browser):
        context=browser.new_context(viewport={'width':1440,'height':1000}, locale='ko-KR')
        page=context.new_page(); page.on('request',on_request)
        page.on('pageerror',lambda e:errors.append(type(e).__name__))
        page.goto(f'http://127.0.0.1:{args.port}'); page.wait_for_load_state('networkidle')
        # Inspect rendered state before interacting; do not retain credentials or page source.
        assert '디자이너 열기' in page.get_by_role('button').all_text_contents()
        page.get_by_label('개발 환경 연결 암호').fill(password)
        page.get_by_role('button',name='디자이너 열기').click()
        expect(page.get_by_role('textbox',name='양식 이름')).to_be_visible()
        return context,page

    def save(page, module, title):
        with page.expect_response(lambda r:r.request.method in ('POST','PUT') and ('/templates' in r.url) and r.status in (200,201)) as response:
            page.get_by_role('button',name='저장',exact=True).click()
        view=response.value.json(); templates.add(view['templateId'].lower()); write_plan()
        expect(page.get_by_text('저장했습니다. 업무 출력에는 아직 적용하지 않았습니다.',exact=True)).to_be_visible()
        check('browser_saved_'+module+'_'+view['sequence'], view['ownerModule']==module and view['document']['title']==title and view['activeRevisionId'] is None)
        return view

    try:
        before=observe(); check('test_site_initially_empty', all(v['rows']==0 for v in before['site'].values()))
        server.start(api)
        with sync_playwright() as p:
            browser=p.chromium.launch(headless=True)
            context,page=login(browser)
            title=page.get_by_role('textbox',name='양식 이름')
            title.fill('TPS_IT 보고서 저장 검증')
            page.get_by_role('textbox',name='요소 문구').fill('생산 실적 보고서 · 저장 확인')
            page.get_by_role('button',name='사각형 추가').click()
            page.get_by_label('사각형 채우기').fill('#e6eef7')
            first=save(page,'RPS','TPS_IT 보고서 저장 검증')
            title.fill('TPS_IT 보고서 수정본')
            second=save(page,'RPS','TPS_IT 보고서 수정본')
            check('browser_edit_makes_new_revision',second['sequence']=='2' and second['revisionId']!=first['revisionId'])
            page.get_by_role('tab',name='저장 이력').click()
            page.get_by_role('button',name=re.compile('^버전 1')).click()
            expect(title).to_have_value('TPS_IT 보고서 저장 검증'); expect(title).to_be_disabled()
            check('browser_historical_readonly')
            page.get_by_role('button',name='최신본 열기',exact=True).click()
            expect(title).to_have_value('TPS_IT 보고서 수정본'); expect(title).to_be_enabled()
            page.screenshot(path=str(output/'real-designer-desktop.png'),full_page=True)
            check('credentials_not_persisted',page.evaluate('localStorage.length===0 && sessionStorage.length===0'))
            context.close()
            context,page=login(browser)
            page.get_by_role('button',name=re.compile('^TPS_IT 보고서 수정본')).click()
            expect(page.get_by_label('편집 전환 확인')).to_be_visible()
            page.get_by_role('button',name='변경 버리고 이동',exact=True).click()
            expect(page.get_by_role('textbox',name='양식 이름')).to_have_value('TPS_IT 보고서 수정본')
            check('new_browser_session_reopens_saved_template')
            page.get_by_label('작업 영역').select_option('LPS')
            title=page.get_by_role('textbox',name='양식 이름'); expect(title).to_have_value('새 양식')
            title.fill('TPS_IT LPS 독립 양식'); save(page,'LPS','TPS_IT LPS 독립 양식')
            expect(page.locator('.template-item')).to_have_count(1)
            expect(page.locator('.template-item')).to_contain_text('TPS_IT LPS 독립 양식')
            check('browser_lps_catalog_separate',page.locator('.template-item').count()==1)
            page.set_viewport_size({'width':390,'height':844})
            page.screenshot(path=str(output/'real-designer-mobile.png'),full_page=True)
            check('responsive_no_horizontal_page_overflow',page.evaluate('document.documentElement.scrollWidth <= window.innerWidth'))
            check('no_javascript_exceptions',not errors)
            context.close(); browser.close()
        server.stop(); server.start(api)
        status,opened,_=api.request('GET','/templates/'+second['templateId']+'/head')
        check('browser_saved_template_survives_jvm_restart',status==200 and opened['document']==second['document'])
        after=observe(); report['before']=before; report['after']=after
        check('rps_and_other_sites_unchanged',before['rps']==after['rps'] and before['otherSites']==after['otherSites'] and before['rpsCatalogHash']==after['rpsCatalogHash'])
        report['result']='PASS'
    except Exception as e:
        report['result']='FAIL'; report['failureType']=type(e).__name__
        # Browser assertion messages contain only synthetic fixture names, but omit arbitrary payloads.
        if isinstance(e,AssertionError): report['failedCheck']=str(e)[:300]
    finally:
        server.stop(); write_plan()
        if before is not None:
            try:
                report['cleanup']=observe('cleanup'); final=observe()
                check('test_rows_cleaned_and_existing_data_preserved',all(v['rows']==0 for v in final['site'].values()) and final['rps']==before['rps'] and final['otherSites']==before['otherSites'])
            except Exception as e: report['result']='FAIL'; report['cleanupFailureType']=type(e).__name__
        report['ownedProcessIds']=server.pids
        report['passedChecks']=sum(c['passed'] for c in checks)
        (output/'browser-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        env.pop('TPS_DB_PASSWORD',None); env.pop('TPS_DEV_PASSWORD',None)
    print(json.dumps({k:report.get(k) for k in ('result','passedChecks','failureType','failedCheck','cleanup')},ensure_ascii=False))
    return 0 if report['result']=='PASS' else 1


if __name__=='__main__':
    ap=argparse.ArgumentParser(); ap.add_argument('--jar',required=True); ap.add_argument('--java',required=True)
    ap.add_argument('--jdbc',required=True); ap.add_argument('--output',required=True); ap.add_argument('--port',type=int,default=18762)
    raise SystemExit(run(ap.parse_args()))
