package com.example.travelplatform.destination;

import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.destination.dto.CreateDestinationRequest;
import com.example.travelplatform.destination.dto.DestinationWriteRequest;
import com.example.travelplatform.destination.validation.DestinationInputPolicy;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DestinationInputPolicyTest {
    private CreateDestinationRequest request() {
        var request = new CreateDestinationRequest(); request.setName("  雨林  "); return request;
    }
    @Test void createDefaultsAndRequiredTextAreNormalized() {
        var values = DestinationInputPolicy.normalize(request());
        assertThat(values.name()).isEqualTo("雨林");
        assertThat(values.country()).isEqualTo("中国");
        assertThat(values.destinationType()).isEqualTo("CITY");
        assertThat(values.status()).isEqualTo("ACTIVE");
        assertThat(values.sortOrder()).isZero();
        assertThat(values.parentId()).isNull();
    }
    @Test void missingPutFieldsAndExplicitNullDefaultsAreRejected() {
        assertThatThrownBy(() -> DestinationInputPolicy.normalize(new DestinationWriteRequest())).isInstanceOf(ApiException.class);
        var request = request(); request.setCountry(null);
        assertThatThrownBy(() -> DestinationInputPolicy.normalize(request)).isInstanceOf(ApiException.class);
    }
    @Test void unicodeLengthsAndLargeParentIdsArePreserved() {
        var request = request(); request.setName("🌿".repeat(120)); request.setParentId("9007199254740993");
        assertThat(DestinationInputPolicy.normalize(request).parentId()).isEqualTo(9007199254740993L);
        request.setName("🌿".repeat(121));
        assertThatThrownBy(() -> DestinationInputPolicy.normalize(request)).isInstanceOf(ApiException.class);
    }
    @Test void invalidEnumsNegativeSortAndLongDescriptionsAreRejected() {
        var request = request(); request.setDestinationType("BAD");
        assertThatThrownBy(() -> DestinationInputPolicy.normalize(request)).isInstanceOf(ApiException.class);
        request.setDestinationType("CITY"); request.setSortOrder(-1);
        assertThatThrownBy(() -> DestinationInputPolicy.normalize(request)).isInstanceOf(ApiException.class);
        request.setSortOrder(0); request.setDescription("x".repeat(10001));
        assertThatThrownBy(() -> DestinationInputPolicy.normalize(request)).isInstanceOf(ApiException.class);
    }
}
