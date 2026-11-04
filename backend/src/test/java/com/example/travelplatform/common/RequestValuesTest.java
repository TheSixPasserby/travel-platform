package com.example.travelplatform.common;

import com.example.travelplatform.common.validation.RequestValues;
import com.example.travelplatform.common.error.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class RequestValuesTest {
    @Test void idsArePositiveDecimalLongs() {
        assertThat(RequestValues.positiveId("9007199254740993")).isEqualTo(9007199254740993L);
        assertThat(RequestValues.positiveId("9223372036854775807")).isEqualTo(Long.MAX_VALUE);
        for (String value : new String[]{null, "", "0", "-1", "+1", "1.5", "x", "9223372036854775808"})
            assertThatThrownBy(() -> RequestValues.positiveId(value)).isInstanceOf(ApiException.class);
    }
    @Test void paginationDefaultsAndBoundsAreExplicit() {
        assertThat(RequestValues.page(null)).isEqualTo(1);
        assertThat(RequestValues.size(null)).isEqualTo(10);
        assertThat(RequestValues.size("50")).isEqualTo(50);
        for (String value : new String[]{"", "0", "-1", "51", "1.5"})
            assertThatThrownBy(() -> RequestValues.size(value)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> RequestValues.page("0")).isInstanceOf(ApiException.class);
    }
    @Test void paginationOffsetCannotOverflow() {
        assertThat(RequestValues.offset(3, 10)).isEqualTo(20);
        assertThatThrownBy(() -> RequestValues.offset(Long.MAX_VALUE, 50)).isInstanceOf(ApiException.class);
    }
    @Test void keywordUsesUnicodeCodepointsAndWhitespaceNormalization() {
        assertThat(RequestValues.keyword("  山路  ")).isEqualTo("山路");
        assertThat(RequestValues.keyword(" \t ")).isNull();
        assertThat(RequestValues.keyword(null)).isNull();
        assertThat(RequestValues.keyword("🌿".repeat(100))).isEqualTo("🌿".repeat(100));
        assertThatThrownBy(() -> RequestValues.keyword("🌿".repeat(101))).isInstanceOf(ApiException.class);
    }
    @Test void sqlWildcardsAreEscapedAsLiteralSearchText() {
        assertThat(RequestValues.likePattern("a!b%c_d")).isEqualTo("%a!!b!%c!_d%");
    }
}
