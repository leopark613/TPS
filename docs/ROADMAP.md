# TPS 소스 구조와 개발 로드맵

2026-11-06. 현재는 **개발 기반 + 양식 저장·재열기 첫 기능**이 구현·검증됐다. 전체 TPS 기능은 앞으로 단계별로 완성한다.

## 한눈에 보는 실행 구조

```text
사용자 브라우저
    React 화면 — RPS 보고서 / LPS 라벨 선택
        ↓ HTTP API
    Spring Boot 한 개 — 양식 검증·저장·버전 처리
        ↓ MyBatis의 SQL
    SQL Server의 TPS DB / dbo
```

React 화면의 빌드 결과를 Spring Boot 실행 JAR 안에 넣는다. 배포하면 하나의 TPS 프로그램으로 실행된다. 별도 프런트 서버나 별도 코어 제품을 만드는 구조가 아니다.

## 실제 소스 폴더

```text
TPS(ThiraReportSystem)
│
├─ server/                         [백엔드: Java / Spring Boot]
│  ├─ src/main/java/com/thira/tps/
│  │  ├─ TpsApplication.java        프로그램 시작점
│  │  ├─ config/                   DB·업무영역 설정, 로컬 인증
│  │  └─ document/                 현재 구현한 공통 양식 기능
│  │     ├─ DocumentController     화면이 호출하는 API
│  │     ├─ DocumentService        저장·재열기·이력·충돌 처리
│  │     ├─ DocumentValidator      양식 구조/지원 기능 검증
│  │     ├─ DocumentPersistence    MyBatis DB 접근
│  │     └─ DTO / Mapper 등        Lombok DTO, MapStruct 매핑
│  ├─ src/main/resources/mapper/   실제 SQL XML
│  ├─ src/main/resources/schema/   문서 JSON 규격
│  └─ src/test/                    백엔드 단위 테스트
│
├─ frontend/                       [프런트엔드: React / TypeScript]
│  ├─ src/App.tsx                  업무영역·목록·속성·저장 이력 화면
│  ├─ src/Canvas.tsx               텍스트·사각형 배치 캔버스
│  ├─ src/document.ts              양식 데이터 편집/지원 판정
│  ├─ src/editor-state.ts          미저장 편집·저장 중 상태 보호
│  ├─ src/api.ts                   백엔드 API 연결
│  ├─ src/styles.css               화면 스타일
│  └─ tests/                       브라우저 경합 시험
│
├─ tests/integration/              실제 DB·브라우저 연결 시험
├─ database/                       현재 문서 저장 A7 생성 SQL
├─ scripts/                        빌드·실행·Eclipse·Windows 포장
├─ ide/                            Eclipse 가져오기/Run·Debug 안내
├─ docs/                           설계·명세·검증·최종 인계 계획
├─ pom.xml + mvnw.cmd              기본 Maven 빌드
├─ build.gradle + gradlew.bat      선택 Gradle 빌드
├─ Jenkinsfile + ci/               회사 CI/Nexus 연결용 준비 설정
└─ dist/                           빌드·시험 배포 결과물(Git 제외)
```

현재 별도 `rps/`, `lps/` 업무 구현 패키지가 모두 만들어진 것은 아니다. 공통 `document` 기능에서 RPS/LPS의 저장 소유와 사용 가능 설정을 구분한다. 리포트 실행, LPS 발행·채번 등 전용 업무 소스는 해당 단계를 구현하면서 같은 TPS 안에 추가한다.

## 지금 끝난 것과 남은 것

| 영역 | 현재 상태 |
|---|---|
| 개발 DB | TPS.dbo 준비. 기존 RPS12개/272행 복사 + 문서 저장7개. 총19테이블 |
| 빌드 | Maven 기본/Gradle 선택, 같은 소스·라이브러리·실행 구성을 검증 |
| 백엔드 첫 기능 | 생성·저장·목록·재열기·버전 이력·중복/동시 저장 보호 구현 |
| 프런트 첫 기능 | RPS/LPS 선택, 텍스트·사각형 편집, 저장·재열기·이력 화면 구현 |
| 시험 | 단위·실제 DB·브라우저·재시작·기존 데이터 보존 검증 통과 |
| Eclipse | JDK21/Lombok 준비 및 실행 설정 제공. 실제 GUI import/Run·Debug는 후속 |
| 보고서·LPS 전체 업무 | 데이터/QR·Excel 가져오기·렌더·프린팅·발행·채번 등을 추가해야 함 |
| Jenkins/Nexus | 연결 설정 준비. 회사 서버에서 실제 빌드·게시를 실행한 상태는 아님 |
| 최종 배포 | Windows 시험 패키지 생성. 다른 사람 PC·실제 프린터 검증은 후속 |

시험용 문서 행은 검증 후 정리했으므로 DB에 데모 문서가 남아 있지는 않다. 기존 RPS 복사 데이터와 원본은 보존했다. 앞으로 새 업무에 필요한 테이블은 기능별로 구조를 검토하며 추가한다.

## 무엇으로 빌드하고 실행하나

**평소에는 Maven으로 빌드하면 된다.** Gradle은 동일 소스를 빌드하는 선택지다. 둘을 순서대로 매번 실행할 필요는 없다.

```powershell
# 소스 루트, JDK21/JAVA_HOME 및 Node24 준비
.\scripts\build.ps1 -BuildTool Maven

# Gradle을 선택하는 경우
.\scripts\build.ps1 -BuildTool Gradle
```

이 PC에서 확인한 Java 소켓 오류에는 선택 옵션 `-WindowsSocketFallback`을 추가한다. 빌드는 프런트 설치·단위 시험·React 생성과 서버 단위 시험·JAR 생성을 함께 수행한다. 결과는 `dist/maven` 또는 `dist/gradle`에 나온다.

개발 실행은 `scripts/run-local.ps1`, Eclipse 실행 준비는 `scripts/open-eclipse.ps1`, Java 포함 Windows 포장은 `scripts/package-windows.ps1`을 사용한다. 자세한 입력·DB 옵션은 루트 README와 Eclipse 안내에 있다. 배포 패키지를 받은 PC에는 IDE·Node·빌드 도구가 필요 없으며, 포함 Java로 실행한다. DB는 별도로 연결한다.

## 앞으로의 구현 순서

```text
[완료] 기반 + 공통 양식 저장·재열기
   ↓
[다음] 일반 보고서: 데이터 조회·필드 연결·QR/바코드·미리보기
   ↓
       Excel 양식 가져오기 → 같은 디자이너에서 수정·데이터 연결
   ↓
       LPS 발행·채번·당시 내용 재출력 + 출력 에이전트 통합
   ↓
       기존 RPS/LPS 기능 대조, 자주 쓰는 디자이너 기능 확장
   ↓
       단위별 연결·통합 시험, 실제 출력, 양쪽 성능/자원 비교
   ↓
       Windows 배포·사용자 선택 패치·다른 PC 시험·최종 검증
   ↓
       설계/매뉴얼 PDF + 전체 참조 인계 패키지 첫 버전 / GitHub 보관
```

이는 다음 구현 계획이며 세부 계약과 필요한 결정을 각 단계에서 정한다. 테스트는 마지막에만 몰아서 하지 않고 단계마다 수행한다. 전체 참조 패키지는 전체1차가 안정된 시점에 첫 버전으로 모으고, 이후 주요 버전별로 갱신한다. 그 사이에도 소스·설계·테스트의 작은 Git 커밋은 계속 남긴다.
