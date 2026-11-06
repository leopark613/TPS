package com.thira.tps.rps.viewer;

import java.util.*;
import com.thira.tps.config.RuntimeSettings;
import com.thira.tps.document.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.node.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GeneralViewerServiceTest {
    private final CanonicalJson json=new CanonicalJson();
    private DocumentService documents;
    private ReportQuery query;
    private GeneralViewerService viewer;
    private ObjectNode doc;
    private ApiDtos.TemplateView view;
    private final String templateId=UUID.randomUUID().toString(),revisionId=UUID.randomUUID().toString();
    @BeforeEach void setup() throws Exception {
        documents=mock(DocumentService.class);query=mock(ReportQuery.class);
        viewer=createViewer("RPS,LPS");doc=ReportFixtures.document(json,true);
        view=new ApiDtos.TemplateView();view.setTemplateId(templateId);view.setRevisionId(revisionId);view.setOwnerModule("RPS");view.setBodyHash("unit-hash");view.setDocument(doc);view.setEditable(false);
        when(documents.historical(templateId,revisionId)).thenReturn(view);
        when(query.reportInfo("source-site","000012")).thenReturn(new ReportViewerDtos.ReportRow("000012","가상 품목",null));
    }
    private GeneralViewerService createViewer(String modules) throws Exception {
        var env=new MockEnvironment().withProperty("server.address","127.0.0.1").withProperty("tps.dev-password","unit-only-password").withProperty("TPS_DB_USER","test").withProperty("TPS_DB_PASSWORD","unit-only")
            .withProperty("tps.site-id","editor-site").withProperty("TPS_RPS_REFERENCE_SITE_ID","source-site").withProperty("tps.enabled-modules",modules);env.setActiveProfiles("local");
        return new GeneralViewerService(documents,new DocumentValidator(json),query,new RuntimeSettings(env),json,new QrEncoder());
    }
    @Test void resolvesExactHistoricalRevisionAndSiteWithNoDocumentMutation() {
        String before=json.canonical(doc);
        var result=viewer.preview(templateId,revisionId,"000012");
        assertThat(result.getTemplateId()).isEqualTo(templateId);assertThat(result.getRevisionId()).isEqualTo(revisionId);assertThat(result.getBodyHash()).isEqualTo("unit-hash");
        assertThat(result.getElements()).hasSize(2);
        assertThat(result.getElements().get(0).getProperties().get("text").asString()).isEqualTo("가상 품목");
        assertThat(result.getElements().get(1).getProperties().get("payload").asString()).isEqualTo("000012");
        assertThat(result.getElements().get(1).getQr().getMatrix()).isNotEmpty();
        assertThat(result.getResultHash()).hasSize(64);assertThat(json.canonical(doc)).isEqualTo(before);
        verify(query).reportInfo("source-site","000012");verify(documents).historical(templateId,revisionId);verifyNoMoreInteractions(query,documents);
    }
    @Test void staticPreviewUsesNoQuery() throws Exception {
        doc=ReportFixtures.document(json,false);view.setDocument(doc);
        var result=viewer.preview(templateId,revisionId,null);assertThat(result.getQueryRef()).isNull();assertThat(result.getReportId()).isNull();verifyNoInteractions(query);
        fails(()->viewer.preview(templateId,revisionId,"000012"),"UNEXPECTED_REPORT_ID");
    }
    @Test void queryVersionAndOwnerAreCheckedBeforeDataLookup() {
        ((ObjectNode)doc.at("/datasets/0/queryRef")).put("version","2");fails(()->viewer.preview(templateId,revisionId,"000012"),"QUERY_NOT_SUPPORTED");
        view.setOwnerModule("LPS");fails(()->viewer.preview(templateId,revisionId,"000012"),"QUERY_MODULE_MISMATCH");verifyNoInteractions(query);
    }
    @Test void moduleDisabledPreventsEvenHistoricalLookup() throws Exception {
        viewer=createViewer("LPS");fails(()->viewer.preview(templateId,revisionId,"000012"),"MODULE_DISABLED");fails(()->viewer.reportList(),"MODULE_DISABLED");verifyNoInteractions(query,documents);
    }
    @Test void missingAndOversizedSelectionNeverQuery() {
        fails(()->viewer.preview(templateId,revisionId,null),"INVALID_REPORT_ID");fails(()->viewer.preview(templateId,revisionId,"가".repeat(14)),"INVALID_REPORT_ID");verifyNoInteractions(query);
    }
    @Test void missingDataAndDataFailureHaveDifferentSanitizedErrors() {
        fails(()->viewer.preview(templateId,revisionId,"missing"),"REPORT_DATA_NOT_FOUND");
        when(query.reportInfo("source-site","000012")).thenThrow(new DataAccessResourceFailureException("private SQL and connection secret"));
        assertThatThrownBy(()->viewer.preview(templateId,revisionId,"000012")).isInstanceOf(ApiException.class).hasMessageNotContaining("private").extracting("code").isEqualTo("REPORT_DATA_UNAVAILABLE");
    }
    @Test void nullableBindingPolicyDiffersFromMissingFieldAndEmptyQr() {
        ObjectNode binding=(ObjectNode)doc.at("/sections/0/bands/0/elements/1/bindings/0");
        ((ObjectNode)binding.get("source")).put("fieldId","reportClassId");
        fails(()->viewer.preview(templateId,revisionId,"000012"),"BINDING_VALUE_REQUIRED");
        binding.put("nullPolicy","empty");fails(()->viewer.preview(templateId,revisionId,"000012"),"QR_PAYLOAD_EMPTY");
        ((ObjectNode)doc.at("/sections/0/bands/0/elements/0/bindings/0/source")).put("fieldId","reportClassId");
        ((ObjectNode)doc.at("/sections/0/bands/0/elements/0/bindings/0")).put("nullPolicy","empty");
        ((ObjectNode)binding.get("source")).put("fieldId","reportId");
        assertThat(viewer.preview(templateId,revisionId,"000012").getElements().get(0).getProperties().get("text").asString()).isEmpty();
    }
    @Test void unknownVisualPropertyIsPreservedButCannotPretendToRender() {
        ((ObjectNode)doc.at("/sections/0/bands/0/elements/0/properties")).put("futureStyle","unknown");
        fails(()->viewer.preview(templateId,revisionId,"000012"),"UNSUPPORTED_PREVIEW_PROPERTY");verifyNoInteractions(query);
    }
    @Test void unsupportedElementIsNotSilentlyOmitted() {
        ((ObjectNode)doc.at("/sections/0/bands/0/elements/1")).put("type","vendor.unknown");
        fails(()->viewer.preview(templateId,revisionId,"000012"),"PREVIEW_NOT_SUPPORTED");verifyNoInteractions(query);
    }
    @Test void outOfBoundsAndNonsquareQrAreExplicit() {
        ObjectNode frame=(ObjectNode)doc.at("/sections/0/bands/0/elements/1/frame");
        frame.put("x",9007199254740991L);fails(()->viewer.preview(templateId,revisionId,"000012"),"ELEMENT_OUT_OF_BOUNDS");
        frame.put("x",10000);frame.put("width",29000);fails(()->viewer.preview(templateId,revisionId,"000012"),"QR_FRAME_NOT_SQUARE");verifyNoInteractions(query);
    }
    @Test void listCapsRowsAndDetectsActualExtraRow() {
        var rows=new ArrayList<ReportViewerDtos.ReportRow>();for(int i=0;i<101;i++)rows.add(new ReportViewerDtos.ReportRow("r"+i,"synthetic",null));
        when(query.reportList("source-site")).thenReturn(rows);
        var result=viewer.reportList();assertThat(result.getRows()).hasSize(100);assertThat(result.isTruncated()).isTrue();assertThat(result.getDataset()).isEqualTo(ReportDataContract.dataset(json));
        when(query.reportList("source-site")).thenReturn(rows.subList(0,100));assertThat(viewer.reportList().isTruncated()).isFalse();
    }
    private void fails(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,String code) { assertThatThrownBy(callable).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code); }
}
