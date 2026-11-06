# Eclipse 설정 준비 검증 — 2026-11-06

실제 설치에서 확인: Eclipse 4.33 계열, JDT Core3.39, m2e2.6.1, m2e-apt2.2.202, Buildship3.1.9, ini의 embedded Java21.0.5 경로와 Lombok javaagent 항목. 기존 Lombok은 `java -jar lombok.jar version`에서1.18.22였으며 TPS의1.18.48과 다르다. 이 작업에서는 Eclipse 설치·ini·사용자 workspace를 변경하지 않았다.

작성 파일:

- `scripts/open-eclipse.ps1`: 설치 경로 매개변수, JDK21/Lombok 확인, 마스킹 입력과 프로세스 환경 상속, 기본 workspace 소스 루트, 선택적 NIO 대체 경로와 빈 디렉터리 정리.
- `ide/TPS-local-server.launch`, `ide/TPS-local-tps-server.launch`: 프로젝트 이름별 portable Java Run/Debug 설정. 절대 설치 경로·DB 호스트·사용자 비밀번호를 담지 않는다.
- `ide/ECLIPSE-SETUP.md`: clone 후 준비·import·APT·Run/Debug·같은 WAS의 화면 갱신 절차.

PowerShell parser 오류0, 두 launch XML 파싱 통과. 실제 JDK21.0.11과 기존 Eclipse 대상으로 `-ValidateOnly`를 실행해 업데이트 전 Jdk21=true/Lombok11848=false/Launched=false를 확인했다. 이후 주 작업자가 기존 ini·Lombok을 정본 증거 폴더에 백업하고 Lombok1.18.48 교체를 수행했다. 이 helper를 다시 실행해 Jdk21=true/Lombok11848=true/Launched=false를 확인했다. GUI는 실행하지 않았다.

아직 실행하지 않은 확인: Eclipse GUI import, 실제 프로젝트 이름, JavaSE-21 매핑, JDT에서의 Lombok/MapStruct 증분 생성, Run/Debug 기동, 중단점, launcher 종료 시 보조 디렉터리 정리. Maven/Gradle CLI 또는 브라우저 테스트 통과를 이 IDE 검증의 대체 증거로 사용하지 않는다.
