package com.thira.tps.document;

import java.io.IOException;
import java.security.Principal;
import java.util.Set;
import com.thira.tps.config.RuntimeSettings;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/tps")
public class DocumentController {
    private final DocumentService service;
    private final CanonicalJson json;
    private final RuntimeSettings settings;
    public DocumentController(DocumentService service,CanonicalJson json,RuntimeSettings settings) { this.service=service;this.json=json;this.settings=settings; }
    @GetMapping("/session") public ApiDtos.Session session(Principal user) { return new ApiDtos.Session(user.getName(),settings.getSiteId(),settings.getEnabledModules()); }
    @GetMapping("/templates") public ApiDtos.TemplatePage list(@RequestParam String module,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) { return service.list(module,page,size); }
    @PostMapping("/templates") public ResponseEntity<ApiDtos.TemplateView> create(HttpServletRequest request,Principal user,@RequestHeader(value="Idempotency-Key",required=false) String key) throws IOException {
        JsonNode body=body(request,Set.of("clientDraftId","ownerModule","document"));
        var result=service.create(user.getName(),CanonicalJson.uuid(key),CanonicalJson.uuid(json.text(body,"clientDraftId")),json.text(body,"ownerModule"),body.get("document"));
        return ResponseEntity.status(201).eTag(result.getHeadEtag()).body(result);
    }
    @GetMapping("/templates/{id}/head") public ResponseEntity<ApiDtos.TemplateView> head(@PathVariable String id) {
        var result=service.head(CanonicalJson.uuid(id)); return ResponseEntity.ok().eTag(result.getHeadEtag()).body(result);
    }
    @PutMapping("/templates/{id}/head") public ResponseEntity<ApiDtos.TemplateView> save(@PathVariable String id,HttpServletRequest request,Principal user,
        @RequestHeader(value="Idempotency-Key",required=false) String key,@RequestHeader(value="If-Match",required=false) String etag) throws IOException {
        JsonNode body=body(request,Set.of("document"));
        var result=service.save(user.getName(),CanonicalJson.uuid(key),CanonicalJson.uuid(id),etag,body.get("document"));
        return ResponseEntity.ok().eTag(result.getHeadEtag()).body(result);
    }
    @GetMapping("/templates/{id}/revisions") public ApiDtos.RevisionList revisions(@PathVariable String id) { return service.revisions(CanonicalJson.uuid(id)); }
    @GetMapping("/templates/{id}/revisions/{revisionId}") public ApiDtos.TemplateView historical(@PathVariable String id,@PathVariable String revisionId) { return service.historical(CanonicalJson.uuid(id),CanonicalJson.uuid(revisionId)); }
    @PostMapping("/save-requests/resolve") public ApiDtos.ResolveResult resolve(HttpServletRequest request,Principal user) throws IOException {
        JsonNode body=body(request,Set.of("operation","scopeId","requestKey"));
        return service.resolve(user.getName(),json.text(body,"operation"),CanonicalJson.uuid(json.text(body,"scopeId")),CanonicalJson.uuid(json.text(body,"requestKey")));
    }
    private JsonNode body(HttpServletRequest request,Set<String> keys) throws IOException {
        if(request.getContentLengthLong()>CanonicalJson.MAX_BYTES) throw new ApiException(413,"REQUEST_TOO_LARGE","문서 크기는 1 MiB 이하여야 합니다.");
        JsonNode body=json.parse(request.getInputStream().readNBytes(CanonicalJson.MAX_BYTES+1));
        if(!body.isObject() || body.properties().stream().anyMatch(e->!keys.contains(e.getKey())) || keys.stream().anyMatch(k->!body.has(k)))
            throw new ApiException(400,"INVALID_REQUEST","요청 항목을 확인하세요.");
        return body;
    }
}
