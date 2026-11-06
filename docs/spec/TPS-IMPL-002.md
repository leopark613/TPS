# TPS-IMPL-002 — 일반 보고서 데이터 연결과 QR 미리보기

2026-11-06. 사용자의 다음 구현 지시에 따른 작은 수용 단위. 상태: 구현·독립 검토·로컬 DB/브라우저 검증 통과. 결과는 docs/VERIFICATION.md 및 정본 verification/TPS-IMPL-002 참조. 전체 제품 완료와 구분한다.

## 범위
빈 일반 보고서에 텍스트·사각형·QR을 배치하고 문자열 필드를 텍스트/QR에 연결한다. 저장한 정확한 버전을 재열어, 사용자가 선택한 한 데이터 행으로 서버가 해석한 한 페이지를 미리 본다. Excel이 선행 조건이 아니다. 이번은 데이터 미리보기이며 PDF·페이지 반복/분할·인쇄 완료가 아니다.

하나의 WAS, 기존 document 저장 책임 유지. rps.viewer.GeneralViewerService의 bindDataSource/preview 명칭을 기존 실제 서비스에서 이어 쓴다. HTTP controller와 DTO는 신규 경계라고 기록한다. 공유 binding/QR 엔진은 내부 Java 호출이며 별도 코어 제품이 아니다. LPS 채번·업무 흐름을 이 구현에 섞지 않는다. DTO Lombok; MyBatis SELECT; 필요 변환은 MapStruct.

## 데이터와 저장 안전
새 DDL 0. 기존 DOC7 중 TEMPLATE/REVISION/REQUEST만 시험 저장. 활성화 포인터·RPS12·프린터·원격 MES는 변경/호출하지 않는다.
고정 서버 등록 queryRef={id:"ReportList",classId:"TPS_LOCAL_SAMPLE",version:"1"}, ordinal=0. UI 표시명 "로컬 보고서 목록 예제". 실제 MES 업무 쿼리나 기존 StoredQuery 실행을 뜻하지 않는다. 필드: reportId, reportName, reportClassId (모두 type=string, path=[동일 ID], classId는 null 가능). XMLDATA/MESSAGESET/연결정보는 읽거나 반환하지 않는다.
MyBatis 고정 SQL: SELECT TOP(101) REPORTID AS reportId, REPORTNAME AS reportName, REPORTCLASSID AS reportClassId FROM dbo.RPS_REPORT WHERE SITEID=#{sourceSiteId} ORDER BY REPORTID. 선택행 조회도 SITEID+REPORTID로 제한한다. timeout=5. SQL 문자열, URL, mapper 경로는 요청으로 받지 않는다.
sourceSiteId는 서버 siteId와 같음이 기본. 명시적인 선택 설정 TPS_RPS_REFERENCE_SITE_ID가 있으면 현재 로컬 TPS site 하나에서 지정한 복제 원본 site 하나의 보고서 메타데이터를 읽도록 허용한다. 이 설정은 신뢰된 서버 환경에만 두고 API 요청으로 변경할 수 없다. 환경에서 유효한 1..40 UTF8 bytes를 검증한다. 매핑값/업무행은 테스트 결과에 출력하지 않는다. 쿼리 site 조건을 결과가 없다는 이유로 제거하지 않는다.
문서 JSON에는 dataset 정의와 binding만 저장한다. 조회 결과, QR matrix, 화면 선택행은 문서에 넣지 않는다. 미저장 내용은 미리보기를 위해 자동 저장하지 않는다.

## API 계약
기존 인증·same-origin·크기 제한을 따른다. module은 소유 확인 및 서버 활성화 설정으로 검증. 이 예제 query는 RPS용, LPS에 연결 시 명시 거부. 공통 QR 정적 요소는 두 영역의 저장 모델에서 지원 가능하다.

GET /api/tps/rps/ReportList
응답 {dataset:Dataset, rows:[{reportId:string,reportName:string,reportClassId:string|null}], truncated:boolean, label:string}. Dataset.id="rps-report-list", fields위 계약, queryRef위 계약. 최대100개; 응답은 최대100개이며 truncated는101번째 행 존재로 정확히 판정한다. 프런트가 실제 row값을 문서에 복사 저장하지 않는다.

POST /api/tps/rps/Preview
본문은 정확히 {templateId:uuid,revisionId:uuid,reportId:string|null}. 서버 DocumentService.historical로 site/owner/module/hash 검증한 immutable revision을 사용한다. RPS 소유만 허용. 데이터가 있는 경우 reportId 필수(최대40 UTF8 bytes), 현재 sourceSite의 행을 재조회. dataset이 없으면 reportId=null로 정적 요소 미리보기 가능.
응답:
{templateId,revisionId,bodyHash,queryRef:QueryRef|null,executedAt:ISO UTC,reportId:string|null,resultHash:string,units:"um",page:Page,elements:PreviewElement[],diagnostics:Diagnostic[]}
PreviewElement={id,type,frame,properties,qr?:{matrix:string[],moduleCount:number,quietZoneModules:4,version:number,errorCorrection:"M",encoding:"UTF-8"}}.
matrix는 검정1/흰색0 문자 행 배열이며 quiet zone 제외. moduleCount는matrix크기. 프런트는 외곽4모듈을 흰색으로 포함해 정사각형 QR을 그린다. properties에는 서버가 해석한 text/payload를 포함한다. 기존 프런트와 같은 여백 기준 frame. resultHash는 선택한 필드값의 canonical JSON hash(정적은 빈object). 데이터는 로그에 출력하지 않는다.
응답 성공시 전체 요소를 표시해야 한다. 미지원 요소/표현식은 오류422, 누락 후 성공으로 처리하지 않는다. 데이터없음404 REPORT_DATA_NOT_FOUND, 지원하지 않는 query tuple422 QUERY_NOT_SUPPORTED, 데이터조회 실패503 REPORT_DATA_UNAVAILABLE(원문SQL오류 미노출), QR빈값422 QR_PAYLOAD_EMPTY/용량초과422 QR_CAPACITY_EXCEEDED 등 구별.

## 문서 지원 계약
기존 schema 유지. 1section/1detail band/회전없음/자식없음/assets없음/용지<=2m. Dataset은 없거나 위 계약과 정확히 일치하는1개, band.datasetId는 해당ID 또는 생략(바인딩이 있으면 해당dataset을 사용). 새 requiredFeatures는 tps.text:1,tps.rectangle:1,tps.qr:1만 지원.
문자열 field → tps.text의 text 또는 tps.qr의 payload. source.kind=field, evaluation={scope:"row",event:"beforePrint"}, nullPolicy=error|empty. 같은 element.property 중복 연결은422. field/type/path/query버전/ordinal 불일치는 편집지원 범위 밖 또는 명시422이며 미리보기에서는 반드시 거부. 기존 unknown내용은 저장 보존+읽기전용 원칙 유지. literal정책과 expression은 이번 실행 미지원.
QR 기본properties={symbology:"QR",encoding:"UTF-8",errorCorrection:"M",quietZoneModules:4,versionMode:"auto",defaultsContract:"tps.qr:1",payload:"TPS"}. 이 설정 외 QR 동작은 미지원 진단. 빈/null payload를 정상 QR처럼 보이지 않는다. 문자열 앞뒤 공백/선행0/한글/줄바꿈 보존. UTF8최대용량 초과 명시거부. ZXing core3.5.4 adapter, Apache2.0 고지, Maven/Gradle동일. 기존 DevExpress L기본값과 같다고 주장하지 않는다.

기존 저장·편집 호환 보충: 알려지지 않은 element.properties는 첫 구현처럼 그대로 보존하며 기존 편집 가능 판정을 갑자기 제한하지 않는다. 미리보기는 별도의 엄격한 지원 판정을 하여 알 수 없는 표현 속성이 있으면 UNSUPPORTED_PREVIEW_PROPERTY로 거부한다. root extensions는 실행하지 않는 불투명 메타데이터다. 따라서 편집 가능과 미리보기 가능은 같은 조건이 아니다. 새 QR 옵션/쿼리/바인딩의 의미가 지원 범위를 벗어나면 읽기 전용 원칙을 적용한다.

미리보기 상세 한도: 글자 크기4..200pt, 지원 색상, 용지와 detail 영역 내부 배치, 정사각형 QR을 검증한다. QR UTF8 입력의 방어 한도는10,000bytes이며 실제 허용 용량은 ZXing 인코딩 결과로 판정한다(숫자4000자 roundtrip 포함). 응답 전체 QR matrix는2,000,000셀 이하로 제한한다.

## React 흐름
RPS에서 '데이터 연결'으로 목록 예제를 불러오고 연결, 요소 속성에서 '직접 입력/보고서ID/보고서명/분류ID' 선택. 데이터값은 디자이너 배치뷰에서 placeholder로 표시하고 실제 결과는 '저장본 미리보기'로 구분한다. QR배치뷰는 QR자리임을 나타내며 가짜 QR을 실제처럼 표시하지 않는다.
미리보기는 dirty/pending/loading중 비활성, 명시 저장 안내. 과거버전은 읽기전용 상태로 미리보기 가능. 보고서선택/문서/revision/편집 변경마다 이전미리보기 무효화. 요청세대검사로 늦은응답 무시. 실패시 이전QR을 현재결과로남기지 않는다. 화면차단모달 없이 별도영역/탭. 사용자용문구는 구현용어를 최소화한다.

## 수용시험
Java: 기존저장회귀, 바인딩계약/누락/중복/유형/스코프/null/모듈/site/query버전/unsupported, QR 생성→decoder판독원문비교(한글/선행0/구분자/줄바꿈), capacity/empty.
React: 지원판정Java일치, 필드연결/해제/직접입력, 선택행변경/늦은응답/실패의 stale QR방지, 저장중편집회귀.
실제 localTPS API+브라우저: 조회→일반보고서text+QR연결→저장→새브라우저재열기→정확revision미리보기→QR해독. 시험양식 고유TPS_IT site/등록IDs만 저장·정리. RPS12/기존DOC/DB구조/identity 전후불변, activeNULL, 출력0. 업무값을증거에노출하지않고합성문구스크린샷사용.
Maven/Gradle unit+빌드 및 런타임의존성/소스동일성 검사. 독립검토 후 실제검증결과/실패/미완료를 기록. 기존 데이터확인 과정에서 부족한 업무테이블/추가복사 필요시 사용자결정 질문을 별도로 남긴다.

## 출처
ZXing 공식 릴리스 https://github.com/zxing/zxing/releases 및 공식 Maven 정보 https://zxing.github.io/zxing/dependency-info.html (2026-11-06 확인). 전체 renderer선택/성능비교는 아직미확정. 관리자의 SQL수정·저장·시험실행 계약(D036)은 후속범위로유지한다.
