package com.thira.tps.document;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.*;
import static org.assertj.core.api.Assertions.*;

class DocumentValidatorTest {
    private final CanonicalJson json=new CanonicalJson();
    private DocumentValidator validator;
    private ObjectNode document;
    @BeforeEach void setup() throws Exception {
        validator=new DocumentValidator(json);
        try(var in=getClass().getResourceAsStream("/native-document.json")) { document=(ObjectNode)json.parse(in.readAllBytes()); }
    }
    private ObjectNode element() { return (ObjectNode)document.at("/sections/0/bands/0/elements/0"); }
    @Test void nativeAndFuturePropertiesRoundTripWithoutLoss() {
        var checked=validator.check(document); assertThat(checked.isEditable()).isTrue();
        assertThat(checked.getDocument().at("/sections/0/bands/0/elements/0/properties/futureStyle/preserve").asString()).isEqualTo("yes");
    }
    @Test void unknownElementAndFeatureArePreservedReadOnly() {
        element().put("type","vendor.future"); element().put("typeVersion","27");
        var checked=validator.check(document); assertThat(checked.isEditable()).isFalse();
        assertThat(checked.getDocument()).isEqualTo(document);
        assertThat(checked.getDiagnostics()).extracting(ApiDtos.Diagnostic::getCode).contains("UNSUPPORTED_ELEMENT","READ_ONLY_DOCUMENT");
    }
    @Test void duplicateElementSectionIdIsRejected() { element().put("id","section1"); fails("DUPLICATE_ID"); }
    @Test void danglingBandDatasetIsRejected() { ((ObjectNode)document.at("/sections/0/bands/0")).put("datasetId","missing"); fails("MISSING_DATASET_REFERENCE"); }
    @Test void danglingProvenanceIsRejected() {
        ((ArrayNode)document.at("/provenance/elementOrigins")).add(json.parse("{\"elementId\":\"missing\",\"locator\":\"A1\"}")); fails("MISSING_ELEMENT_REFERENCE");
    }
    @Test void missingFieldIsRejected() {
        ((ArrayNode)element().get("bindings")).add(json.parse("{\"property\":\"text\",\"source\":{\"kind\":\"field\",\"datasetId\":\"missing\",\"fieldId\":\"nope\"},\"evaluation\":{\"scope\":\"row\",\"event\":\"beforePrint\"},\"nullPolicy\":\"empty\"}")); fails("MISSING_FIELD_REFERENCE");
    }
    @Test void assetsCannotPretendToBeUploaded() {
        ((ArrayNode)document.get("assets")).add(json.parse("{\"id\":\"asset1\",\"sha256\":\""+"0".repeat(64)+"\",\"mediaType\":\"image/png\",\"byteLength\":0,\"objectRef\":\"local/fake\"}")); fails("ASSET_UPLOAD_NOT_AVAILABLE");
    }
    @Test void invalidStructureCannotBeSaved() { document.remove("sections"); fails("DOCUMENT_INVALID"); }
    @Test void titleIsBoundedByDatabaseUtf16Length() { document.put("title","가".repeat(201)); fails("TITLE_TOO_LONG"); }
    @Test void impossibleMarginsAreRejected() { ((ObjectNode)document.at("/sections/0/page/margins")).put("left",210000); fails("INVALID_MARGINS"); }
    @Test void tooManyElementsAreRejected() {
        ArrayNode elements=(ArrayNode)document.at("/sections/0/bands/0/elements"); JsonNode seed=element().deepCopy();
        for(int i=0;i<500;i++) { ObjectNode copy=(ObjectNode)seed.deepCopy(); copy.put("id","copy"+i);elements.add(copy); } fails("TOO_MANY_ELEMENTS");
    }
    private void fails(String code) { assertThatThrownBy(()->validator.check(document)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code); }
}
