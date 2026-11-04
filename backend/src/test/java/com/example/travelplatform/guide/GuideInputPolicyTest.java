package com.example.travelplatform.guide;

import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.guide.dto.CreateGuideRequest;
import com.example.travelplatform.guide.dto.GuideImageRequest;
import com.example.travelplatform.guide.dto.UpdateGuideRequest;
import com.example.travelplatform.guide.validation.GuideInputPolicy;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class GuideInputPolicyTest {
    private CreateGuideRequest request() {
        var r = new CreateGuideRequest(); r.setTitle("  沿溪慢行  "); r.setContent("\n  一段山路\n"); return r;
    }

    @Test void requiredTextAndDraftDefaultsPreserveMarkdownWhitespace() {
        var r = request(); var v = GuideInputPolicy.normalize(r);
        assertThat(v.title()).isEqualTo("沿溪慢行");
        assertThat(v.content()).isEqualTo("\n  一段山路\n");
        assertThat(GuideInputPolicy.creationStatus(r)).isEqualTo("DRAFT");
        assertThat(v.tagIds()).isEmpty(); assertThat(v.images()).isEmpty();
        assertThat(v.destinationId()).isNull(); assertThat(v.budgetPerPerson()).isNull();
    }

    @Test void missingAndExplicitNullRequiredValuesAreRejected() {
        assertThatThrownBy(() -> GuideInputPolicy.normalize(null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> GuideInputPolicy.normalize(new UpdateGuideRequest())).isInstanceOf(ApiException.class);
        var r = request(); r.setContent(null);
        assertThatThrownBy(() -> GuideInputPolicy.normalize(r)).isInstanceOf(ApiException.class);
        r.setContent(""); r.setTitle("  ");
        assertThatThrownBy(() -> GuideInputPolicy.normalize(r)).isInstanceOf(ApiException.class);
    }

    @Test void unicodeBoundariesAndLargeStringDestinationIdAreExact() {
        var r = request(); r.setTitle("🌿".repeat(180)); r.setDestinationId("9007199254740993");
        assertThat(GuideInputPolicy.normalize(r).destinationId()).isEqualTo(9007199254740993L);
        r.setTitle("🌿".repeat(181));
        assertThatThrownBy(() -> GuideInputPolicy.normalize(r)).isInstanceOf(ApiException.class);
        r.setTitle("山路"); r.setContent("🌿".repeat(100000));
        assertThat(GuideInputPolicy.normalize(r).content()).hasSize(200000);
        r.setContent("🌿".repeat(100001));
        assertThatThrownBy(() -> GuideInputPolicy.normalize(r)).isInstanceOf(ApiException.class);
    }

    @Test void optionalTextIsStrippedAndWhitespaceBecomesNull() {
        var r = request(); r.setSummary("  清晨的山谷  "); r.setSeason(" \n ");
        var v = GuideInputPolicy.normalize(r);
        assertThat(v.summary()).isEqualTo("清晨的山谷"); assertThat(v.season()).isNull();
        r.setSummary("🌿".repeat(501));
        assertThatThrownBy(() -> GuideInputPolicy.normalize(r)).isInstanceOf(ApiException.class);
        r.setSummary(null); r.setSeason("春".repeat(41));
        assertThatThrownBy(() -> GuideInputPolicy.normalize(r)).isInstanceOf(ApiException.class);
    }

    @Test void monetaryGrammarIsExactAndScaleIsTwo() {
        var r = request();
        for (String amount : List.of("0", "0.1", "0.10", "99999999.99")) {
            r.setBudgetPerPerson(amount);
            assertThat(GuideInputPolicy.normalize(r).budgetPerPerson().scale()).isEqualTo(2);
        }
        r.setBudgetPerPerson("0.1");
        assertThat(GuideInputPolicy.normalize(r).budgetPerPerson().toPlainString()).isEqualTo("0.10");
        for (String amount : List.of("01", "-0", ".5", "1.", "1e2", "NaN", "100000000", "0.001", " 1", "1 ")) {
            r.setBudgetPerPerson(amount);
            assertThatThrownBy(() -> GuideInputPolicy.normalize(r)).as(amount).isInstanceOf(ApiException.class);
        }
    }

    @Test void travelDaysStayWithinOneAnd365() {
        var r = request();
        for (int days : List.of(1, 365)) { r.setTravelDays(days); assertThat(GuideInputPolicy.normalize(r).travelDays()).isEqualTo(days); }
        for (int days : List.of(0, -1, 366)) { r.setTravelDays(days); assertThatThrownBy(() -> GuideInputPolicy.normalize(r)).isInstanceOf(ApiException.class); }
    }

    @Test void creationStatusIsStrictAndPublishingRequiresNonblankBody() {
        var r = request();
        for (String status : Arrays.asList(null, "HIDDEN", "published", "")) {
            r.setStatus(status); assertThatThrownBy(() -> GuideInputPolicy.creationStatus(r)).isInstanceOf(ApiException.class);
        }
        r.setStatus("PUBLISHED"); assertThat(GuideInputPolicy.creationStatus(r)).isEqualTo("PUBLISHED");
        r.setContent(" \n\t"); var v = GuideInputPolicy.normalize(r);
        assertThatThrownBy(() -> GuideInputPolicy.requirePublishable(v)).isInstanceOf(ApiException.class);
        r.setContent(""); assertThat(GuideInputPolicy.normalize(r).content()).isEmpty();
        r.setContent("正文"); assertThatCode(() -> GuideInputPolicy.requirePublishable(GuideInputPolicy.normalize(r))).doesNotThrowAnyException();
    }

    @Test void tagIdsArePositiveUniqueAndLimited() {
        var r = request(); r.setTagIds(List.of("9007199254740993", "2"));
        assertThat(GuideInputPolicy.normalize(r).tagIds()).containsExactly(9007199254740993L, 2L);
        for (List<String> ids : List.of(List.of("1", "01"), List.of("0"), List.of("9223372036854775808"))) {
            r.setTagIds(ids); assertThatThrownBy(() -> GuideInputPolicy.normalize(r)).isInstanceOf(ApiException.class);
        }
        r.setTagIds(Arrays.asList((String)null));
        assertThatThrownBy(() -> GuideInputPolicy.normalize(r)).isInstanceOf(ApiException.class);
        r.setTagIds(java.util.stream.LongStream.rangeClosed(1, 11).mapToObj(Long::toString).toList());
        assertThatThrownBy(() -> GuideInputPolicy.normalize(r)).isInstanceOf(ApiException.class);
    }

    @Test void imageOrderIsPreservedAndDuplicatesNullsAndTooManyAreRejected() {
        var r = request(); var a = new GuideImageRequest("/uploads/a.png", "溪谷"); var b = new GuideImageRequest("/uploads/b.jpg", null);
        r.setImages(List.of(b, a)); assertThat(GuideInputPolicy.normalize(r).images()).containsExactly(b, a);
        r.setImages(List.of(a, a)); assertThatThrownBy(() -> GuideInputPolicy.normalize(r)).isInstanceOf(ApiException.class);
        r.setImages(Arrays.asList((GuideImageRequest)null)); assertThatThrownBy(() -> GuideInputPolicy.normalize(r)).isInstanceOf(ApiException.class);
        r.setImages(java.util.stream.IntStream.range(0,21).mapToObj(i -> new GuideImageRequest("/uploads/"+i+".png",null)).toList());
        assertThatThrownBy(() -> GuideInputPolicy.normalize(r)).isInstanceOf(ApiException.class);
    }

    @Test void imageTextBoundariesAreCheckedWithoutPretendingToCheckDisk() {
        var r = request(); r.setImages(List.of(new GuideImageRequest("/uploads/a.png", "🌿".repeat(200))));
        assertThat(GuideInputPolicy.normalize(r).images()).hasSize(1);
        r.setImages(List.of(new GuideImageRequest("/uploads/a.png", "🌿".repeat(201))));
        assertThatThrownBy(() -> GuideInputPolicy.normalize(r)).isInstanceOf(ApiException.class);
        r.setImages(List.of(new GuideImageRequest(null, null)));
        assertThatThrownBy(() -> GuideInputPolicy.normalize(r)).isInstanceOf(ApiException.class);
        r.setImages(null); r.setCoverImageUrl("x".repeat(501));
        assertThatThrownBy(() -> GuideInputPolicy.normalize(r)).isInstanceOf(ApiException.class);
    }
}
