# TPS 양식의 물리 저장 선택 v0.1

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

2026-11-06. 사용자는 **같은 MES_LITE DB에 TPS_ 전용 저장 구조를 설계하는 방향을 선택**했다(D047). 이어 RPS/LPS를 구분하는 이름을 요청했으므로 세부안은 `TPS_RPS_`, `TPS_LPS_`, 공통 문서는 `TPS_DOC_`, 출력 작업은 `TPS_PRINT_`로 후속 D048에서 채택했다. 실제 DB 생성은 보류하며 metadata 조회 결과와 컬럼/키 초안은 database-v0.1에서 관리한다.

## 확인한 현행 제약

| 항목 | 구조 정렬 후 기준과 의미 |
|---|---|
| RPS_REPORT 키 | REPORTID+SITEID, 각각 varchar(40) NOT NULL, 대소문자 구분 UTF8 collation. 임의 lowercase/키 합치기 금지 |
| 필수 표시명 | REPORTNAME nvarchar(200) NOT NULL. 본체 NOT NULL은 위 키2개와 표시명 |
| 본문 | XMLDATA/JSONSCHEMA nullable nvarchar(max). SQL xml 타입이 아니어도 DX XML 소비자가 기대하는 형식은 유지해야 함 |
| 데이터 연결 | StoredQuery ID/ClassID/Version, MESSAGESET, JSONKEY/JSONLISTKEY의 업무 의미 보존. TPS metadata 저장용으로 전용하지 않음 |
| RPS_REPORTHIST | LASTEVENTSEQ bigint IDENTITY(1,1)는 테이블 전체 이벤트 키. 양식별 revision/동시수정 lock token이 아님 |

근거: [목표 구조 참조](../../MES_LITE_DB_AlterScript/01_G2_TARGET_SCHEMA_REFERENCE.sql), [적용 후 metadata](../../MES_LITE_DB_AlterScript/evidence/apply-01.json). 기존 검증 기록을 읽은 것이며 이 단락의 근거는 기존 구조 정렬 증거다. 후속 metadata 조회 결과는 최신 catalog 보고서로 구분한다. 컬럼 이름으로 매핑하고 물리 순서를 전제하지 않는다. C# 이력 모델에 없는 JSONKEY/JSONLISTKEY도 DB에는 있어 모델만으로 이력 보존을 판단하지 않는다.

기존 목록 조회에는 TPS 문서 형식이나 시험ID 접두사 제외 조건이 없다. [REPORT.Get.Autogen.cs](<C:/Users/THIRAUTECH/_gitlab/RPS/rps1.8.0/BACKEND/THiRAMES.RPS.API/REPORT/REPORT.Get.Autogen.cs:26>)는 site/사용 상태 조회를 하며 단건 조회는 사용 상태만으로도 격리되지 않는다. [ReportStorage](<C:/Users/THIRAUTECH/_gitlab/RPS/rps1.8.0/BACKEND/THiRAMES.RPS.Service/ReportStorage.cs:190>)의 빈 양식 생성·저장 경로와 [ReportDesignerService](<C:/Users/THIRAUTECH/_gitlab/RPS/rps1.8.0/BACKEND/THiRAMES.RPS.Service/Services/ReportDesignerService.cs:18>)도 있다. 따라서 ‘TPS_ ID’나 숨김 상태만으로 기존 앱의 접촉/다시 저장을 막았다고 하지 않는다.

## 선택지

| 안 | 이점 | 추가 검토 |
|---|---|---|
| 기존 RPS_ 신규 행에 저장 | 기존 키/분류 활용, 추가 table 없이 접근 | 옛 앱과 형식 충돌, revision/진단/원본 참조 전용 계약 부족. 기존 컬럼 전용이나 접두사만으로 해결하지 않음 |
| 같은 MES_LITE의 TPS_ 전용 table | 원본과 신규 문서 분리, 불변 revision/CAS/요청키 계약을 명확히 구현 가능 | 신규 DDL·권한·백업·운영 이관 필요. DB 장애·계정 권한 자체가 분리되는 것은 아님 |
| DB 외 문서 저장소 | DB 신규 table을 피할 여지 | 사용자 원래 DB 저장 흐름과 차이; head/revision/원자성·백업·다중 사용자 잠금의 추가 부담. 자동 채택하지 않음 |

## 권고안의 논리 저장 단위

이름과 컬럼은 설계 후보이며 CREATE/ALTER 스크립트가 아니다. 최종 물리 타입/인덱스·보존기간·크기 제한은 실제 workload와 허용 범위 확인 후 정한다.

| 단위 후보 | 식별·무결성 |
|---|---|
| TPS_DOC_TEMPLATE | site+template ID, 소유 모듈, head revision, 비교 token; 별도 active revision/token |
| TPS_DOC_REVISION | site+template+revision ID, 양식별 sequence 유일, parent revision, schema, 본문/hash, 시각/작성자/진단. 불변 |
| TPS_DOC_ACTIVATION | 업무 적용/복귀 감사, 이전/새 revision 및 active token, 요청 식별자 |
| TPS_DOC_ASSET | site+asset ID, content hash, media type/size, ready 상태·blob 또는 검증된 storage key |
| TPS_DOC_REVISION_ASSET | revision→asset 참조, 같은site 및존재 검증. 참조 중 자산 정리 금지 |
| TPS_DOC_IMPORT | 원본 시스템/DB 별칭·table·복합키·이력키(있으면), 관찰시각, 원문/부속자료 hash/asset 참조, converter, 변환 보고 |
| TPS_DOC_REQUEST | site+actor+operation+template+request ID 유일, fingerprint, 진행/결과, 생성 revision. head/적용 commit과 일관성 유지. 신규 생성은 client draftId를 범위에 포함 |

원본 snapshot은 XMLDATA뿐 아니라 데이터 schema·파라미터·쿼리 식별자·에셋 등 변환에 사용한 자료를 함께 식별한다. raw hash의 인코딩과 NULL/빈 값 구분을 고정하고 MODIFYTIME/이력번호 하나를 불변 원본 대신 쓰지 않는다. 연결 암호·운영 query 결과 전체를 provenance에 복사하지 않는다.

업무 발행·PrintJob·RenderSnapshot 저장은 문서 본문과 분리된 기존 상위 설계를 따른다. 위7개 후보로 제품 DB 전체가 끝난다는 뜻이 아니다. 이관 시 실제 RPS 원본에 FK를 만들어 구버전 앱의 삭제/갱신 동작을 바꾸는 방안은 기본으로 두지 않는다. 원본 복합키와 보존 snapshot을 provenance로 기록한다.

## 권한과 다음 작업

D037/D038 관리자 연결·DDL 권한은 완료된 1회 작업이다. D027 G2 소량 신규 시험행 허용은 MES_LITE 신규 쓰기로 확대하지 않는다. D047은 저장 방향 선택으로 기록하고 실제 DDL/시험 쓰기는 구체 스크립트·대상·행수·보존/복구 계획을 별도 준비한 후 해당 권한 범위에서 수행한다. 이번 산출물에는 실행 SQL이 없다.


## LPS 실제 소스와 업무별 저장(2026-11-06)

[테이블과 소유 분석](../../analysis/lps/20261106-TABLES-AND-TPS-OWNERSHIP.md): LPS table매핑71개는 소스범위이며 후속 catalog 조회에서 알려진 이름의 객체가 없음을 확인했다. 새TPS는 TPS_RPS_/TPS_LPS_/공통TPS_DOC_/TPS_PRINT_로 구분하는 채택 방향이다. RPS/LPS 기능은 독립활성화하되 한WAS·공통서비스를 사용한다. 같은테이블 저장이 같은양식·active 공유를 의미하지 않으며 각Template 소유모듈로 분리한다.
