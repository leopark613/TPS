package com.thira.tps.rps.viewer;

import com.thira.tps.document.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.*;
import static org.assertj.core.api.Assertions.*;

class ReportDocumentCapabilityTest {
    private final CanonicalJson json=new CanonicalJson();
    private DocumentValidator validator;
    private ObjectNode doc;
    @BeforeEach void setup() throws Exception { validator=new DocumentValidator(json);doc=ReportFixtures.document(json,true); }
    @Test void fieldBoundTextAndQrAreEditableWithOriginalDocumentIntact() {
        var checked=validator.check(doc);assertThat(checked.isEditable()).isTrue();assertThat(checked.getDocument()).isEqualTo(doc);
    }
    @Test void staticQrIsAvailableWithoutDatasetForBothModuleOwners() throws Exception {
        var staticDocument=ReportFixtures.document(json,false);
        assertThat(validator.check(staticDocument).isEditable()).isTrue();
        assertThatCode(()->ReportDataContract.checkOwner("LPS",staticDocument)).doesNotThrowAnyException();
        assertThatThrownBy(()->ReportDataContract.checkOwner("LPS",doc)).isInstanceOf(ApiException.class).extracting("code").isEqualTo("QUERY_MODULE_MISMATCH");
    }
    @Test void alteredQueryVersionIsPreservedButReadonly() {
        ((ObjectNode)doc.at("/datasets/0/queryRef")).put("version","2");readonly();
    }
    @Test void alteredOrdinalTypePathOrExtraDatasetIsReadonly() {
        ((ObjectNode)doc.at("/datasets/0")).put("ordinal",1);readonly();
        ((ObjectNode)doc.at("/datasets/0")).put("ordinal",0);
        ((ObjectNode)doc.at("/datasets/0/fields/0")).put("type","integer");readonly();
        ((ObjectNode)doc.at("/datasets/0/fields/0")).put("type","string");
        ((ArrayNode)doc.at("/datasets/0/fields/0/path")).set(0,"other");readonly();
    }
    @Test void duplicateTargetBindingIsRejected() {
        ((ArrayNode)doc.at("/sections/0/bands/0/elements/0/bindings")).add(ReportFixtures.binding(json,"text","reportId","empty"));
        assertThatThrownBy(()->validator.check(doc)).isInstanceOf(ApiException.class).extracting("code").isEqualTo("DUPLICATE_BINDING");
    }
    @Test void unsupportedScopeEventPolicyAndQrOptionsRemainReadonly() {
        ObjectNode evaluation=(ObjectNode)doc.at("/sections/0/bands/0/elements/0/bindings/0/evaluation");
        evaluation.put("scope","report");readonly();evaluation.put("scope","row");
        evaluation.put("event","printOnPage");readonly();evaluation.put("event","beforePrint");
        ObjectNode binding=(ObjectNode)doc.at("/sections/0/bands/0/elements/0/bindings/0");
        binding.put("nullPolicy","literal");readonly();binding.put("nullPolicy","error");
        ((ObjectNode)doc.at("/sections/0/bands/0/elements/1/properties")).put("errorCorrection","L");readonly();
    }
    @Test void unknownPropertiesPreserveExistingEditorCompatibility() {
        ((ObjectNode)doc.at("/sections/0/bands/0/elements/1/properties")).put("futureOption","opaque");
        assertThat(validator.check(doc).isEditable()).isTrue();
        assertThat(validator.check(doc).getDocument()).isEqualTo(doc);
    }
    @Test void fieldOrderDoesNotChangeMeaning() {
        ArrayNode fields=(ArrayNode)doc.at("/datasets/0/fields");var first=fields.remove(0);fields.add(first);
        assertThat(validator.check(doc).isEditable()).isTrue();
    }
    private void readonly() { var checked=validator.check(doc);assertThat(checked.isEditable()).isFalse();assertThat(json.canonical(checked.getDocument())).isEqualTo(json.canonical(doc)); }
}
