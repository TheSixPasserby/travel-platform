package com.example.travelplatform.comment;

import com.example.travelplatform.comment.validation.CommentInputPolicy;
import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CommentInputPolicyTest {
    private void invalid(String value) {
        assertThatThrownBy(()->CommentInputPolicy.normalize(value)).isInstanceOfSatisfying(ApiException.class,
                error->assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
    @Test void lineEndingsAndUnicodeBoundaryWhitespaceAreCanonical() {
        assertThat(CommentInputPolicy.normalize(" \t\u00A0\u200B\u3000溪谷\r\n旅途\r很好\uFEFF "))
                .isEqualTo("溪谷\n旅途\n很好");
    }
    @Test void nullEmptyAndOnlyInvisibleBoundaryWhitespaceFail() {
        invalid(null);invalid("");
        for(String value:List.of(" \t\n\r","\u00A0","\u200B\uFEFF","\u3000\u2007\u202F"))invalid(value);
    }
    @Test void emojiUseCodePointsAndRespect2000Boundary() {
        assertThat(CommentInputPolicy.normalize("🌿".repeat(2000))).hasSize(4000);
        invalid("🌿".repeat(2001));
    }
    @Test void boundaryWhitespaceDoesNotConsumeTheContentLimit() {
        assertThat(CommentInputPolicy.normalize(" ".repeat(20)+"溪".repeat(2000)+"\u00A0\uFEFF")).hasSize(2000);
    }
    @Test void combiningCharactersCountSeparately() {
        assertThat(CommentInputPolicy.normalize("e\u0301".repeat(1000))).isEqualTo("e\u0301".repeat(1000));
        invalid("e\u0301".repeat(1001));
    }
    @Test void joinersAndMeaningfulInternalWhitespaceArePreserved() {
        for(String value:List.of("👩\u200D👩\u200D👧\u200D👦","A\u200CB\u200DC","a\tb\nc\u00A0d\u3000e\u200Bf")) {
            assertThat(CommentInputPolicy.normalize(value)).isEqualTo(value);
        }
    }
    @Test void unsupportedControlsAreRejectedBeforeTrimming() {
        for(int cp:new int[]{0,1,8,11,12,14,31,127,128,133,159}) {
            invalid("a"+Character.toString(cp)+"b");invalid(Character.toString(cp)+"text");
        }
    }
    @Test void htmlAndSqlLikeTextAreStoredAsPlainTextRatherThanParsed() {
        String value="<img src=x onerror=alert(1)> ' OR 1=1 --";
        assertThat(CommentInputPolicy.normalize(" "+value+" ")).isEqualTo(value);
    }
}
