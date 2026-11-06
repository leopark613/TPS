package com.thira.tps.document;

import java.util.regex.Pattern;

public final class HeadEtag {
    private HeadEtag() {}
    private static final String ID="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final Pattern FORMAT=Pattern.compile("\"h:("+ID+"):("+ID+"):([1-9][0-9]*)\"");
    public static String of(Rows.Template template) { return "\"h:"+template.getTemplateId().toLowerCase(java.util.Locale.ROOT)+":"+template.getHeadRevisionId().toLowerCase(java.util.Locale.ROOT)+":"+template.getHeadToken()+"\""; }
    public static void requireValid(String etag,String templateId) {
        if(etag==null || etag.isBlank()) throw new ApiException(428,"IF_MATCH_REQUIRED","마지막으로 연 양식의 버전 정보가 필요합니다.");
        var match=FORMAT.matcher(etag);
        if(!match.matches() || !match.group(1).equals(templateId)) throw new ApiException(400,"INVALID_IF_MATCH","양식 버전 정보가 올바르지 않습니다.");
        try { Long.parseLong(match.group(3)); } catch(NumberFormatException ex) { throw new ApiException(400,"INVALID_IF_MATCH","양식 버전 정보가 올바르지 않습니다."); }
    }
}
