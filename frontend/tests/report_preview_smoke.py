"""Synthetic mocked API UI test. No real DB/query/printing, no QR encoder verification.
Owns its static HTTP thread and headless browser; both always close.
"""
import argparse
import asyncio
import copy
import functools
import http.server
import json
import re
import threading
from pathlib import Path
from playwright.async_api import async_playwright, expect


async def run(args):
    out = Path(args.output); out.mkdir(parents=True, exist_ok=True)
    saved = []; previews = []; errors = []; checked = []
    delay_next = False; fail_next = False; fail_list = False
    started = asyncio.Event(); release = asyncio.Event(); finished = asyncio.Event()
    fields = ['reportId', 'reportName', 'reportClassId']
    dataset = dict(id='rps-report-list', queryRef=dict(id='ReportList', classId='TPS_LOCAL_SAMPLE', version='1'), ordinal=0,
                   fields=[dict(id=f, type='string', path=[f]) for f in fields])
    rows = [dict(reportId='REPORT_A', reportName='시험 보고서 A', reportClassId='SYNTHETIC'), dict(reportId='REPORT_B', reportName='시험 보고서 B', reportClassId=None)]
    template_id = '11111111-1111-4111-8111-111111111111'

    def view(doc, owner):
        n = len(saved) + 1; rid = f'22222222-2222-4222-8222-{n:012d}'
        return dict(templateId=template_id, name=doc['title'], ownerModule=owner, revisionId=rid, headRevisionId=rid,
                    headToken=str(n), revisionNo=str(n), sequence=str(n), bodyHash='ab' * 32, headEtag=f'"h:{n}"',
                    activeRevisionId=None, createdAt='2026-11-06T01:00:00Z', lastSavedAt='2026-11-06T01:00:00Z', document=copy.deepcopy(doc), diagnostics=[], editable=True)

    async def api(route):
        nonlocal delay_next, fail_next, fail_list
        req = route.request; path = req.url.split('/api/tps', 1)[1]; status = 200
        if path == '/session':
            result = dict(actorId='tps-developer', siteId='MOCK_REPORT_ONLY', enabledModules=['RPS', 'LPS'])
        elif path.startswith('/templates?'):
            items = [v for v in saved[-1:] if f"module={v['ownerModule']}" in path]
            result = dict(items=items, page=0, size=50, total=len(items))
        elif path == '/rps/ReportList':
            if fail_list:
                fail_list = False; status = 503; result = dict(code='REPORT_DATA_UNAVAILABLE', message='데이터 조회 시험 실패')
            else: result = dict(dataset=dataset, rows=rows, truncated=False, label='로컬 보고서 목록 예제')
        elif path == '/rps/Preview':
            request = req.post_data_json; previews.append(copy.deepcopy(request))
            hold = delay_next; delay_next = False
            if hold: started.set(); await release.wait()
            if fail_next:
                fail_next = False; status = 503; result = dict(code='REPORT_DATA_UNAVAILABLE', message='미리보기 시험 실패')
            else:
                stored = next(v for v in saved if v['revisionId'] == request['revisionId'])
                doc = stored['document']; row = next((r for r in rows if r['reportId'] == request['reportId']), {})
                rendered = []
                for el in doc['sections'][0]['bands'][0]['elements']:
                    item = {k: copy.deepcopy(el[k]) for k in ['id', 'type', 'frame', 'properties']}
                    for b in el['bindings']: item['properties'][b['property']] = row[b['source']['fieldId']] or ''
                    if el['type'] == 'tps.qr':
                        item['qr'] = dict(matrix=['1' * 21] + ['1' + '0' * 19 + '1'] * 19 + ['1' * 21], moduleCount=21, quietZoneModules=4, version=1, errorCorrection='M', encoding='UTF-8')
                    rendered.append(item)
                result = dict(templateId=stored['templateId'], revisionId=stored['revisionId'], bodyHash=stored['bodyHash'],
                              queryRef=dataset['queryRef'] if doc['datasets'] else None, executedAt='2026-11-06T01:00:00Z', reportId=request['reportId'],
                              resultHash='cd' * 32, units='um', page=doc['sections'][0]['page'], elements=rendered, diagnostics=[])
            await route.fulfill(status=status, content_type='application/json', body=json.dumps(result));
            if hold: finished.set()
            return
        elif (path == '/templates' and req.method == 'POST') or (path.endswith('/head') and req.method == 'PUT'):
            result = view(req.post_data_json['document'], req.post_data_json.get('ownerModule', saved[-1]['ownerModule'] if saved else 'RPS')); saved.append(result)
        elif path.endswith('/head'): result = saved[-1]
        elif path.endswith('/revisions'):
            result = dict(items=[dict(revisionId=v['revisionId'], sequence=v['sequence'], bodyHash=v['bodyHash'], createdAt=v['createdAt'], createdBy='mock') for v in reversed(saved)])
        elif '/revisions/' in path:
            result = copy.deepcopy(next(v for v in saved if v['revisionId'] == path.rsplit('/', 1)[1])); result.update(editable=False, headEtag=None, headRevisionId=saved[-1]['revisionId'])
        else: raise AssertionError(f'Unexpected mock endpoint: {req.method} {path}')
        await route.fulfill(status=status, content_type='application/json', body=json.dumps(result))

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        try:
            context = await browser.new_context(viewport=dict(width=1440, height=980)); page = await context.new_page()
            page.on('pageerror', lambda e: errors.append(str(e)))
            await page.route('**/api/tps/**', api)
            await page.goto(args.url); await page.wait_for_load_state('networkidle')
            await page.get_by_label('개발 환경 연결 암호').fill('mock-only-secret')
            await page.get_by_role('button', name='디자이너 열기').click()
            await expect(page.get_by_label('양식 캔버스')).to_be_visible()
            # Inspect the rendered controls before actions; selectors are user-visible names.
            assert await page.get_by_role('button', name='데이터 연결', exact=True).count() == 1
            title = page.get_by_role('textbox', name='양식 이름')
            await title.fill('TPS 데이터·QR 시험')
            await page.get_by_role('button', name='데이터 연결', exact=True).click()
            await expect(page.get_by_label('미리볼 보고서')).to_be_visible()
            await page.get_by_label('표시할 내용').select_option('reportName')
            await expect(page.get_by_role('textbox', name='요소 문구')).to_be_disabled()
            await page.get_by_role('button', name='QR 추가', exact=True).click()
            await page.get_by_label('표시할 내용').select_option('reportId')
            await expect(page.get_by_role('textbox', name='QR 문구')).to_be_disabled()
            await expect(page.get_by_test_id('preview-qr')).to_have_count(0)
            await expect(page.get_by_role('button', name='저장본 미리보기', exact=True)).to_be_disabled()
            await page.get_by_role('button', name='저장', exact=True).click()
            await expect(page.get_by_text('저장했습니다. 업무 출력에는 아직 적용하지 않았습니다.', exact=True)).to_be_visible()
            assert len(saved) == 1 and 'rows' not in json.dumps(saved[0]['document'])
            checked += ['binding metadata only', 'QR placement is not a generated code', 'dirty preview disabled', 'explicit save']

            await page.get_by_label('미리볼 보고서').select_option('REPORT_A')
            delay_next = True
            await page.get_by_role('button', name='저장본 미리보기', exact=True).click(); await asyncio.wait_for(started.wait(), 10)
            await page.get_by_label('미리볼 보고서').select_option('REPORT_B')
            await expect(page.get_by_test_id('saved-preview')).to_have_count(0)
            await page.get_by_role('button', name='저장본 미리보기', exact=True).click()
            await expect(page.get_by_label('저장본 데이터 미리보기')).to_contain_text('시험 보고서 B')
            release.set(); await asyncio.wait_for(finished.wait(), 10)
            await expect(page.get_by_label('저장본 데이터 미리보기')).to_contain_text('시험 보고서 B')
            await expect(page.get_by_label('저장본 데이터 미리보기')).not_to_contain_text('시험 보고서 A')
            assert len(saved) == 1
            await page.screenshot(path=str(out / 'report-preview-desktop.png'), full_page=True)
            checked += ['row change clears old preview', 'out-of-order response ignored', 'preview never auto-saves']

            await page.get_by_role('button', name='배치 편집으로 돌아가기').click()
            started = asyncio.Event(); release = asyncio.Event(); finished = asyncio.Event(); delay_next = True
            await page.get_by_role('button', name='저장본 미리보기', exact=True).click(); await asyncio.wait_for(started.wait(), 10)
            await title.fill('TPS 편집 중 응답 시험')
            release.set(); await asyncio.wait_for(finished.wait(), 10)
            await expect(page.get_by_test_id('saved-preview')).to_have_count(0)
            await expect(page.get_by_role('button', name='저장본 미리보기', exact=True)).to_be_disabled()
            await page.get_by_role('button', name='저장', exact=True).click()
            await expect(page.get_by_text('저장했습니다. 업무 출력에는 아직 적용하지 않았습니다.', exact=True)).to_be_visible()
            checked += ['edit during preview invalidates late response']

            await page.get_by_role('button', name='저장본 미리보기', exact=True).click()
            await expect(page.get_by_test_id('preview-qr')).to_be_visible()
            fail_next = True
            await page.get_by_role('button', name='저장본 미리보기', exact=True).click()
            await expect(page.get_by_text('미리보기 시험 실패', exact=True)).to_be_visible()
            await expect(page.get_by_test_id('preview-qr')).to_have_count(0)
            checked += ['failure removes previous QR']

            await page.get_by_role('tab', name='저장 이력').click()
            await page.get_by_role('button', name=re.compile('^버전 1')).click()
            await expect(title).to_be_disabled()
            await page.get_by_role('button', name='데이터 새로고침').click()
            await page.get_by_label('미리볼 보고서').select_option('REPORT_A')
            await page.get_by_role('button', name='저장본 미리보기', exact=True).click()
            await expect(page.get_by_test_id('saved-preview')).to_be_visible()
            assert previews[-1]['revisionId'] == saved[0]['revisionId']
            checked += ['historical immutable revision preview']
            fail_list = True
            await page.get_by_role('button', name='데이터 새로고침').click()
            await expect(page.get_by_text('데이터 조회 시험 실패', exact=True)).to_be_visible()
            await expect(page.get_by_test_id('saved-preview')).to_have_count(0)
            await expect(page.get_by_label('미리볼 보고서')).to_have_value('')
            checked += ['query refresh failure clears preview and row']

            await page.get_by_role('button', name='최신본 열기', exact=True).click()
            await expect(title).to_be_enabled()
            await page.get_by_role('tab', name='속성').click()
            await page.get_by_role('button', name='데이터 연결 해제', exact=True).click()
            await page.get_by_role('button', name=re.compile('^QR 요소')).click()
            await expect(page.get_by_label('표시할 내용')).to_have_value('')
            await expect(page.get_by_role('textbox', name='QR 문구')).to_have_value('TPS')
            await expect(page.get_by_role('textbox', name='QR 문구')).to_be_enabled()
            await page.set_viewport_size(dict(width=390, height=844))
            await page.screenshot(path=str(out / 'report-editor-mobile.png'), full_page=True)
            assert await page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
            checked += ['disconnect restores direct input', 'mobile no horizontal overflow']

            await page.get_by_label('작업 영역').select_option('LPS')
            await page.get_by_role('button', name='변경 버리고 이동', exact=True).click()
            await page.get_by_role('button', name='QR 추가', exact=True).click()
            await page.get_by_role('textbox', name='QR 문구').fill('00123 한글\nLPS')
            await expect(page.get_by_role('button', name='데이터 연결', exact=True)).to_have_count(0)
            await page.get_by_role('button', name='저장', exact=True).click()
            await expect(page.get_by_text('저장했습니다. 업무 출력에는 아직 적용하지 않았습니다.', exact=True)).to_be_visible()
            assert saved[-1]['ownerModule'] == 'LPS' and saved[-1]['document']['datasets'] == []
            assert saved[-1]['document']['sections'][0]['bands'][0]['elements'][-1]['properties']['payload'] == '00123 한글\nLPS'
            checked += ['LPS static QR independent of RPS data']
            assert not errors, errors
            checked += ['no browser exceptions']
            await context.close()
        finally: await browser.close()
    summary = dict(status='PASS', kind='synthetic-mock-api-ui', checks=checked, count=len(checked), databaseWrites=0,
                   qrEncodingVerified=False, notes='Matrix shape is a synthetic test response; real QR decoding is a separate server/integration test.')
    (out / 'report-preview-result.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('--dist', required=True); parser.add_argument('--output', required=True)
    args = parser.parse_args()
    class QuietHandler(http.server.SimpleHTTPRequestHandler):
        def log_message(self, *unused): pass
    server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), functools.partial(QuietHandler, directory=str(Path(args.dist).resolve())))
    thread = threading.Thread(target=server.serve_forever, daemon=True); thread.start(); args.url = f'http://127.0.0.1:{server.server_port}'
    try: asyncio.run(run(args))
    finally: server.shutdown(); server.server_close(); thread.join(timeout=5)
