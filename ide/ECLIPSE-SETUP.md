# TPS 소스를 Eclipse에서 실행하기

TPS는 하나의 솔루션이다. `server`는 Spring Boot 실행 모듈이고 Maven 이름은 `tps-server`이다. `frontend`는 같은 제품의 React 화면이다. RPS/LPS는 이 실행 안에서 선택하는 업무 영역이며 별도 서버·코어 저장소가 필요하지 않다. Eclipse 설치 경로나 기존 회사 Jenkins/Nexus가 소스 실행의 필수 경로로 저장되어 있지 않다.

현재 안내는 로컬 TPS 문서 저장·재열기 및 일반 보고서 데이터·QR 미리보기 개발용이다. 운영 인증·전체 디자이너·출력 기능의 완료 안내가 아니다.

이 PC의 Eclipse4.33/m2e2.6.1에서 발견된 JAR 플러그인과 외부 리소스 링크 오류는 POM에 호환 설정을 추가했다. 소스 갱신 후 `tps-server` 선택 → F5 → `Maven > Update Project`(Alt+F5) → `Project > Clean`을 수행한다. 정상 Source 항목은 `src/main/java`, `src/main/resources`, 테스트와 APT 생성 폴더이며 프로젝트 루트 전체를 Source로 쓰지 않는다. 자세한 원인과 확인 범위는 ECLIPSE-FIX.md를 본다. 실제 GUI의 오류 해소를 파일 수정만으로 완료했다고 단정하지 않는다.

## 1. 다른 PC에서도 필요한 준비

- 소스를 clone한 TPS 폴더와 JDK 21 전체 설치본.
- Java 21을 지원하는 Eclipse, Maven 연동(m2e), Annotation Processing 연동(m2e-apt).
- Eclipse에 설치된 Lombok **1.18.48**. Maven이 의존성을 받는 것과 Eclipse 에디터의 Lombok 설치는 별개다. 설치 후 Eclipse를 완전히 종료하고 다시 시작한다. [Lombok 공식 설치 안내](https://projectlombok.org/setup/eclipse)
- 화면을 빌드할 때 Node.js/npm. 버전은 `frontend/package.json` 및 프로젝트 빌드 안내를 따른다.
- 준비된 TPS SQL Server DB와 개발용 접속 권한. 이 실행은 DB를 자동 생성하거나 기존 RPS 데이터를 수정하지 않는다.

기존 Eclipse를 사용할 수 있다. IDE가 없는 배포 대상은 Eclipse가 아니라 검증된 실행 패키지를 사용한다. Maven/Gradle 선택은 같은 소스의 빌드 방식이며 제품을 두 개로 나누지 않는다.

## 2. 먼저 CLI 빌드

TPS 저장소 루트에서 실행한다.

```powershell
.\scripts\build.ps1 -BuildTool Maven
```

이 명령은 화면과 Java 테스트·빌드를 함께 수행한다. Eclipse와 CLI의 차이를 확인할 기준 빌드를 먼저 만든다. Eclipse 설정 파일이나 IDE 배포물을 Git에 통째로 넣지 않는다.

## 3. 비밀번호를 저장하지 않고 Eclipse 열기

사용할 Eclipse와 JDK 경로만 인수로 전달한다. 아래 경로는 예시이며 각 PC의 설치 경로로 바꾼다.

```powershell
.\scripts\open-eclipse.ps1 `
  -EclipseExe 'C:\Tools\eclipse\eclipse.exe' `
  -JavaHome 'C:\Tools\jdk-21'
```

스크립트는 DB 호스트·계정을 묻고, DB 비밀번호와 브라우저 접속 비밀번호는 화면에 표시하지 않고 입력받는다. 브라우저 비밀번호는 12자 이상이다. 값은 새 Eclipse 프로세스와 그 프로세스에서 실행한 Java에만 전달한다. 소스, `.launch`, `.env`, Eclipse 설정에 비밀번호를 기록하지 않는다. 준비한 환경변수 `TPS_DB_HOST`, `TPS_DB_USER`, `TPS_DB_PASSWORD`, `TPS_DEV_PASSWORD`를 상속할 수도 있다. 암호를 명령행 문자열로 적지 않는다.

이미 같은 workspace로 실행 중인 Eclipse는 먼저 정상 종료한다. 기존 프로세스에 새 환경변수가 들어간 것으로 간주하지 않는다. 실행기를 열어 둔 상태에서 Eclipse를 사용하고, Eclipse 종료 시 실행기도 끝난다. 실행기는 Eclipse를 설치하거나 `eclipse.ini`를 자동 수정하지 않는다.

기본 workspace는 **TPS 소스 폴더**이다. 별도 workspace를 원하면 `-Workspace 'C:\Workspaces\TPS'`를 전달한다. 이 값은 소스 위치를 바꾸지 않는다. `-SiteId`, `-EnabledModules RPS`, `-Port`로 개발 실행 범위를 정할 수 있다.

`-AllowLocalCertificateException`은 별도로 허용된 해당 로컬 TPS DB에서만 사용한다. 기본값은 서버 인증서를 검증한다. 예외를 켜도 DB 통신 암호화는 유지되며, 다른 서버에 대한 예외는 현재 백엔드에서 허용하지 않는다.

Windows에서 `Unable to establish loopback connection`과 AF_UNIX 연결 오류가 재현된 경우에만 `-WindowsSocketFallback`을 추가한다. 해당 프로세스에 한해 테스트한 JDK TCP 루프백 대체 경로를 사용하며 OS·방화벽·보안 설정을 바꾸지 않는다. 다른 PC에 이를 항상 적용할 필요는 없다. 실행기가 만든 빈 보조 디렉터리는 Eclipse 종료 뒤 정리한다.

실행 없이 준비 상태만 검사하려면 같은 인수에 `-ValidateOnly`를 붙인다. 이 모드에서는 비밀번호를 묻거나 Eclipse를 실행하지 않는다.

## 4. Maven 프로젝트 가져오기

1. `Window > Preferences > Java > Installed JREs`에서 JDK 21을 등록한다. `Execution Environments > JavaSE-21`을 그 JDK에 연결한다.
2. `File > Import > Maven > Existing Maven Projects`를 연다.
3. 기본 workspace를 소스 루트로 정했다면 **`<TPS>/server`**를 Root Directory로 선택한다. workspace 루트 자체의 aggregator `pom.xml`을 프로젝트로 가져오지 않는다. workspace 바로 아래 폴더를 가져올 때 Eclipse 프로젝트 이름은 `server`가 될 수 있으며 Maven artifact 이름 `tps-server`와 달라도 제품이 달라지는 것은 아니다. [m2e 프로젝트 이름 설명](https://eclipse.dev/m2e/documentation/m2e-faq.html#why-projects-are-renamed-on-import)
4. 별도 workspace를 사용한다면 `<TPS>` 루트에서 프로젝트를 검색해 `server/pom.xml`을 선택할 수 있다. 이때 보통 프로젝트 이름은 `tps-server`이다. 실제 Project Explorer 이름을 확인한다. 소스 파일을 다른 위치로 복사하지 않는다.
5. 해당 프로젝트의 `Properties > Maven > Annotation Processing`에서 **Automatically configure JDT APT**를 선택한다. `Maven > Update Project`와 `Project > Clean` 후 `DocumentDtoMapperImpl` 생성 여부를 확인한다. Lombok과 MapStruct 처리기를 별도 수동 소스로 복사하지 않는다. [MapStruct 공식 Eclipse 설정](https://github.com/mapstruct/mapstruct/blob/main/documentation/src/main/asciidoc/chapter-2-set-up.asciidoc)
6. React 파일도 Eclipse에서 보려면 `File > New > Project > General > Project`를 연다. Project name을 `frontend`로 쓰고, `Use default location`을 해제한 뒤 기존 `<TPS>/frontend` 폴더를 선택하여 Finish한다. workspace가 TPS 루트라 기본 경로가 이미 그 폴더라면 기본 경로를 사용해도 된다. 기존 파일은 그대로 열고 다른 폴더로 복사하지 않는다.
7. Maven/Gradle 서버 프로젝트를 중복으로 가져오지 않는다. 같은 서버 소스를 m2e와 Buildship이 동시에 관리하게 만들지 않는다. Gradle CLI 빌드 지원은 Eclipse Buildship 조합의 GUI 검증 완료를 뜻하지 않는다.

`server/pom.xml`에는 `<m2e.apt.activation>jdt_apt</m2e.apt.activation>`이 이미 들어 있다. 프로젝트의 기존 명시 설정이 우선할 수 있으므로 생성 결과를 확인한다. 사용자의 다른 프로젝트 workspace 설정을 일괄 변경하지 않는다.

## 5. Run / Debug

`File > Import > Run/Debug > Launch Configurations`에서 이 `ide` 폴더를 선택한다. 실제 Eclipse 프로젝트 이름에 맞는 **하나만** 가져온다.

| Project Explorer 이름 | 가져올 설정 |
|---|---|
| `server` | `TPS-local-server.launch` |
| `tps-server` | `TPS-local-tps-server.launch` |

이름이 다르면 `Run > Run Configurations > Java Application`의 Project 항목을 실제 이름으로 고친다. Main class는 `com.thira.tps.TpsApplication`, JRE는 `JavaSE-21`, profile은 `local`이다. Environment의 **Append environment to native environment**를 유지한다. 실행기에서 받은 비밀번호를 여기에 다시 입력해 저장하지 않는다.

`Run`은 정상 실행, `Debug`는 같은 실행에 중단점·변수 조회를 제공한다. 소스의 컨트롤러나 서비스에 중단점을 걸고 저장 요청을 보내면 해당 Java 흐름을 확인할 수 있다. 별도 Tomcat/WAS 설치나 Servers 뷰 배포는 필요하지 않다. [Eclipse Run/Debug 안내](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.user/tasks/task-running_and_debugging.htm)

기동이 확인되면 `http://127.0.0.1:18080`을 연다. 포트를 변경했다면 그 포트를 사용한다. 브라우저 계정은 `tps-developer`, 비밀번호는 실행기에서 입력한 값이다. Java 프로세스를 중복 실행하면 포트가 충돌하므로 Debug 뷰에서 이전 TPS 실행을 정상 종료한다.

## 6. React 빌드 결과가 서버에 반영되는 경로

```text
frontend/src                       React 원본 (.tsx 등)
    | npm ci + npm run build
frontend/dist                      index.html + assets/*.js, *.css
    | server/pom.xml의 copy-frontend (process-resources)
server/target/classes/static       Eclipse에서 실행하는 서버의 화면 리소스
    | 전체 Maven package 빌드
server/target/tps-server.jar        BOOT-INF/classes/static에 화면 포함
```

화면만 수정했다면 **TPS 루트의 `build-frontend.cmd`를 더블클릭**한다. 실제 처리는 `scripts/build-frontend.ps1`이며 JDK21/Node/npm을 확인하고 npm 설치·React 빌드·Maven 리소스 복사·SHA-256 파일 일치 검사를 차례로 실행한다. 성공하면 위 두 결과 경로를 콘솔에 표시한다. Eclipse에서 F5로 새로고침하고 서버를 실행하거나 이미 실행 중이면 브라우저를 새로 고친다. 브라우저 캐시가 남으면 Ctrl+F5를 사용한다. 수동으로 파일을 끌어다 넣을 필요가 없다.

이 실행 파일은 Eclipse를 종료하거나 서버를 재시작하지 않는다. Java 변경이 있으면 Eclipse에서 서버를 다시 실행해야 한다. 배포용 기존 JAR/ZIP 내부는 이 화면 전용 빌드로 바뀌지 않으므로 배포 산출물은 `scripts/build.ps1 -BuildTool Maven`으로 다시 만든다. Gradle을 선택한 전체 빌드는 `server/build/resources/main/static`을 거쳐 같은 JAR 내부 경로로 묶는다.

## 검증 범위

이 안내와 launcher/launch XML은 구문 검사·실제 설치 정보 조회·실행 흐름 대조를 거쳤다. 실제 Eclipse GUI에서 가져온 프로젝트의 Source 경로, Maven Dependencies, JDT APT 생성과 Problems 오류0개를 확인했다. 애플리케이션 Run/Debug·중단점은 아직 직접 검증하지 않았으며 사용자가 위 순서로 실행할 단계다. CLI의 실제 DB·웹 검증과 IDE 실행 검증을 구분한다. 비차단 경고6개와 Spring Tools 외부 버전조회 PKIX는 사용자의 지시대로 추가 조사하지 않는다.
