# Jenkins/Nexus 연결 준비

현재 파일은 TPS 전용 연결 구성이다. 기존 회사 Jenkins/Nexus 설정을 수정하거나 게시·배포를 실행하지 않았다. TPS는 이 서비스들 없이도 개발 PC에서 빌드하고 완성 패키지로 실행할 수 있다.

전용 Windows 에이전트의 label을 `tps-windows`로 준비하고 JDK21/JAVA_HOME, Node24/npm, Git, 저장소 접근 권한을 제공한다. `Jenkinsfile`의 BUILD_TOOL은 Maven/Gradle 중 선택하며 기본은 Maven이다. `PUBLISH_NEXUS=false`가 기본이다. DB 자격증명을 빌드에 넣지 않는다. 실제 DB 통합시험은 별도 격리 개발 환경에서 수행한다.

게시를 준비할 때 회사 Nexus에 TPS용 hosted Maven 저장소를 정한다. 홈페이지 주소가 아닌 `/repository/<검토된 이름>/` URL이 필요하다. Jenkins의 신뢰된 job/agent 설정에 `TPS_NEXUS_ALLOWED_REPOSITORY`를 지정하고, 전용 publisher 계정을 credentials ID `tps-nexus-publisher`로 등록한다. 비밀번호는 저장소에 넣지 않는다. 파이프라인은 정확한 회사 HTTPS 호스트·포트 및 설정된 저장소 경로만 허용하며 userinfo/query/fragment를 거부한 다음 credentials를 주입한다.

좌표는 `com.thira.tps:tps-server:0.1.0-SNAPSHOT`이다. 초기에는 snapshot 저장소가 필요하다. 정식 버전은 버전 부여와 release 저장소를 함께 검토한다. 같은 버전을 두 빌드로 연속 게시하지 않는다. Maven은 `ci/maven-settings.xml`의 환경변수 자리표시자를 사용하며 Gradle은 환경에서 게시 계정/URL을 받는다.

게시 단계는 artifact 업로드만 한다. 현장 앱을 자동 중단·재시작하거나 패치를 강제로 적용하지 않는다. 관리자의 공지와 사용자의 명시적인 패치 시작이라는 기존 결정은 별도 후속 업데이트 기능에서 구현한다.
