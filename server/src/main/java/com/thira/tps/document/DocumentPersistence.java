package com.thira.tps.document;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DocumentPersistence {
    Rows.Receipt lockReceipt(@Param("scope") Rows.Receipt scope);
    Rows.Receipt findReceipt(@Param("scope") Rows.Receipt scope);
    Rows.Template lockTemplate(@Param("siteId") String siteId,@Param("templateId") String templateId);
    Rows.Template findTemplate(@Param("siteId") String siteId,@Param("templateId") String templateId);
    Rows.Revision findRevision(@Param("siteId") String siteId,@Param("templateId") String templateId,@Param("revisionId") String revisionId);
    List<Rows.Listed> listTemplates(@Param("siteId") String siteId,@Param("module") String module,@Param("offset") long offset,@Param("size") int size);
    long countTemplates(@Param("siteId") String siteId,@Param("module") String module);
    List<Rows.Revision> listRevisions(@Param("siteId") String siteId,@Param("templateId") String templateId);
    int insertTemplate(Rows.Template row);
    int insertRevision(Rows.Revision row);
    int updateHead(@Param("siteId") String siteId,@Param("templateId") String templateId,@Param("revisionId") String revisionId,@Param("name") String name,@Param("oldToken") long oldToken,@Param("newToken") long newToken);
    int insertReceipt(Rows.Receipt row);
}
