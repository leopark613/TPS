package com.thira.tps.document;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.AfterMapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel="spring")
public interface DocumentDtoMapper {
    ApiDtos.TemplateSummary summary(Rows.Listed row);
    @Mapping(target="sequence", source="revisionNo")
    ApiDtos.RevisionSummary revision(Rows.Revision row);
    default String decimal(long number) { return Long.toString(number); }
    default String timestamp(LocalDateTime time) { return time == null ? null : time.toInstant(ZoneOffset.UTC).toString(); }
    default String hash(byte[] bytes) { return bytes == null ? null : HexFormat.of().formatHex(bytes); }
    @AfterMapping default void ids(@MappingTarget ApiDtos.TemplateSummary target) {
        target.setTemplateId(lower(target.getTemplateId())); target.setHeadRevisionId(lower(target.getHeadRevisionId())); target.setActiveRevisionId(lower(target.getActiveRevisionId()));
    }
    @AfterMapping default void revisionId(@MappingTarget ApiDtos.RevisionSummary target) { target.setRevisionId(lower(target.getRevisionId())); }
    private static String lower(String value) { return value==null?null:value.toLowerCase(java.util.Locale.ROOT); }
}
