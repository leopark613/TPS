package com.thira.tps.document;

import java.security.MessageDigest;
import java.sql.SQLException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import com.thira.tps.config.RuntimeSettings;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

@Service
public class DocumentService {
    private final DocumentPersistence db;
    private final CanonicalJson json;
    private final DocumentValidator validator;
    private final DocumentDtoMapper dto;
    private final RuntimeSettings settings;
    private final TransactionTemplate transaction;
    public DocumentService(DocumentPersistence db,CanonicalJson json,DocumentValidator validator,DocumentDtoMapper dto,RuntimeSettings settings,PlatformTransactionManager manager) {
        this.db=db; this.json=json; this.validator=validator; this.dto=dto; this.settings=settings;
        transaction=new TransactionTemplate(manager); transaction.setTimeout(20);
    }
    public ApiDtos.TemplatePage list(String module,int page,int size) {
        settings.checkModule(module);
        if(page<0 || size<1 || size>100) throw new ApiException(400,"INVALID_PAGE","목록 조회 범위를 확인하세요.");
        var rows=db.listTemplates(site(),module,(long)page*size,size);
        return new ApiDtos.TemplatePage(rows.stream().map(dto::summary).toList(),page,size,db.countTemplates(site(),module));
    }
    public ApiDtos.TemplateView head(String templateId) {
        Rows.Template template=required(db.findTemplate(site(),templateId));
        return view(template,revision(templateId,template.getHeadRevisionId()),false);
    }
    public ApiDtos.TemplateView historical(String templateId,String revisionId) {
        Rows.Template template=required(db.findTemplate(site(),templateId));
        return view(template,revision(templateId,revisionId),true);
    }
    public ApiDtos.RevisionList revisions(String templateId) {
        required(db.findTemplate(site(),templateId));
        return new ApiDtos.RevisionList(db.listRevisions(site(),templateId).stream().map(dto::revision).toList());
    }
    public ApiDtos.TemplateView create(String actor,String requestKey,String draftId,String owner,JsonNode document) {
        settings.checkModule(owner); actor(actor);
        var checked=validator.check(document);
        Rows.Receipt request=scope(actor,"CREATE",draftId,requestKey);
        request.setFingerprint(fingerprint(request,owner,null,checked.getDocument()));
        return retry(() -> transaction.execute(status -> {
            ApiDtos.TemplateView previous=completed(db.lockReceipt(request),request.getFingerprint());
            if(previous!=null) return previous;
            LocalDateTime now=now();
            Rows.Template template=new Rows.Template();
            template.setSiteId(site()); template.setTemplateId(UUID.randomUUID().toString()); template.setOwnerModule(owner);
            template.setName(checked.getDocument().get("title").asString()); template.setEnabled(true); template.setCreatedBy(actor); template.setCreatedAt(now);
            db.insertTemplate(template);
            Rows.Revision revision=newRevision(template,checked,actor,1,now);
            db.insertRevision(revision);
            if(db.updateHead(site(),template.getTemplateId(),revision.getRevisionId(),template.getName(),0,1)!=1) throw new IllegalStateException("Create CAS failed");
            template.setHeadRevisionId(revision.getRevisionId()); template.setHeadToken(1);
            ApiDtos.TemplateView result=view(template,revision,false);
            receipt(request,result,now); return result;
        }));
    }
    public ApiDtos.TemplateView save(String actor,String requestKey,String templateId,String ifMatch,JsonNode document) {
        actor(actor); HeadEtag.requireValid(ifMatch,templateId);
        var checked=validator.check(document);
        Rows.Receipt request=scope(actor,"SAVE",templateId,requestKey);
        request.setFingerprint(fingerprint(request,null,ifMatch,checked.getDocument()));
        return retry(() -> transaction.execute(status -> {
            ApiDtos.TemplateView previous=completed(db.lockReceipt(request),request.getFingerprint());
            if(previous!=null) return previous;
            Rows.Template template=required(db.lockTemplate(site(),templateId));
            String current=HeadEtag.of(template);
            if(!current.equals(ifMatch)) throw new ApiException(412,"HEAD_CHANGED","다른 저장본이 있습니다. 현재 편집 내용은 유지됩니다.",List.of(),current);
            Rows.Revision old=revision(templateId,template.getHeadRevisionId());
            if(template.getHeadToken()==Long.MAX_VALUE || old.getRevisionNo()==Long.MAX_VALUE)
                throw new ApiException(409,"VERSION_LIMIT","새 양식으로 저장해야 합니다.");
            LocalDateTime now=now(); long newToken=template.getHeadToken()+1;
            Rows.Revision revision=newRevision(template,checked,actor,old.getRevisionNo()+1,now);
            db.insertRevision(revision);
            String name=checked.getDocument().get("title").asString();
            if(db.updateHead(site(),templateId,revision.getRevisionId(),name,template.getHeadToken(),newToken)!=1)
                throw new ApiException(412,"HEAD_CHANGED","다른 저장본이 있습니다. 다시 확인하세요.");
            template.setName(name); template.setHeadRevisionId(revision.getRevisionId()); template.setHeadToken(newToken);
            ApiDtos.TemplateView result=view(template,revision,false);
            receipt(request,result,now); return result;
        }));
    }
    public ApiDtos.ResolveResult resolve(String actor,String operation,String scopeId,String key) {
        actor(actor); if(!Set.of("CREATE","SAVE").contains(operation)) throw new ApiException(400,"INVALID_OPERATION","요청 종류를 확인하세요.");
        Rows.Receipt receipt=db.findReceipt(scope(actor,operation,scopeId,key));
        if(receipt==null) return new ApiDtos.ResolveResult("unknown",null);
        ApiDtos.TemplateView result=completed(receipt,null);
        return new ApiDtos.ResolveResult(result==null?"unknown":"completed",result);
    }
    private Rows.Receipt scope(String actor,String operation,String scopeId,String key) {
        Rows.Receipt r=new Rows.Receipt(); r.setSiteId(site()); r.setActorId(actor); r.setOperation(operation);
        r.setScopeKind("CREATE".equals(operation)?"CLIENT_DRAFT":"TEMPLATE"); r.setScopeId(CanonicalJson.uuid(scopeId)); r.setRequestKey(CanonicalJson.uuid(key)); return r;
    }
    private byte[] fingerprint(Rows.Receipt request,String owner,String etag,JsonNode doc) {
        var value=json.object(); value.put("operation",request.getOperation()); value.put("scopeKind",request.getScopeKind());
        value.put("scopeId",request.getScopeId()); value.put("ownerModule",owner); value.put("ifMatch",etag); value.set("document",doc);
        return json.hash(json.canonical(value));
    }
    private ApiDtos.TemplateView completed(Rows.Receipt receipt,byte[] fingerprint) {
        if(receipt==null) return null;
        if(!CanonicalJson.HASH_VERSION.equals(receipt.getFingerprintVersion())) throw new ApiException(409,"REQUEST_VERSION_UNSUPPORTED","요청 기록 버전을 확인해야 합니다.");
        if(fingerprint!=null && !MessageDigest.isEqual(receipt.getFingerprint(),fingerprint))
            throw new ApiException(409,"REQUEST_KEY_REUSED","같은 저장 요청에 다른 내용을 보낼 수 없습니다.");
        if(!"COMPLETED".equals(receipt.getStatus()) || receipt.getResultJson()==null) throw new ApiException(503,"SAVE_RETRY_REQUIRED","저장 결과를 확인한 뒤 같은 요청으로 다시 시도하세요.");
        ApiDtos.TemplateView result=json.read(receipt.getResultJson(),ApiDtos.TemplateView.class);
        settings.checkModule(result.getOwnerModule()); return result;
    }
    private void receipt(Rows.Receipt request,ApiDtos.TemplateView result,LocalDateTime now) {
        request.setReceiptId(UUID.randomUUID().toString()); request.setFingerprintVersion(CanonicalJson.HASH_VERSION);
        request.setStatus("COMPLETED"); request.setResultJson(json.write(result)); request.setCreatedAt(now); request.setCompletedAt(now);
        db.insertReceipt(request);
    }
    private Rows.Revision newRevision(Rows.Template template,DocumentValidator.Checked checked,String actor,long sequence,LocalDateTime now) {
        Rows.Revision row=new Rows.Revision(); row.setSiteId(site()); row.setTemplateId(template.getTemplateId()); row.setRevisionId(UUID.randomUUID().toString());
        row.setRevisionNo(sequence); row.setParentRevisionId(template.getHeadRevisionId()); row.setSchemaVersion(checked.getDocument().get("schemaVersion").asString());
        row.setBodyJson(checked.getCanonical()); row.setBodyHash(checked.getHash()); row.setHashVersion(CanonicalJson.HASH_VERSION);
        row.setValidationState(checked.isEditable()?"VALID":"WARNING"); row.setDiagnosticsJson(json.write(checked.getDiagnostics())); row.setCreatedAt(now); row.setCreatedBy(actor); return row;
    }
    private ApiDtos.TemplateView view(Rows.Template template,Rows.Revision revision,boolean historical) {
        if(!CanonicalJson.HASH_VERSION.equals(revision.getHashVersion()) || !MessageDigest.isEqual(json.hash(revision.getBodyJson()),revision.getBodyHash()))
            throw new ApiException(500,"DOCUMENT_INTEGRITY_FAILURE","저장된 양식의 무결성을 확인해야 합니다.");
        var checked=validator.check(json.parse(revision.getBodyJson()));
        ApiDtos.TemplateView out=new ApiDtos.TemplateView();
        out.setTemplateId(template.getTemplateId().toLowerCase(Locale.ROOT)); out.setName(checked.getDocument().get("title").asString()); out.setOwnerModule(template.getOwnerModule());
        out.setHeadRevisionId(lower(template.getHeadRevisionId())); out.setHeadToken(Long.toString(template.getHeadToken()));
        out.setActiveRevisionId(lower(template.getActiveRevisionId())); out.setCreatedAt(dto.timestamp(template.getCreatedAt()));
        out.setLastSavedAt(dto.timestamp(revision.getCreatedAt())); out.setRevisionNo(Long.toString(revision.getRevisionNo()));
        out.setRevisionId(lower(revision.getRevisionId())); out.setSequence(Long.toString(revision.getRevisionNo())); out.setBodyHash(dto.hash(revision.getBodyHash()));
        out.setDocument(checked.getDocument()); out.setDiagnostics(checked.getDiagnostics()); out.setEditable(!historical && checked.isEditable());
        out.setHeadEtag(historical?null:HeadEtag.of(template)); return out;
    }
    private Rows.Template required(Rows.Template template) {
        if(template==null || template.getHeadRevisionId()==null) throw new ApiException(404,"TEMPLATE_NOT_FOUND","양식을 찾을 수 없습니다.");
        settings.checkModule(template.getOwnerModule()); return template;
    }
    private Rows.Revision revision(String templateId,String revisionId) {
        Rows.Revision row=db.findRevision(site(),templateId,revisionId);
        if(row==null) throw new ApiException(404,"REVISION_NOT_FOUND","저장 버전을 찾을 수 없습니다."); return row;
    }
    private <T> T retry(Supplier<T> work) {
        for(int attempt=0;;attempt++) {
            try { return work.get(); }
            catch(DataAccessException ex) {
                if(attempt>=4 || !retryable(ex)) throw ex;
                try { Thread.sleep(30L*(attempt+1)); } catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new ApiException(503,"SAVE_RETRY_REQUIRED","저장 결과를 확인한 뒤 같은 요청으로 다시 시도하세요."); }
            }
        }
    }
    private boolean retryable(Throwable error) {
        for(Throwable e=error;e!=null;e=e.getCause()) if(e instanceof SQLException sql && Set.of(1205,2601,2627).contains(sql.getErrorCode())) return true;
        return false;
    }
    private String site() { return settings.getSiteId(); }
    private static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS); }
    private static String lower(String value) { return value==null?null:value.toLowerCase(Locale.ROOT); }
    private static void actor(String actor) { if(actor==null || actor.isBlank() || actor.length()>100) throw new ApiException(401,"INVALID_ACTOR","연결 정보를 확인하세요."); }
}
