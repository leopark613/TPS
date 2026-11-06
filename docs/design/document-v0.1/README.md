# TPS 문서 형식 v0.1

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

2026-11-06 / 구현 전 설계안. D045의 원본 보존·RPS→TPS 이관 후 TPS 사용을 전제로 한다. 이 문서의 `0.1.0-draft`는 검토용 포맷이며 제품의 호환 보장 버전이 아니다. 기존 전체 기능을 이 예제의 텍스트/QR로 축소하지 않는다.

## 설계 선택

문서 본문은 UTF-8 JSON으로 제안한다. React의 편집 상태나 특정 출력 엔진의 JRXML/HTML을 그대로 영구 저장하지 않는다. Spring Boot에서 같은 문서 의미를 검증하고 renderer adapter로 변환한다. JSON을 택해도 기존 XMLDATA에 곧바로 저장한다는 뜻은 아니다. [물리 저장 선택](storage-options.md)은 별도 결정이다.

| 대상 | 의미와 내용 | 본문과 구분할 이유 |
|---|---|---|
| Template | site별 양식 식별, 소유 모듈(RPS/LPS), 표시 이름·업무 분류, head revision, nullable active revision | 접근 권한·동시 저장·업무 적용 관리 |
| DocumentRevision | 확정된 본문 바이트·hash, 이전 revision, 작성자·시각·이유 | 저장본 불변; 구버전 덮어쓰지 않음 |
| DocumentBody | 용지·배치·요소·바인딩·에셋/데이터 정의 참조·버전 | 렌더러와 독립된 편집 가능한 의미 |
| ImportRecord | 원본 blob/hash, 원본키/site, 변환기 버전, 원본 위치→요소 대응과 진단 | 원본 보존·변환 추적·재시도 |
| RenderSnapshot | 정확한 문서 revision, 데이터 값·query revision·에셋·폰트·renderer/profile 식별 | 현재 데이터와 발행 당시 결과를 분리 |

DB용 ID·작성자·site 권한은 서버가 관리한다. 본문에 넣은 값만으로 권한을 판단하지 않는다. 문서에는 DB 암호·접속문자열·임의 SQL·발급번호 할당 상태·스풀 상태·실제 개인정보 표본을 넣지 않는다. 쿼리 관리 UI와 배포 registry(D036)는 별도 기능으로 유지한다.

공통 문서 테이블/서비스를 공유해도 RPS/LPS 양식은 기본적으로 서로 다른 소유·ID·버전·active를 가진다. 한쪽 저장/적용이 다른 모드의 양식을 바꾸지 않는다. 다른 모드에서 재사용할 때는 명시적 복사로 새 양식을 만들고 불변 asset만 정책에 따라 공유한다. 모드 비활성·권한 제한은 UI뿐 아니라 서버 API에서도 검사한다. 소유 모듈은 문서 body가 아닌 서버 envelope에 두고 임의 변경하지 않는다.

## 본문 구조

| 필드 | 계약 |
|---|---|
| format / schemaVersion | `tps.document` / 정확한 schema 버전. 처음 draft는 `0.1.0-draft` |
| units | 위치·크기·여백·선 두께의 기준은 정수 µm(`um`). 화면 확대율·프린터 DPI와 분리 |
| title / locale / timeZone | 사람이 보는 이름과 표시 규칙. query 권한이나 실행 환경의 암묵 기본값으로 쓰지 않음 |
| requiredFeatures | 문서를 해석하는 데 필요한 기능 ID와 정확한 기능 계약 버전 |
| sections[] | 순서 있는 용지 구획. 용지 크기·여백, 순서 있는 bands[] |
| bands[] | 고정 영역/상세 반복/머리말·꼬리말/그룹 등. id, kind, height, data scope, elements[], childBands[] |
| elements[] | id, type, typeVersion, frame, properties, bindings, children. 배열 순서를 그리기 순서로 사용 |
| datasets[] | 내부 alias, 승인된 query 계약·고정 버전 참조, result-set ordinal, field ID/path/type |
| assets[] | 내부 asset ID·sha256·매체형·크기·저장 객체 참조. URL·로컬 경로 대신 검증된 참조 |
| provenance | native/RPS XML/Excel origin, ImportRecord 참조, 원본 hash와 위치 매핑 |
| extensions | 네임스페이스별 보존 공간. 실행 가능한 코드·임의 새 속성 허용 통로가 아님 |

예제는 [일반 보고서](examples/report.json), [Excel 유래 보고서](examples/excel.json)다. 둘 다 같은 데이터 필드→텍스트/QR 연결을 표현한다. 예제의 ID·query는 합성값이며 실제 DB를 조회하지 않는다.

## 정밀도·참조·확장

- 1mm=1000µm, 기존 RPS 1/100inch=254µm. 원본→µm 변환에서 한 번만 반올림하고 원본 단위/값과 변환 규칙은 ImportRecord에 보존한다. µm 저장이 실제 프린터 오차를 보장하지 않는다. 글꼴 크기는 pt 단위 십진 문자열로 분리한다.
- 좌표 원점은 용지 좌상단이다. band 안 요소는 band 콘텐츠 좌상단, 자식 요소는 부모 콘텐츠 좌상단 기준이며 축/변환 규칙을 타입 계약에 둔다. 용지 여백은 renderer가 한 번 적용한다. 밖으로 나가는 요소를 조용히 삭제하지 않는다.
- element/band/section ID는 문서 안에서 유일하고 저장·재열기·undo 동안 안정적이다. 다른 이름으로 저장은 새 Template ID를 얻는다. 요소 ID는 문서 범위이므로 복제본에 유지 가능하되 외부 참조는 template/revision과 함께 식별한다.
- 데이터 결과 순서0/1을 원본 adapter에서 보존한다. 표시 이름과 field ID/path를 구분하고 `000012`는 문자열로 유지한다. null·필드 없음·조회 실패·식 평가 실패는 다른 상태다.
- 바인딩은 target property + field 또는 버전 있는 표현식 AST 참조 + 평가 scope를 가진다. 블록 UI와 고급 식은 같은 의미 모델을 사용한다. 샘플은 field 바인딩만 구현 가능한 구조를 보여주며 식 parser는 미구현이다.
- 표의 병합/RowSpan·열 비율·남는 폭 배분은 테이블 구조 속성으로 보존한다. 계산된 사각형만 저장해 표 의미를 잃지 않는다. 동일 속성의 여러 식은 이벤트·평가 순서 계약을 가진다. QR 설정은 원본의 생략/명시 여부와 해석된 유효값·기본값 계약 버전을 구분해 보존하고 새 라이브러리 기본값으로 조용히 바꾸지 않는다.
- 테이블·병합·서브리포트·차트·QR 옵션은 type/version별 속성 schema와 의미 검사로 정의한다. 이 초안의 properties가 object라고 아무 옵션이나 출력 가능하다고 판정하지 않는다. 각 확장에는 editor/importer/validator/renderer 지원표가 필요하다.
- [schema.json](schema.json)은 외곽 구조만 검증한다. element ID 유일성·참조 존재·scope·중첩 순환·부모 제한·단위 계산·활성 query/asset 권한·타입별 속성은 별도 의미 검증이다. JSON Schema의 구조 검증과 출력 가능 판정을 합치지 않는다. [JSON Schema 2020-12](https://json-schema.org/draft/2020-12)

## 버전과 미지원 문서

앱 버전, schemaVersion, element typeVersion, 저장 revision, query revision은 다르다. 신버전을 구버전 앱으로 열면 지원 여부를 먼저 확인하고, 미지원이면 읽기 전용 진단 또는 열기 거절을 반환한다. 원본 JSON/XML은 그대로 보존하고 구버전 형식으로 자동 덮어쓰지 않는다. PATCH/PUT 요청에서 모르는 필드를 조용히 버리지 않는다.

schema migration은 기존 저장본을 고치는 UPDATE가 아니라 검증 가능한 새 revision 생성이다. 원본 hash·migrator 버전·변경/손실 보고를 남긴다. 같은 변환을 다시 수행해도 요청 중복으로 revision을 늘리지 않는 계약은 [저장·재열기](save-reopen.md)를 따른다. unknown 요소는 opaque 원본 참조로 보존하되 renderer가 해당 기능을 모르면 출력 가능으로 처리하지 않는다.

## 다음 상세화

전체 컨트롤별 속성 schema, 표현식 문법/타입, 합계·페이지 시점, 정량 크기/복잡도 제한과 font 패키지 정책은 후속이다. 이 초안·fixture 검사는 JSON 계약의 점검이며 Spring Boot/React 저장·렌더 시험을 대체하지 않는다.
