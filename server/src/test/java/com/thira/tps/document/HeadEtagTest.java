package com.thira.tps.document;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HeadEtagTest {
    @Test void strongVersionSpecificTagsOnly() {
        String id=UUID.randomUUID().toString(); Rows.Template row=new Rows.Template(); row.setTemplateId(id);row.setHeadRevisionId(UUID.randomUUID().toString()); row.setHeadToken(3);
        String tag=HeadEtag.of(row); assertThatCode(()->HeadEtag.requireValid(tag,id)).doesNotThrowAnyException();
        for(String invalid:new String[]{"*","W/"+tag,tag+","+tag,tag.replace(":3\"",":9223372036854775808\"")})
            assertThatThrownBy(()->HeadEtag.requireValid(invalid,id)).isInstanceOf(ApiException.class).extracting("status").isEqualTo(400);
        assertThatThrownBy(()->HeadEtag.requireValid(null,id)).isInstanceOf(ApiException.class).extracting("status").isEqualTo(428);
    }
}
