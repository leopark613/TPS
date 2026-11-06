# TPS 기존 명칭 대응 조사

2026-11-06. 원본 정적 읽기 검증. 런타임 호출/물리 출력 검증을 뜻하지 않는다. 사용자 지시: 기존 RPS/LPS 명칭을 가능한 한 유지한다. 이 파일은 작업 복사본의 조사 초안이며 정본 반영은 부모 에이전트가 수행한다.

## 조사 루트
- RPS: `C:\Users\THIRAUTECH\_gitlab\RPS\rps1.8.0`
- LPS: `C:\Users\THIRAUTECH\_gitlab\LPS`
- 복원 agent: `C:\Users\A1292021154\Desktop\LLM_Skill_Context\_TPS(ThiraPrintSystem)\_ref\agent-main`

아래 경로는 위 루트에 대한 상대 경로이며 행번호는 조사 당시 원본이다.

## RPS
| 원본 | 증거 | TPS 제안 |
|---|---|---|
| Report / REPORTManager | BACKEND/THiRAMES.RPS.API/Models/Report.cs:10; Service/300.리포트/REPORTManager.ReportInfo.cs:12, ReportList.cs:13 | rps.report. Report 모델 및 ReportInfo/ReportList/ReportSave/ReportDelete/ReportXMLDATA 작업명을 보존 |
| StoredQueryInfo / StoredQuerySave | BACKEND/THiRAMES.RPS.Service/300.리포트/REPORTManager.StoredQueryInfo.cs:13, StoredQuerySave.cs:14 | rps.query. 원본작업명/쿼리 ID·분류·버전 계약 보존, 저장방식 변경은 별도 |
| GeneralViewerService | BACKEND/THiRAMES.RPS.Service/Util/PrintGeneral/GeneralViewerService.cs:15; Viewer:27, BindDataSource:44, Preview:121, LoadReportData:178, ExecuteDataSet:207 | rps.viewer.GeneralViewerService. Java 메서드는 viewer/bindDataSource/preview/loadReportData |
| PreviewReportService | BACKEND/THiRAMES.RPS.Service/Services/PreviewReportService.cs:12; CustomizeReportAsync:24 → Preview:50. ServiceExtension.cs:54 등록 | 별도 orchestration이 필요할 때 이름 보존 |
| ReportDesignerService | BACKEND/THiRAMES.RPS.Service/Services/ReportDesignerService.cs:9; ReportDesignerSave:18, ReportDesignerLoad:37 | 업무 어댑터 이름 보존. 공통 document 저장소는 계속 공유 |
| ReportStorage | BACKEND/THiRAMES.RPS.Service/ReportStorage.cs:18, GetData:85, SetData:266; BindDataSource 호출:166; Viewer 호출:398,483,504 | DevExpress 확장 원본이므로 공통 DocumentService를 무조건 이 이름으로 변경하지 않음 |
| ReportPrintService / PrintQueueService | BACKEND/THiRAMES.RPS.Service/Services/ReportPrintService.cs:11, Print:27; PrintQueueService.cs:15, Enqueue:46; ServiceExtension.cs:72–74 등록 | 출력 업무/공통 출력의 책임에 맞춰 원본 이름 활용 |
| ExcelReportImportController / ExcelReportImportService / ExcelReportImportResult | BACKEND/THiRAMES.RPS.Service/Controller/ExcelReportImportController.cs:29 Import; Services/ExcelReportImportService.cs:34 Import; Interfaces/IExcelReportImportService.cs:17 결과 | 동일 이름 보존, LayoutBytes는 새 문서 모델에 맞게 명시 변환 |
| QRCODE / QRCode | BACKEND/THiRAMES.RPS.Service/Util/PrintZPL/ZPLManager.cs:674; ZPLCommand.cs:411 | ZPL 출력의 QR 이름/역할. 일반 보고서의 DevExpress QR 전체 기능과 동일하다고 주장하지 않음 |

Report 필드: Reportid:25, Siteid:32, Reportname:37, Reportclassid:42, Reporttype:47, Storedqueryid:52, Storedqueryclassid:57, Storedqueryversion:62, Iszpl:67, Xmldata:72, Jsonschema:77, Messageset:82, Jsonlistkey:87, Jsonkey:92. Java에서는 reportId 등으로 명시 매핑하며 외부 식별자/DB 컬럼을 조용히 바꾸지 않는다.

VITE/src/router/routes.js:9–12,22,26,38은 Viewer/ViewerList/Preview/Print/Report/Reportquery/Designer 화면을 연결한다. VITE/src/stores/api/reportstore.js:46 ReportList, :63 ReportInfo, :78 ReportSave; reportquerystore.js:47 StoredQueryInfo, :63 StoredQuerySave, :75 RuleMultiInquiry. FRONT와 VITE는 별도 변형이다. 배포 EXE의 실제 프런트 빌드 출처까지 이번 조사에서 입증한 것은 아니다.

주의: BACKEND/THiRAMES.RPS.Business/300.리포트/ReportPreview.cs:10부터 클래스 전체가 주석이다. 현재 활성 진입점으로 채택하지 않는다. ReportController 같은 새 HTTP 경계는 신규 클래스임을 표시한다. ReportExecutionService/LabelIssuanceService/SerialNumberService는 이번 조사에서 발견한 레거시 이름이 아니다.

## LPS
LPS 서비스 경로 기준: services/src/main/java/com/thirautech/cmos/lps/packages/business/oi/.
- command/CwvaVaLotLabelPrintGenIdM01.java:28 클래스, messageValidation:31, process:37. :105–118 LpsMbasidrseq 조회·갱신, :123 LpsMbasidrdef. :155 GENID, :156 LSATSEQ 결과키. 처음의 자동생성 TODO 주석만 보고 전체를 빈 stub으로 분류하지 않는다. 실제 :132–136 신규 GenID 분기와 :158 암호화 분기는 미완성이다.
- web-ui/src/main/resources/wwwroot/view/ngs/mes/PL/PL_LM_012.js:929 handleClickBtnGenerate → :992 CwvaVaLotLabelPrintGenIdM01; :1269 handleClickBtnPrint → :1287 lpsAgent.printZpl.
- 같은 UI :1247 CwvaVaLotLabelPrintInsertGenIdM01his/, :1488 tranCPOPM01HISUpd → :1495 CwvaVaLotLabelPrintUpdatePrtM01his/. 해당 Java command 본문은 services/src와 web-ui/src/main/java 검색에서 미발견. 호출명만 존재하는 것을 완성된 서비스로 기록하지 않는다.
- services/src/main/resources/sql/mssql/lps/Cwva_VaLotLabelPrintView_LotList_00001_LPS_sql.xml:4 select ID=CwvaVaLotLabelPrintViewLotList-00001.
- entity/LpsCpopm01his.java:146 REPRINT_TIME, :149 REPRINT_USER_ID. 이 필드 존재만으로 완성된 재출력 서비스라고 주장하지 않는다.
- 추가 원본 작업명: CpopLblDef, CwipLabelParamInfo, CwipViewLabelDesignTestLabelInfo, CatrNamStsExcelUpload. manager/CwipViewLabelDesignTestLabelInfoManger.java:19는 원본 철자가 Manger다. 철자 정규화 시 대응표를 유지한다.
- TPS 제안: lps.command 아래 실제 작업명을 보존하고, LpsMpoplbldef/LpsMpoplbldgn/LpsMbasidrdef/LpsMbasidrseq/LpsCpopm01his 업무 모델의 원본 대응을 유지한다. 외부 CoreRule 프레임워크 의존성까지 복제할 필요는 없다.

## 두 agent 구분
- LPS/agent/Program.cs:8 → PrinterController.Start의 HttpListener. PrinterController.cs:106,120,134,141 → PrinterManager.PrintText/PrintToPdf/PrintZpl/ConvertZplToImageBytes. DTO PrintRequest:157, PrintPdfRequest:163, ZplPrintRequest:169, ZplRawRequest:175.
- 복원 agent/Program.cs:164–166은 PrinterService/SerialService/PrintService 등록, :200 MapControllers, :201 PrintHub. PrintController.cs의 PrintersController:20, PrintController:165, ImageGrfController:206, ZplPreviewController:304, ZplPrintController:407. DTO PrintJobRequest:942, ZplPreviewRequest:960, ZplUsbPrintRequest:1045, ImageZplConvertRequest:1466.
- LPS web-ui/src/main/resources/wwwroot/resources/aming/lps.agent.js:90 printZpl → /zpl-print, :176 getPrintersAsync, :195 getZplPreviewAsync. 복원 agent의 controller 계약과 대응하는 정적 증거다.
- 복원 agent/services/HttpService.cs의 SubmitPrintJobAsync는 정의 외 호출을 찾지 못했다. 활성 출력 경로로 단정하지 않는다.

## 이번 구현과의 구분
TPS-IMPL-002의 ReportList는 로컬 복제 RPS 보고서 메타데이터만 읽는 고정 예제다. 기존 StoredQuery 임의 실행이나 LPS 채번을 이번에 구현한 것으로 기록하지 않는다. 일반 보고서 data/QR 기능은 공통 문서 모델을 사용하되 RPS 조회/미리보기 업무명은 실제 GeneralViewerService 명칭에서 이어 쓴다.
