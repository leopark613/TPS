# TPS 저장·재열기 및 일반 보고서 데이터 미리보기 구조

## TPS-IMPL-002 추가 구조

2026-11-06. 다음 구조를 구현했으며 검증 결과는 VERIFICATION.md 및 각 구현 단위의 증거를 구분해서 본다.

```text
React (frontend/src)
  App / Canvas                양식 편집·저장·이력
  ReportPanel / report         RPS 데이터 연결·선택행·저장본 미리보기
  api                         같은 WAS의 JSON API 호출
              |
Spring Boot (server/src/main/java/com/thira/tps)
  document                    공통 문서·불변 버전·동시 저장 보호
  rps/viewer
    ReportViewerController    신규 HTTP 경계: ReportList / Preview
    GeneralViewerService      기존 RPS 서비스명 계승: bindDataSource / preview
    ReportQuery               MyBatis 고정 보고서 메타데이터 조회
    QrEncoder                 ZXing QR 생성 어댑터
  config                      인증·현재 site·RPS/LPS 활성화·명시 원본 site 매핑
              |
SQL Server TPS.dbo
  TPS_DOC_*                   정의와 바인딩만 저장, 조회 결과는 저장하지 않음
  RPS_REPORT                  복제된 기존 보고서 목록의 3개 필드만 읽음
```

`document`가 모든 API를 담는다는 뜻은 아니다. RPS 조회·미리보기는 `rps.viewer`로 분리했다. LPS 발행·채번은 실제 원본 업무 이름을 확인한 `lps` 패키지에 후속 구현한다. 현재 LPS 채번 패키지가 구현됐다는 뜻은 아니다. 명칭 근거와 변경 이유는 LEGACY-NAME-MAPPING.md에 기록한다.

이번 일반 보고서 기능은 한 데이터 행을 텍스트·QR에 연결하고, 정확한 저장 버전을 다시 읽어 서버에서 값을 해석한다. React는 서버 결과를 그린다. 미저장 내용은 자동 저장하지 않으며 편집·행·버전이 바뀌면 이전 미리보기를 지운다. QR은 UTF-8/M/자동 버전/4모듈 여백이라는 TPS 기본 계약이며 기존 DevExpress 기본값과 같다고 주장하지 않는다.

예제 쿼리는 고정 MyBatis `ReportList / TPS_LOCAL_SAMPLE / 1`이다. 기존 STOREDQUERY나 MES 업무 SQL은 실행하지 않는다. 원본 site는 현재 site가 기본이며 `TPS_RPS_REFERENCE_SITE_ID`를 명시한 프로세스에만 복제된 다른 site 하나의 메타데이터를 허용한다. API가 site·SQL·JDBC 주소를 결정할 수 없다. 새 DB 테이블은 필요 없다.

편집·저장은 알 수 없는 기존 속성을 보존한다. 미리보기는 표현할 수 없는 속성을 오류로 알리므로 일부 요소가 빠진 성공 결과를 내지 않는다. `extensions`는 실행하지 않는 메타데이터다. 실제 PDF, 페이지 반복·분할, 프린터 송신, LPS 번호 발급, 원본 DevExpress 출력과의 동등성은 이 단위의 완료 범위 밖이다.

## TPS-IMPL-001 기반 구조 (이력)

2026-11-06, TPS-IMPL-001. 이 문서는 구현된 범위와 후속 범위를 구분한다.

```text
브라우저 React
  ├─ RPS 보고서 / LPS 라벨 작업 영역
  └─ 공통 양식 디자이너·목록·저장 이력
             │ 같은 출처의 JSON API
Spring Boot 한 개 (내장 Tomcat)
  ├─ 로컬 인증·설정·모듈 사용 제한
  └─ document: controller → service → MyBatis XML
             │ TLS JDBC
SQL Server TPS.dbo
  ├─ TPS_DOC_TEMPLATE : 업무 소유와 현재 편집본 포인터
  ├─ TPS_DOC_REVISION : 변경하지 않는 저장 버전
  ├─ TPS_DOC_REQUEST  : 중복 요청 결과
  ├─ TPS_DOC_ASSET / REVISION_ASSET / IMPORT / ACTIVATION : 준비된 후속 저장 구조
  └─ RPS_ 12개 : 기존 구조·272행 복사 기준선, 제품은 이번에 변경하지 않음
```

문서 형식은 `tps.document / 0.1.0-draft`, 길이 단위는 μm 정수다. 텍스트와 사각형을 편집하고 SHA-256으로 저장·조회 무결성을 확인한다. 문서 스키마와 지원 기능을 구분하며 미지원 속성·요소를 조용히 버리지 않는다. 이번 단계에는 파일 업로드가 없으므로 asset을 가진 새 저장 요청은 명시적으로 거부한다.

저장은 template, immutable revision, head token, idempotency receipt를 하나의 DB 트랜잭션으로 처리한다. 같은 요청을 다시 보내면 같은 저장 결과를 반환하고, 변경된 내용으로 같은 키를 재사용하면 충돌을 반환한다. If-Match가 현재 편집 버전과 다르면 다른 사람의 저장을 덮지 않는다. 저장 중 사용자가 추가 편집한 내용도 화면에 유지한다.

사이트·작성자는 서버 설정과 인증에서 결정한다. RPS/LPS 소유는 생성 시 고정되며 활성화 설정에 따라 API도 제한한다. 별도 BFF 서버나 내부 Feign 호출은 없다. 기능별 화면 API를 같은 WAS 안에서 제공한다.

React 빌드 결과를 실행 JAR의 static 리소스로 묶는다. Maven3.9.16 / Gradle9.8.2 래퍼, Java21, Boot4.1.2, MyBatis4.1.0, Lombok1.18.48, MapStruct1.6.3을 사용한다. Gradle에도 Boot BOM을 강제해 Maven과 의존성을 맞췄다. 빌드 도구가 제품 실행 성능을 개선한다는 주장은 하지 않는다.

기존 DevExpress/C#/Vue와의 기능·성능 비교는 전체 렌더/출력 기능이 준비된 후 같은 조건에서 실제 측정한다. 이번 저장·재열기 검증은 디자이너 전체, Excel 이관, QR/바코드 렌더링, LPS 발행·채번·재출력, 물리 프린팅, 운영 인증, 패치, 다른 PC 설치의 완료를 뜻하지 않는다.
