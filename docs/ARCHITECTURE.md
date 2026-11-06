# TPS 저장·재열기 구현 구조

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
