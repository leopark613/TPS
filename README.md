# TPS — Thira Report System

Spring Boot와 React로 만드는 단일 보고서·라벨 솔루션입니다. RPS/LPS는 내부 업무 영역을 구분하고 공통 디자이너를 사용합니다. 별도 코어 제품 분리는 이후 검토합니다.

현재 구현은 **양식 저장·재열기 첫 단계(0.1.0 preview)**입니다. 텍스트·사각형 편집, RPS/LPS별 목록, 저장 버전, 이전 버전 조회, 중복 요청 및 동시 편집 보호를 제공합니다. 저장은 업무 적용과 분리됩니다. Excel 가져오기, 데이터/QR 연결, 보고서 렌더링, 발행·채번·프린팅, 운영 인증과 자동 패치는 후속 범위입니다.

## 개발 PC에서 빌드

JDK 21과 Node.js 24를 설치하고 `JAVA_HOME`을 설정합니다. 별도의 Maven/Gradle 설치는 필요 없습니다. 최초 실행은 공개 의존성 저장소에 접근해야 합니다.

```powershell
.\scripts\build.ps1 -BuildTool Maven
# 동일 소스의 선택 빌드
.\scripts\build.ps1 -BuildTool Gradle
```

두 빌드 모두 프런트엔드 설치·테스트·빌드와 서버 단위 테스트를 수행합니다. 산출물은 `dist/maven` 또는 `dist/gradle`에 생성됩니다. Eclipse 설정은 CLI 빌드에 필요하지 않습니다. 이 PC에서 발견된 Java AF_UNIX 오류에는 `-WindowsSocketFallback`을 선택적으로 추가합니다. 전역 OS/보안 설정을 바꾸지 않습니다.

## 로컬 실행

검토된 TPS DB/dbo의 문서 저장 테이블이 먼저 준비되어 있어야 합니다. 프로그램은 DB를 자동 생성하거나 기존 RPS 데이터를 변경하지 않습니다.

```powershell
.\scripts\run-local.ps1 -Jar .\server\target\tps-server.jar -DbHost <DB_HOST>
```

SQL 계정·비밀번호와 브라우저 비밀번호(12자 이상)를 입력합니다. 비밀번호는 파일에 저장하지 않습니다. `http://127.0.0.1:18760`에서 입력한 브라우저 비밀번호로 연결합니다. 이 preview는 로컬 개발 인증만 제공합니다. 기본 DB TLS는 서버 인증서를 검증합니다. 허용된 로컬 TPS 개발 DB에만 `-LocalCertificateException`을 선택할 수 있습니다.

Java까지 포함한 Windows 배포본 생성:

```powershell
.\scripts\package-windows.ps1 -BuildTool Maven
```

생성된 ZIP에는 실행 JAR·React 화면·Java 런타임·실행 스크립트가 포함됩니다. 실행 PC에는 Eclipse/Node/Maven/Gradle이 필요 없습니다. SQL Server 연결은 필요하며, 다른 PC에서의 실제 설치 검증은 아직 남아 있습니다.

## Eclipse·CI·검증·재개

- [Eclipse 가져오기 및 Run/Debug](ide/ECLIPSE-SETUP.md)
- [백엔드·프런트 구조와 개발 로드맵](docs/ROADMAP.md)
- [설계 및 구현 범위](docs/ARCHITECTURE.md)
- [검증 결과와 남은 범위](docs/VERIFICATION.md)
- [Jenkins/Nexus 연결 준비](ci/README.md)
- [다른 PC·에이전트 인계 패키지 계획](docs/PORTABLE-HANDOFF.md)
- [현재 구현 계약](docs/spec/TPS-IMPL-001.md)

GitHub push와 실제 Jenkins/Nexus 게시를 아직 실행하지 않았습니다. 기존 회사 CI나 서버 설정을 변경하지 않았습니다. 상세 조사·이력의 정본은 사용자가 지정한 TPS 기록 폴더이며, 전체 인계 패키지는 전체 1차 구현·테스트 완료 후 작성합니다.
