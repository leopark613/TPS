# TPS-IMPL-002 React 작업 검증

2026-11-06. 별도 작업 복사본의 frontend/만 수정했다. 원본 RPS/LPS/TPS 저장소·정본기록·DB는 이 작업에서 수정하지 않았다.

## 구현
- RPS 고정 예제 데이터 연결/새로고침/해제, 텍스트·QR 필드 연결/해제와 null 정책. 결과행은 문서에 저장하지 않는다.
- RPS/LPS 공통 QR 배치/정적 내용 편집. 배치뷰는 읽을 수 없는 QR 자리 표시다.
- 저장본의 정확한 revision/bodyHash/선택행으로 별도 데이터 미리보기. 암묵적 저장 없음.
- 편집/이동/저장/선택행 변경과 요청 세대가 다르면 늦은 응답 무시. 실패·재조회 시 기존 미리보기 제거.
- 서버 matrix만 SVG로 그리고 바깥 4모듈 흰 여백, 정사각형 비율 유지. 프런트 QR 인코더 의존성 추가0.
- 기존 저장 중 추가 편집/중복요청/충돌/과거 버전 읽기 전용 흐름 유지.

## 실제 실행 결과
- `npm test`: 32개 PASS (기존15 + 신규17).
- `npm run build`: TypeScript 검사 + Vite production build PASS. 새 의존성/lock 변경0.
- `tests/report_preview_smoke.py`: 합성 mocked API 브라우저15개 PASS. 순서 뒤집힌 응답, 편집 중 응답, 실패 후 이전QR 제거, 과거revision, 데이터조회 실패, 연결해제, LPS정적QR, 모바일 가로overflow 및 브라우저예외를 검사했다.
- `tests/visual_smoke.py`: 기존 합성 저장/재열기/경합 브라우저11개 PASS.
- Desktop/mobile 화면을 이미지로 직접 열어 확인했다. 기존 색/배치 유지, 데이터조작 위쪽 분리, 미리보기 표시와 배치복귀, 모바일 inspector 하단 배치가 읽힌다.
- 모의 QR matrix는 UI 표시용 합성 테스트 응답이다. 실제 QR 인코딩/해독·실DB·출력 성공 증거로 해석하지 않는다. 부모 에이전트가 실제 통합검증을 담당한다.

증거: `evidence/frontend-unit.txt`, `evidence/report-preview-mock/report-preview-result.json`, `evidence/save-regression-mock/visual-smoke-result.json`, 각 폴더의 합성 스크린샷.

## 실패 및 수정 이력
- 초기 npm 실행 위치가 복사본 루트여서 package.json 미발견. 실제 frontend/에서 재실행했다.
- 복사본에 node_modules가 없어 offline npm ci를 시도했으나 why-is-node-running 캐시 부재로 실패. 기존 실제 TPS의 설치 의존성을 작업복사본에 복사해 사용했다. 원본 수정/새 패키지 추가0.
- 첫 tsc에서 JSON 객체 타입 narrowing 오류1곳. source 로컬변수로 좁혀 수정 후 build 통과.
- 독립검토에서 payload 미지정 정적QR의 서버/프런트 편집가능 판정차이 지적. 미지정 payload를 빈문구로 수정 가능하도록 맞추고 단위회귀 추가.
- font 입력4..200 범위를 서버 preview 계약에 맞췄다. SVG 문자열 공백보존도 명시했다.
- sandbox의 로컬 Python HTTPServer 시작이 진행되지 않아 두 시험이 출력폴더 생성 전 멈췄다. 정확한 명령줄/PID 확인 후 해당 시험 Python 자식만 종료했고 venv 부모도 종료됐다. require_escalated로 같은 loopback 합성시험 재실행 후 통과했다. 테스트 서버/browser는 finally로 정리한다. 사용자 프로세스/DB접속/프린터작업0.

## 부모 통합용 파일
수정: src/App.tsx, Canvas.tsx, api.ts, document.ts, styles.css, DESIGN.md.
신규: src/report.ts, ReportPanel.tsx, report.test.ts, tests/report_preview_smoke.py, 이 문서.
제품 실행 파일에는 증거PNG/테스트결과를 포함할 필요가 없다. 증거는 정본 검증폴더에 보관한 뒤 작업복사본은 부모의 검증된 정리 절차를 따른다.
