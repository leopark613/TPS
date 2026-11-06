# TPS 저장·재열기 계약 v0.1

## 최신 개발 DB 전환·실제 준비 완료 — D049/D050 (2026-11-06)

사용자 지시로 개발·시험 기준을 **로컬 SQL Server 10.121.13.159:1433 / TPS / dbo**로 변경했다. 새 TPS DB 생성, 신규 문서 테이블 A7 생성, MES_LITE의 기존 dbo.RPS_12개·292컬럼·272행 복사를 수행했다. source 읽기만·대상 신규 생성/복사 후 precommit 및 별도 연결 postcommit 구조/identity/값 해시 검증을 통과했다. 최종 별도 조회: dbo 테이블19개, RPS 복사행272, A7행0, 비활성/미신뢰 FK·CHECK0. 기존 LSE_BUSAN_DEV와 MES_LITE/G2 원본은 변경하지 않았다.

로컬 엔진17.0.1000.7 Express, DB collation Korean_100_CS_AI_KS_WS_SC_UTF8, compatibility150이다. MES_LITE 엔진15.x로의 향후 이동은 검토된 구조·데이터 스크립트 방식이며 최신엔진 .bak 하향복원을 전제하지 않는다. 이번 로컬 준비에만 인증서 확인 예외를 명시 허용받았고 암호화는 유지했다. 암호는 저장하지 않았다.

[실행 SQL·복사·검증 기록](<C:/Users/A1292021154/Desktop/LLM_Skill_Context/_TPS(ThiraPrintSystem)/TPS_Local_DB_Setup/README.md>) · [현재 DB 설계](<C:/Users/A1292021154/Desktop/LLM_Skill_Context/_TPS(ThiraPrintSystem)/design/LOCAL-DEVELOPMENT-DATABASE.md>) · [DBeaver 안내](<C:/Users/A1292021154/Desktop/LLM_Skill_Context/_TPS(ThiraPrintSystem)/03_로컬TPS_DB와_DBeaver_안내.txt>)

신규 TPS_ 후보42개 중 실제 생성은 A7이며 나머지35개는 상세설계 순서대로 준비한다. 42개 전체 구현/생성·RPS→TPS 문서 변환·제품 저장/렌더/프린터 시험은 완료한 것이 아니다. 앞으로 제품 시험은 로컬 TPS를 사용하고 부족한 MES 업무 데이터 때문에 원격 운영 DB로 자동 우회하지 않는다. 원래 MES_LITE에 직접 TPS를 설계/생성하려던 D047/D048의 위치·보류 범위는 이번 로컬 준비에 한해 대체됐으며 MES_LITE 재이관은 후속 별도 작업이다.

아래 과거 단계의 MES_LITE 기본 대상·DDL 미실행 문구는 당시 이력이다. 현재 기준은 이 블록과 D049/D050을 우선한다.

## 최신: 접두어 채택·MES_LITE 조회·생성 전 목록 (D048, 2026-11-06)

TPS_RPS_/TPS_LPS_/공통 TPS_DOC_/TPS_PRINT_ 접두어를 사용한다. MES_LITE catalog 조회에서 DB VIEW DEFINITION=1, LPS_/LSP_/TPS_ 및 소스71개/접두어 제거 이름의 객체0개, 기존 RPS_ 테이블12개를 확인했다. 업무 행 조회·쓰기·DDL·프로시저 호출0회이며 연결은 종료했다.

새 구조는 **42개 후보를 단계별로 검토**한다: A 문서저장7, B 조회·렌더·RPS·출력19, C LPS11, D 기존 기능 완성을 위한 후속 상세5. 이 수는 승인된 최종 개수나 한 번에 생성할 수량이 아니다. 첫 검토 묶음은 A7이며 실제 CREATE/ALTER/시험 쓰기는 하지 않는다. 사용자에게 테이블 목록을 먼저 제시하고 실제 생성에 대한 명시 지시 전까지 DB 생성은 보류한다.

[상세 기록](<C:/Users/A1292021154/Desktop/LLM_Skill_Context/_TPS(ThiraPrintSystem)/02_생성예정_TPS_테이블목록.txt>) · [상세 기록](<C:/Users/A1292021154/Desktop/LLM_Skill_Context/_TPS(ThiraPrintSystem)/design/database-v0.1/README.md>) · [상세 기록](<C:/Users/A1292021154/Desktop/LLM_Skill_Context/_TPS(ThiraPrintSystem)/analysis/lps/20261106-MESLITE-CATALOG.md>)

아래 이전 날짜/단계의 조사 미실행·접두어 권고 문구는 당시 이력이다. 현재의 사실·실행 경계는 이 블록과 D048을 우선한다. 기존 1회 관리자 권한을 재사용하지 않는다. 제품 저장/렌더/인쇄·성능 검증은 NOT RUN이다.

2026-11-06 / 설계안·제품 시험 NOT RUN. [문서 형식](README.md)의 공통 의미를 일반 보고서·Excel 유래 양식·LPS 양식에 동일하게 적용한다. 사용자가 저장과 업무 적용 분리를 선택했다(D046). 저장은 편집본 보관이며 업무에 적용은 별도 동작이다.

## 사용자 흐름

```text
열기 → 편집 중 → 저장 요청 → 서버 검증 → 원자적 저장 완료 → 저장 버전 표시
                  ├─ 입력 오류: 편집 내용 유지, 위치별 안내
                  ├─ 충돌: 내 편집 유지 + 서버 변경본 비교/다른 이름 저장
                  └─ 통신 불명: 요청 상태 확인, 같은 키로 재시도
저장본 재열기 → 버전/자산 확인 → 편집 캔버스 구성 → 필요할 때 미리보기
```

재열기만으로 query 실행·발급번호 소비·프린터 송신을 하지 않는다. 사용자가 명시적으로 미리보기/출력할 때 해당 계약을 호출한다. 새 양식 창·Excel 업로드를 열었다는 이유로 업무 DB에 빈 양식을 자동 생성하지 않는다.

저장 버튼은 편집 내용을 보관한다. 입력을 다시 편집한 동안 예전 저장 요청이 완료되면 그 응답의 편집 세대만 저장 완료로 표시하고 더 최신 편집은 dirty로 남긴다. 로딩 실패·변환 실패 시 기존 캔버스와 미저장 편집을 유지한다. 브라우저 종료 전 복구용 임시저장은 별도 정책이며 이번 설계가 항상 자동 복구됨을 보장하지 않는다.

열기/변환 요청에도 편집 세션·양식ID·load generation·시작 시 edit generation을 붙인다. 성공 응답이라도 다른 양식을 선택했거나 새 편집을 시작했으면 현재 캔버스에 적용하지 않는다. 늦은 응답을 폐기/보류하고 사용자 편집을 보존한다. 서버 저장본과 브라우저 화면의 최신성은 별도로 확인한다.

## 서버 계약

1. 인증·site/양식 수정 권한, 요청 크기/압축·중첩 제한을 검사한다. 중복 JSON key·비유한 수·잘못된 UTF-8 등 모호한 입력은 거부한다.
2. 요청 ID를 site+actor+operation+template 범위로 조회한다. 같은 키·같은 payload fingerprint이면 이전 결과/진행 상태를 반환한다. 같은 키·다른 payload는 409 `IDEMPOTENCY_KEY_REUSE`다. 완료 요청을 먼저 찾으므로 이미 성공한 재전송이 낡은 base revision 때문에 충돌로 바뀌지 않는다. 재전송에서도 현재 조회 권한은 검사한다.
3. schema·기능 버전·참조·요소별 속성·정적 표현식·asset ready/hash를 검사한다. 구조가 올바르나 미지원 기능/데이터 미연결이 있는 보정용 초안은 진단과 함께 저장 가능하게 설계한다. 단, 출력 가능과 구분하고 업무 적용을 막는다. 실행 데이터 없이 판정 불가한 검사는 후속 상태로 남긴다.
4. DB transaction에서 head의 잠금/비교 조건을 확인한다. immutable revision 삽입, head CAS 변경, 이력·참조·요청 완료 기록을 함께 commit한다. 조건 불일치면 전부 rollback하고 기존 데이터 보존. `(site,template,revision)` 및 요청 키 유일 제약으로 경쟁 재시도도 중복 생성하지 않는다. 동시 요청이 처음에는 둘 다 미등록으로 보였더라도, 유일 제약 충돌/CAS 실패 후 같은 요청 키의 commit 결과를 재조회해 성공한 재전송을 잘못412로 반환하지 않는다. 확정되지 않은 경쟁 요청은 진행 상태로 반환한다.
5. 성공 응답에는 templateId/revisionId/sequence/hash/head ETag와 진단을 준다. 브라우저는 확인된 revision을 기준으로 상태를 갱신한다. commit 뒤 응답만 끊겼으면 원래 요청 키로 결과를 복구한다. 저장 응답은 출력 성공이나 활성화를 뜻하지 않는다.

fingerprint는 method/대상/base revision·정규화된 요청 본문을 포함한다. 키 정렬·UTF-8·배열 순서·숫자 표현 규칙을 고정한 서버 직렬화를 사용할 계획이며 구체 알고리즘 버전은 구현 전에 고정한다. 원본 import hash는 원본 바이트에 대한 값으로 별도다. idempotency 레코드 만료 후 오래된 요청을 신규 요청처럼 재실행하지 않는 tombstone/거절 정책과 보존 기간은 후속으로 정한다.

## API 초안

별도 BFF 서버가 아닌 같은 WAS의 공통 문서 서비스다. site는 인증 문맥·권한으로 검증하며 단순 query string만 신뢰하지 않는다. 기존 RPS URL은 별도 호환 adapter에서 처리한다.

| 요청 | 조건·반환 |
|---|---|
| POST /api/tps/templates | 요청 키 필수, client draftId·body. 서버가 새 식별자 부여. 다른 양식과 ID 충돌 시 upsert 금지 |
| GET /api/tps/templates/{id}/head | 편집용 최신 envelope+본문, 강한 ETag. 정확한 revision과 검증 상태 반환 |
| PUT /api/tps/templates/{id}/head | `If-Match` 필수 + 요청 키 + body. head ETag를 비교해 새 immutable revision 생성 |
| GET /api/tps/templates/{id}/revisions/{revisionId} | 불변 저장본·hash·참조. 없는 버전은404, 권한 실패는 정책에 따른403/404 |
| POST /api/tps/save-requests/resolve | 조회 전용. requestId+operation+templateId(생성은 clientDraftId)를 받아 인증된site/actor와 함께 전체 유일키로 pending/completed/rejected/unknown 조회. unknown은 미commit 증거가 아님 |
| POST /api/tps/templates/{id}/restore | 옛 revision 내용을 새 head revision으로 복원. 요청 키·head 조건 필수, 원본 이력 수정 없음 |
| POST /api/tps/templates/{id}/activate | 별도 업무 적용 권한·요청 키 필수. 검증 완료된 특정 revision을 active로 CAS 전환; active 상태용 조건 토큰 사용 |

조건 누락은 저장 거절, head 조건 불일치는412, request-key 충돌은409, 문서 검증 실패는422로 제안한다. `If-Match`는 lost update 방지 조건이며 ETag를 revision 본문 GET과 head 리소스 사이에 혼용하지 않는다. 상세 상태 코드는 API 명세에서 고정한다. [HTTP 조건 요청 표준](https://www.rfc-editor.org/rfc/rfc9110.html#name-if-match)

자동 병합은 초기 필수가 아니다. 충돌 시 내 수정·서버 최신·공통 기준 revision을 비교하고 수동 반영 또는 다른 이름 저장을 제공하는 안이다. 단순 마지막 저장 승리는 금지한다.

## 업무에 적용 (D046)

처음 만든 양식에는 active가 없다. 편집본 저장·시험 미리보기는 가능하지만 업무 출력에는 사용할 수 없으며 적용 필요 상태를 안내한다. 업무 적용은 대상 revision·양식 소유 모듈·권한·동적 데이터 계약과 렌더 지원·자산 완전성을 확인한 뒤 처리한다. 미지원/보정 필요 초안은 적용할 수 없다.

적용 시점의 최신 head를 암묵 선택하지 않고 사용자가 확인한 revision을 지정한다. 그 사이 다른 사람이 새 편집본을 저장해도 지정 revision을 바꾸지 않는다. active CAS와 감사 기록·요청 완료는 한 transaction으로 처리하며 실패하면 이전 active가 유지된다. 과거 버전으로 되돌리기도 명시적 적용 동작으로 기록하고 기존 revision을 수정하지 않는다.

업무 요청을 접수할 때 active revision을 고정한다. 적용 전에 접수한 작업과 이미 확정된 발행건은 새 active로 바꾸지 않으며 새 요청부터 새 active를 사용한다. LPS 재출력은 active 조회가 아니라 원발행 snapshot을 따른다. 시험 미리보기는 초안 revision을 명시할 수 있으나 발급번호·업무 출력 요청을 생성하지 않는다.

양식의 업무 적용과 LPS 속성/매핑의 승인 절차는 별개다. 양식 적용만으로 미승인 업무 속성이 승인되거나 번호가 발급되지 않는다. LPS 발행 서비스에서 필요한 승인 상태·양식 버전·업무 규칙을 함께 검사한다.

## 재열기·자산·출력

- head와 본문을 서로 다른 최신 시점으로 섞지 않도록 먼저 특정 revision을 고정한다. hash 검증·지원 기능 확인·asset manifest 검사를 완료한 뒤 새 캔버스를 적용한다.
- 디스크/object store 자산은 업로드→hash 검증→불변 ready 상태가 먼저다. 이후 DB 참조를 commit한다. 실패한 임시 업로드는 격리하고 활성 참조를 삭제하지 않는다. DB와 파일시스템의 단일 transaction을 가정하지 않는다. 백업/복원은 DB 참조와 자산 manifest의 일관된 묶음이어야 한다.
- ready 확인과 참조 삽입 사이의 정리 경쟁을 막는다. 참조 transaction에서 해당 asset 상태를 잠금/검증하고 참조 commit까지 유지한다. 미참조 asset 정리도 같은 잠금 규약에서 참조가0인 ready→deleting 전환을 먼저 commit하고 그 뒤 객체를 삭제한다. deleting 상태에 새 참조를 허용하지 않는다. 보존기간/lease·고아 객체의 재확인 정책을 구현 전에 고정하며, 단순 시간 경과만으로 조회 직후의 자산을 삭제하지 않는다.
- 첫 Windows 설계에서는 작은 양식 원본·로고 등은 DB blob에 함께 저장하는 옵션도 비교한다. 큰 출력 파일/스냅샷은 저장소 adapter로 분리할 수 있다. 무조건 전체 문서를 한 ZIP에 덮어쓰는 저장 방식은 채택하지 않는다.
- 외부 이미지 URL이 원본에 있으면 이관 진단을 남긴다. 출력 시 임의 URL 재다운로드만으로 당시 내용을 재현하지 않는다. 권한/정책을 통과해 확보한 에셋 버전을 snapshot에 고정하는 방안을 적용한다.
- 과거 양식 보기/복원은 과거 업무 데이터 재조회와 다르다. LPS 재출력은 발행 당시 snapshot을 사용하고 최신 query로 번호·내용을 다시 만들지 않는다. 저장소에서 참조 중 revision/asset을 자동 정리하지 않는다.

## 검증 시나리오

| ID | 입력·실패 | 필요한 결과 | 기존 시나리오 |
|---|---|---|---|
| DS01 | 일반/Excel 유래 양식 저장·재열기 | 요소ID·위치·binding·QR 설정·원본 위치 보존 | SC01/02/20 |
| DS02 | A/B가 같은 head를 편집 후 차례 저장 | A만 commit, B는 충돌 안내·내 편집 보존 | SC05/23 |
| DS03 | commit 직후 응답 단절·같은 요청 재전송 | revision1개·같은 결과; 다른payload 재사용 거부 | SC18/23 |
| DS04 | ready 전 자산·누락·변조 | 반쪽 저장/조용한 대체 없음, 기존 head 유지 | SC01/20/23 |
| DS05 | 미지원 schema/요소·마이그레이션 실패 | 원본 보존, 읽기 제한/진단, 자동 덮어쓰기 없음 | SC20/25 |
| DS06 | 긴 숫자문자열/한글·null/빈 데이터/없는필드 | 타입·값·오류 의미 보존; 열기로 query/발급/출력하지 않음 | SC01/03/13 |
| DS07 | 저장 응답 전 추가 편집/모드전환 | 새 편집 dirty 유지, 상태·revision 혼선 없음 | SC01/05 |
| DS07b | A열기 응답보다 늦게 B열기/새 편집이 발생 | 늦은 A 응답을 적용하지 않고 현재 캔버스 보존 | SC01/05 |
| DS08 | 참조 중 과거 revision/asset 정리·복구 | 원발행 재현 유지, 불완전 복구는 명시적 오류 | SC13/22/23 |
| DS09 | 초안 저장/활성화와 진행 인쇄 경합 | 저장만으로 업무 반영 없음, 적용 이후 새 요청만 새 active, 기존 snapshot 고정 | SC04/13/18 |

모두 제품 시험 NOT RUN이다. 별도 fixture 검사는 포맷 초안의 내부 정합성만 점검한다. DB 경쟁·브라우저 응답 경합·프린터/배포 시험은 구현 단계에서 수행한다.
