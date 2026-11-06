# TPS-IMPL-001 — 양식 저장·재열기 첫 구현

2026-11-06. 사용자가 로컬 TPS DB 기준 Spring Boot/React 구현·테스트를 지시했다. 이 작은 구현 범위의 수용 계약이다. 전체 디자이너/Excel 변환/렌더/출력 완료가 아니다.

## 범위와 기술

- Java21 / Maven3.9.16 / Spring Boot4.1.2 / MyBatis starter4.1.0 / Lombok1.18.48 / MapStruct1.6.3 / networknt JSON Schema3.0.9 (Jackson3).
- React19.3.1 / TypeScript7.0.2 / Vite8.3.6 / plugin-react6.2.1 / Vitest5.1.0. Node24.22.1.
- 코드: 사용자 지정 TPS Git 저장소. 원본 기록 정본은 기존 TPS 폴더. 원격 origin=https://github.com/leopark613/TPS.git, branch=codex/template-save-reopen. 현재는 빈 원격을 clone. push는 검증·비밀/원문 제외 점검 뒤 후속 수행하며 이번 즉시 공개로 해석하지 않는다.
- 하나의 Spring Boot 실행 모듈(server), 내부 document/controller/service/persistence/model/mapper 경계, 별도 frontend. 불필요한 공통 코어/서버 분리 없음. MapStruct는 module내 DTO 매핑, DTO에는 Lombok 필수.
- 로컬 SQL Server TPS.dbo의 기존 A7만 사용. 이 slice는 TEMPLATE/REVISION/REQUEST를 쓰며 ACTIVE 포인터/ACTIVATION은 변경하지 않는다. RPS12/원격MES/번호/프린터 접근 없음. 새 DDL 자동실행 없음.

## 사용자 기능

빈 문서 생성 → 이름/문구 편집 및 기본 텍스트·사각형 배치 → 명시 저장 → 목록 → 브라우저 재로드/새 세션에서 다시 열기. RPS/LPS 모드는 같은 편집기를 사용하고 소유는 생성시 고정된다. 과거 revision은 읽기 전용으로 조회한다. 저장이 업무 적용을 뜻하지 않으며 '업무 미적용'을 표시한다. 업무적용/restore/Excel import/asset upload/QR 출력/데이터조회/printing API는 이번에 제공하지 않는다.

기존 0.1.0-draft schema의 document envelope를 그대로 사용한다. 정상 첫 fixture는 native text와 assets=[]이며 원래합성예제의 가짜font asset을실파일로취급하지않는다. 문서의 unknown properties/bindings/children/extensions를 조용히 버리지 않는다. 기본편집기의 지원범위 밖 내용은 진단과 읽기전용으로 표시한다.

## API 계약 (두 구현이 동일하게 사용)

Base /api/tps. site는 서버 TPS_SITE_ID 설정(기본 TPS_DEV), actor는 인증 principal이다. 요청 site/actor를신뢰하지않는다. 로컬개발용 인증만: server bind127.0.0.1, SpringSecurity HTTPBasic, username=tps-developer, 비밀번호 TPS_DEV_PASSWORD 필수/기본없음. React 연결화면에서입력해메모리에만보관. localStorage/sessionStorage/코드/URL에암호저장금지. 개발모드가아닌운영기동은준비전실패하도록명시한다. 운영SSO/권한완료표기금지. 변경API는 application/json+고유헤더를사용하고외부origin허용안함. API정보노출/로그에문서/비밀출력금지.

| API | 계약 |
|---|---|
| GET /session | 인증후 {actorId,siteId,enabledModules:[RPS,LPS]} |
| GET /templates?module=RPS&page=0&size=50 | {items:TemplateSummary[],page,size,total}; size1..100 |
| POST /templates | header Idempotency-Key(uuid), body {clientDraftId:uuid,ownerModule:RPS\|LPS,document}; 201 TemplateView |
| GET /templates/{id}/head | 200 TemplateView, ETag=headEtag |
| PUT /templates/{id}/head | header If-Match+Idempotency-Key, body {document}; 200 TemplateView |
| GET /templates/{id}/revisions | {items:RevisionSummary[]} 최신순, 상한100; 필요하면후속페이지화 |
| GET /templates/{id}/revisions/{revisionId} | 200 TemplateView, editable=false; 수정불가 |
| POST /save-requests/resolve | body {operation:CREATE\|SAVE,scopeId:uuid,requestKey:uuid}; {status:completed\|unknown,result?:TemplateView} |

TemplateSummary={templateId,name,ownerModule,headRevisionId,headToken,revisionNo,activeRevisionId,createdAt,lastSavedAt}. bigint headToken/revisionNo는십진문자열. activeRevisionId는null.
TemplateView=TemplateSummary+{revisionId,sequence,bodyHash,headEtag,document,diagnostics,editable}. sequence도십진문자열. 최신뷰headEtag는강한따옴표문자열 `"h:<templateId>:<headRevisionId>:<headToken>"`. 과거뷰는headEtag=null, revisionId/sequence/본문/hash는해당과거버전. RevisionSummary={revisionId,sequence,bodyHash,createdAt,createdBy}.
Diagnostic={severity:WARNING\|ERROR,code,path,message}. 오류={code,message,diagnostics?:[],currentHeadEtag?:string}; 문서/DB원문오류를echo하지않는다. 400 malformed조건/JSON,401인증,403비활성모듈,404없는양식/버전,409requestkey재사용,412stalehead,413크기초과,422문서검증,428IfMatch누락.

## 저장 알고리즘

Idempotency-Key는 UUID 문자열. CREATE scope=CLIENT_DRAFT/clientDraftId, SAVE scope=TEMPLATE/templateId. 실제 UQ(site,actor,operation,scopekind,scopeid,key)를사용한다. 요청범위잠금→template잠금 순서고정. transaction 안에서동일receipt를먼저찾아동일fingerprint완료결과를재반환하고다른내용은409. CAS보다receipt결과확인이먼저다. 동시동일요청/DBdeadlock·UQ경쟁은제한재시도후완료결과확인, 무조건500/중복revision금지.

신규는 template(nullhead)→immutable revision1→head/token1→완료receipt를동일transaction으로commit. 수정은조건비교→새revision→head/name/token CAS→완료receipt 동일transaction. 실패시부분행/고아revision없음. 저장은active를바꾸지않음. HEAD_TOKEN/REVISION_NO overflow는거부. owner immutable. 조회는정확한revision ID를고정후hash검증.

본문 hash/fingerprint 계약=tps-canonical-json-v1. 객체키를Java String사전순으로재귀정렬, 배열순서유지, compact JSON UTF8, SHA256소문자hex. 이slice에서는숫자는JS safe integer범위의정수만허용; 소수값은fontSizePt처럼문자열로전달. 1과1.0은동일정수로정규화. 모호한중복JSON키/비유한수/잘못된UTF8/깊이초과거부. fingerprint는operation/scope/owner(해당시)/기준ETag/본문canonical을포함. 원본body/properties/문자열을임의trim/lowercase하지않는다. receipt자동TTL삭제없음.

## 검증·제한

JSON요청최대1MiB, 깊이64, 전체요소최대500, title1..200UTF16units, site최대40UTF8bytes, actor최대100UTF16units. schema202012구조검증+문서내ID유일성/dataset/field/provenance참조 의미검증. assets가있으면이번에는422 ASSET_UPLOAD_NOT_AVAILABLE(통과/무시금지). 알려지지않은element/기능버전은원본보존+진단, editable=false. 원격query/asset URL조회없음.

기본편집가능범위는1section/1detail band/childBands=[]/element children=[]/bindings=[]/datasets=[]/assets=[]의 tps.text:1 및 tps.rectangle:1. properties의text/fontSizePt/color/fill/stroke 외값도복사보존. 범위밖문서는읽기전용. 기초캔버스는정밀인쇄renderer가아니다.

## React 상태

새양식열기로DBINSERT금지. 저장당시editGeneration과requestKey/payload를보존, 저장중추가편집은응답후dirty유지. 실패/412는현재편집보존. 결과불명은같은requestKey/payload 재시도 또는resolve, 다른내용으로키재사용금지. loadGeneration+id검사로늦은이전양식응답무시. dirty상태의모드/문서전환은비차단확인영역에서사용자선택, beforeunload경고. 원문JSON고급보기는읽기전용우선. UI의사용자문구에SQL/PK/CAS같은불필요구현용어표시금지.

## 검증과 완료 기준

Unit: canonical/hash·duplicatekeys·schema/ID/ref·안전숫자·권한모듈·ETag.
SQLServer integration: 실제localTPS신규시험site만사용, CRUD재열기/history/hash/activeNULL, stale412, 동일요청순차·동시재시도, key다른본문409, concurrentCAS승자1개, 검증실패시행불변. 시험fixture는고유site TPS_IT_<run>와등록한ID만쓸것. 시험후해당run의소유행만정확한관계순서로정리하는테스트전용cleanup허용; 기존RPS12/사용자양식무변경. cleanup실패는보고하며임의광역삭제금지.
UI: 실제React+SpringBoot+localSQL로새양식/편집/저장/목록/새세션재열기/버전보기/미저장편집보존, 응답경합은지연모의시험도별도. screenshot·console오류확인. localServer재시작후저장본재열기. 데모문서최대2개 TPS_DEMO_로식별해남기고ID기록.
Build: npm unit/build, Maven unit/package+SQLintegration, 단일JAR에빌드React포함, secret비포함. Eclipse import/run 안내와runtime기동script. 제품전체PDF작성gate아직아님. 독립코드검토후필요수정/재검증. canonical기록+repo안공개가능설계/spec/test요약만동기화. DB원문/자격증명/역컴파일3자소스/_ref/로컬증거덤프는Git에넣지않는다.

## D053 — 단일 TPS 제품·선택형 빌드 및 CI 연결 (2026-11-06)
사용자는 Maven 기본 구성으로 계속하며 Nexus/Jenkins 연결 구조를 준비하되 Gradle도 선택해 같은 소스를 빌드할 수 있도록 요청했다. 이어 TPS 자체를 하나의 솔루션으로 만들고 코어 분리는 추후 별도 분석한다고 명시했다. 서버/프런트 소스 디렉터리는 빌드 경계일 뿐 별도 제품·코어 저장소·실행 서버가 아니다. 하나의 Spring Boot 실행 패키지에 React 결과물을 포함하며 RPS/LPS는 내부 업무 모듈로 유지한다.
Jenkinsfile·Nexus 자격증명 주입/게시 설정을 저장소에 준비한다. 기존 회사 서버 설정 변경·실제 artifact 게시·배포 재시작은 이번 구조 준비로 실행하지 않는다. 개발 PC 독립 빌드와 배포 패키지 독립 실행을 유지한다. 현재 SQL Server TPS 연결은 유지하며 내장 웹서버가 DB 내장을 뜻하지 않는다. Maven/Gradle의 의존성·테스트·패키지 구성 차이를 검증한다.
