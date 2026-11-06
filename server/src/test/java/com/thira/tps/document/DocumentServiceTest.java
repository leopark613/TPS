package com.thira.tps.document;

import java.time.LocalDateTime;
import java.util.*;
import com.thira.tps.config.RuntimeSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.node.ObjectNode;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentServiceTest {
    private final CanonicalJson json=new CanonicalJson();
    private DocumentPersistence db;
    private PlatformTransactionManager tx;
    private DocumentService service;
    private ObjectNode document;
    private String draft,key;
    @BeforeEach void setup() throws Exception {
        db=mock(DocumentPersistence.class);tx=mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenAnswer(call->new SimpleTransactionStatus());
        var env=new MockEnvironment().withProperty("server.address","127.0.0.1").withProperty("tps.dev-password","unit-only-password").withProperty("TPS_DB_USER","test").withProperty("TPS_DB_PASSWORD","unit-only");env.setActiveProfiles("local");
        service=new DocumentService(db,json,new DocumentValidator(json),Mappers.getMapper(DocumentDtoMapper.class),new RuntimeSettings(env),tx);
        try(var input=getClass().getResourceAsStream("/native-document.json")) { document=(ObjectNode)json.parse(input.readAllBytes()); }
        draft=UUID.randomUUID().toString();key=UUID.randomUUID().toString();
    }
    @Test void createCommitsOneRevisionAndReceiptWithoutChangingActive() {
        when(db.updateHead(anyString(),anyString(),anyString(),anyString(),eq(0L),eq(1L))).thenReturn(1);
        ApiDtos.TemplateView result=service.create("tps-developer",key,draft,"RPS",document);
        assertThat(result.getSequence()).isEqualTo("1"); assertThat(result.getActiveRevisionId()).isNull();
        assertThat(result.getDocument()).isEqualTo(document); assertThat(result.getHeadEtag()).contains(result.getRevisionId());
        var template=ArgumentCaptor.forClass(Rows.Template.class); verify(db).insertTemplate(template.capture());
        var revision=ArgumentCaptor.forClass(Rows.Revision.class);verify(db).insertRevision(revision.capture());
        var receipt=ArgumentCaptor.forClass(Rows.Receipt.class);verify(db).insertReceipt(receipt.capture());
        assertThat(template.getValue().getActiveToken()).isZero(); assertThat(revision.getValue().getParentRevisionId()).isNull();
        assertThat(receipt.getValue().getStatus()).isEqualTo("COMPLETED"); assertThat(receipt.getValue().getScopeKind()).isEqualTo("CLIENT_DRAFT");
        verify(tx).commit(any());verify(tx,never()).rollback(any());
    }
    @Test void committedSaveRetryReturnsOriginalResultBeforeLoadingCurrentHead() {
        String template=UUID.randomUUID().toString(),revision=UUID.randomUUID().toString();String etag="\"h:"+template+":"+revision+":1\"";
        ApiDtos.TemplateView original=new ApiDtos.TemplateView();original.setTemplateId(template);original.setOwnerModule("RPS");original.setSequence("2");original.setDocument(document);original.setDiagnostics(List.of());
        when(db.lockReceipt(any())).thenAnswer(call->{ Rows.Receipt request=call.getArgument(0); request.setFingerprintVersion(CanonicalJson.HASH_VERSION);request.setStatus("COMPLETED");request.setResultJson(json.write(original));return request; });
        assertThat(service.save("tps-developer",key,template,etag,document).getSequence()).isEqualTo("2");
        verify(db,never()).lockTemplate(anyString(),anyString()); verify(db,never()).insertRevision(any()); verify(db,never()).insertReceipt(any());
    }
    @Test void conflictingRequestKeyRollsBackBeforeTemplateMutation() {
        when(db.lockReceipt(any())).thenAnswer(call->{ Rows.Receipt stored=new Rows.Receipt(); stored.setFingerprintVersion(CanonicalJson.HASH_VERSION);stored.setFingerprint(new byte[32]);return stored; });
        assertThatThrownBy(()->service.create("tps-developer",key,draft,"RPS",document)).isInstanceOf(ApiException.class).extracting("status").isEqualTo(409);
        verify(db,never()).insertTemplate(any()); verify(tx).rollback(any());verify(tx,never()).commit(any());
    }
    @Test void staleWriterCreatesNoRevision() {
        String id=UUID.randomUUID().toString();Rows.Template row=new Rows.Template();row.setTemplateId(id);row.setOwnerModule("RPS");row.setHeadRevisionId(UUID.randomUUID().toString());row.setHeadToken(2);
        when(db.lockTemplate(anyString(),eq(id))).thenReturn(row);
        String stale="\"h:"+id+":"+UUID.randomUUID()+":1\"";
        assertThatThrownBy(()->service.save("tps-developer",key,id,stale,document)).isInstanceOf(ApiException.class).extracting("status").isEqualTo(412);
        verify(db,never()).insertRevision(any()); verify(tx).rollback(any());
    }
    @Test void integrityMismatchDoesNotReturnBody() {
        String id=UUID.randomUUID().toString(),rid=UUID.randomUUID().toString();Rows.Template t=new Rows.Template();t.setTemplateId(id);t.setOwnerModule("RPS");t.setHeadRevisionId(rid);when(db.findTemplate(anyString(),eq(id))).thenReturn(t);
        Rows.Revision r=new Rows.Revision();r.setBodyJson(json.canonical(document));r.setHashVersion(CanonicalJson.HASH_VERSION);r.setBodyHash(new byte[32]);when(db.findRevision(anyString(),eq(id),eq(rid))).thenReturn(r);
        assertThatThrownBy(()->service.head(id)).isInstanceOf(ApiException.class).extracting("code").isEqualTo("DOCUMENT_INTEGRITY_FAILURE");
    }
    @Test void rpsExampleDatasetCannotBeCreatedUnderLpsOwnership() {
        ((tools.jackson.databind.node.ArrayNode)document.get("datasets")).add(com.thira.tps.rps.viewer.ReportDataContract.dataset(json));
        assertThatThrownBy(()->service.create("tps-developer",key,draft,"LPS",document)).isInstanceOf(ApiException.class).extracting("code").isEqualTo("QUERY_MODULE_MISMATCH");
        verifyNoInteractions(db,tx);
    }
    @Test void rpsExampleDatasetCannotBeSavedIntoExistingLpsTemplate() {
        ((tools.jackson.databind.node.ArrayNode)document.get("datasets")).add(com.thira.tps.rps.viewer.ReportDataContract.dataset(json));
        String id=UUID.randomUUID().toString();Rows.Template row=new Rows.Template();row.setTemplateId(id);row.setOwnerModule("LPS");row.setHeadRevisionId(UUID.randomUUID().toString());row.setHeadToken(2);
        when(db.lockTemplate(anyString(),eq(id))).thenReturn(row);
        assertThatThrownBy(()->service.save("tps-developer",key,id,HeadEtag.of(row),document)).isInstanceOf(ApiException.class).extracting("code").isEqualTo("QUERY_MODULE_MISMATCH");
        verify(db,never()).insertRevision(any());verify(tx).rollback(any());
    }
}
