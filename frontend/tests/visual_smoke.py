"""UI-only regression with an explicitly mocked API; this is not a DB integration test.

Run with the Vite server already running:
python tests/visual_smoke.py --url http://127.0.0.1:5173 --chrome <chrome.exe> --output <evidence-dir>
"""
import argparse
import asyncio
import copy
import json
import functools
import http.server
import threading
from pathlib import Path
from playwright.async_api import async_playwright, expect


async def run(args):
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    saved = []
    captured = []
    save_started = asyncio.Event()
    release_save = asyncio.Event()
    head_started = asyncio.Event()
    release_head = asyncio.Event()
    fail_next_save = False
    delay_next_head = False
    errors = []
    template_id = '11111111-1111-4111-8111-111111111111'

    def make_view(doc, sequence):
        revision_id = f'22222222-2222-4222-8222-{sequence:012d}'
        return dict(templateId=template_id, name=doc['title'], ownerModule='RPS', headRevisionId=revision_id,
                    headToken=str(sequence), revisionNo=str(sequence), activeRevisionId=None,
                    createdAt='2026-11-06T01:00:00Z', lastSavedAt='2026-11-06T01:00:00Z', revisionId=revision_id,
                    sequence=str(sequence), bodyHash='ab' * 32,
                    headEtag=f'"h:{template_id}:{revision_id}:{sequence}"', document=copy.deepcopy(doc), diagnostics=[], editable=True)

    async def api(route):
        nonlocal fail_next_save, delay_next_head
        req = route.request
        path = req.url.split('/api/tps', 1)[1]
        method = req.method
        result = None
        status = 200
        if path == '/session':
            result = dict(actorId='tps-developer', siteId='MOCK_UI_ONLY', enabledModules=['RPS', 'LPS'])
        elif path.startswith('/templates?'):
            result = dict(items=saved[-1:] if 'module=RPS' in path else [], page=0, size=50, total=1 if saved else 0)
        elif (path == '/templates' and method == 'POST') or (path.endswith('/head') and method == 'PUT'):
            if fail_next_save:
                fail_next_save = False
                await route.fulfill(status=412, content_type='application/json', body=json.dumps(dict(code='STALE_HEAD', message='stale')))
                return
            captured.append(dict(body=copy.deepcopy(req.post_data_json), key=req.headers.get('idempotency-key'), client=req.headers.get('x-tps-client')))
            if not saved:
                save_started.set()
                await release_save.wait()
            result = make_view(req.post_data_json['document'], len(saved) + 1)
            saved.append(result)
            status = 201 if method == 'POST' else 200
        elif path.endswith('/head'):
            if delay_next_head:
                delay_next_head = False
                head_started.set()
                await release_head.wait()
            result = saved[-1]
        elif path.endswith('/revisions'):
            result = dict(items=[dict(revisionId=v['revisionId'], sequence=v['sequence'], bodyHash=v['bodyHash'], createdAt=v['createdAt'], createdBy='tps-developer') for v in reversed(saved)])
        elif '/revisions/' in path:
            result = copy.deepcopy(next(v for v in saved if v['revisionId'] == path.rsplit('/', 1)[1]))
            result['headRevisionId'] = saved[-1]['revisionId']
            result['headEtag'] = None
            result['editable'] = False
        else:
            raise AssertionError(f'Unexpected mocked API route: {method} {path}')
        await route.fulfill(status=status, content_type='application/json', body=json.dumps(result, ensure_ascii=False))

    async with async_playwright() as p:
        browser = await p.chromium.launch(executable_path=args.chrome, headless=True)
        context = await browser.new_context(viewport=dict(width=1440, height=1000), locale='ko-KR')
        await context.route('**/api/tps/**', api)
        page = await context.new_page()
        page.on('pageerror', lambda err: errors.append(str(err)))
        await page.goto(args.url)
        await page.get_by_label('개발 환경 연결 암호').fill('synthetic-browser-fixture')
        await page.get_by_role('button', name='디자이너 열기').click()
        title = page.get_by_role('textbox', name='양식 이름')
        await expect(title).to_have_value('새 양식')
        await title.fill('TPS UI 저장 검증')
        await page.get_by_role('textbox', name='요소 문구').fill('생산 실적 보고서')
        await page.get_by_role('button', name='사각형 추가').click()
        await expect(page.get_by_label('사각형 채우기')).to_be_visible()
        await page.screenshot(path=str(output / 'designer-desktop.png'), full_page=True)
        await page.get_by_role('button', name='저장', exact=True).click()
        await asyncio.wait_for(save_started.wait(), 10)
        await title.fill('저장 중 추가 편집')
        release_save.set()
        await expect(page.get_by_text('저장했습니다. 이후 편집한 내용은 아직 저장하지 않았습니다.', exact=True)).to_be_visible()
        await expect(title).to_have_value('저장 중 추가 편집')
        assert saved[0]['document']['title'] == 'TPS UI 저장 검증'
        await page.get_by_role('button', name='저장', exact=True).click()
        await expect(page.get_by_text('저장했습니다. 업무 출력에는 아직 적용하지 않았습니다.', exact=True)).to_be_visible()
        assert len(saved) == 2
        assert captured[0]['client'] == 'designer'
        assert captured[0]['key'] != captured[1]['key']
        await page.get_by_role('tab', name='저장 이력').click()
        await page.get_by_role('button', name='버전 1', exact=False).click()
        await expect(title).to_have_value('TPS UI 저장 검증')
        await expect(title).to_be_disabled()
        await expect(page.get_by_text('읽기 전용', exact=True)).to_be_visible()
        await page.get_by_role('button', name='최신본 열기', exact=True).click()
        await expect(title).to_have_value('저장 중 추가 편집')
        await expect(title).to_be_enabled()
        await title.fill('미저장 문구')
        await page.get_by_role('button', name='＋ 새 양식', exact=True).click()
        await expect(page.get_by_label('편집 전환 확인')).to_be_visible()
        await page.get_by_role('button', name='계속 편집', exact=True).click()
        await expect(title).to_have_value('미저장 문구')
        fail_next_save = True
        await page.get_by_role('button', name='저장', exact=True).click()
        await expect(page.get_by_role('button', name='다른 이름으로 저장', exact=True)).to_be_enabled()
        delay_next_head = True
        await page.get_by_role('button', name='최신본 열기', exact=True).click()
        await page.get_by_role('button', name='변경 버리고 이동', exact=True).click()
        await asyncio.wait_for(head_started.wait(), 10)
        await expect(page.get_by_role('button', name='다른 이름으로 저장', exact=True)).to_be_disabled()
        await expect(page.get_by_role('button', name='복사하여 새 양식', exact=True)).to_be_disabled()
        release_head.set()
        await expect(title).to_have_value('저장 중 추가 편집')
        await expect(title).to_be_enabled()
        await expect(page.get_by_role('button', name='복사하여 새 양식', exact=True)).to_be_enabled()
        await page.set_viewport_size(dict(width=390, height=844))
        await page.screenshot(path=str(output / 'designer-mobile.png'), full_page=True)
        assert await page.evaluate('document.documentElement.scrollWidth <= window.innerWidth'), 'mobile horizontal overflow'
        assert not errors, errors
        await context.close()
        await browser.close()
    result = dict(status='PASS', kind='mock-api-browser-only', assertions=['draft editing', 'text/rectangle tools', 'editing during delayed save retained', 'second save uses new key', 'mutation header', 'historical revision readonly', 'latest revision reopen', 'dirty navigation preserves edits', 'conflict-copy disabled during pending latest-load and re-enabled afterward', 'mobile overflow', 'no browser exceptions'], savedMockRevisions=len(saved), databaseWrites=0)
    (output / 'visual-smoke-result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--url', default='http://127.0.0.1:5173')
    parser.add_argument('--chrome', help='Optional browser executable; defaults to Playwright headless Chromium')
    parser.add_argument('--dist', help='Serve this built UI directory in an owned Python thread')
    parser.add_argument('--output', required=True)
    args=parser.parse_args()
    server=None
    if args.dist:
        class QuietHandler(http.server.SimpleHTTPRequestHandler):
            def log_message(self, *unused): pass
        handler=functools.partial(QuietHandler,directory=str(Path(args.dist).resolve()))
        server=http.server.ThreadingHTTPServer(('127.0.0.1',0),handler)
        thread=threading.Thread(target=server.serve_forever,daemon=True); thread.start()
        args.url=f'http://127.0.0.1:{server.server_port}'
    try: asyncio.run(run(args))
    finally:
        if server: server.shutdown(); server.server_close(); thread.join(timeout=5)
