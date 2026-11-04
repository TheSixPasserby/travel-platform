package com.example.travelplatform.user;

import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.common.error.ErrorCode;
import com.example.travelplatform.user.validation.UserInputPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * M1 Task 3a：用户输入策略的真实边界单元测试（不 mock，直接调用 static 策略）。
 *
 * <p>固定契约：
 * <ul>
 *   <li>{@code normalizeUsername}：strip → {@link java.util.Locale#ROOT} 小写 → ASCII {@code [a-z0-9_]{3,50}}；</li>
 *   <li>{@code validatePassword}：非 null、非全空白、至少 8 个 Unicode 码点、UTF-8 不超过 72 字节，且<b>不 trim</b>；</li>
 *   <li>{@code normalizeNickname}：strip 后 1–50 个 Unicode 码点；</li>
 *   <li>{@code normalizeBio}：null 保持 null，否则最多 300 个 Unicode 码点，且<b>不强制 trim</b>；</li>
 *   <li>非法输入一律抛 {@link ApiException}({@link ErrorCode#VALIDATION_FAILED}, 安全中文文案)，不回显被拒绝的原始值。</li>
 * </ul>
 */
class UserInputPolicyTest {

    /** 密码 UTF-8 上限；BCrypt 只使用前 72 字节，超出必须拒绝而不是静默截断。 */
    private static final int PASSWORD_MAX_UTF8_BYTES = 72;

    // ---------------------------------------------------------------------
    // 用户名：strip + Locale.ROOT 小写 + ASCII [a-z0-9_]{3,50}
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("normalizeUsername")
    class NormalizeUsername {

        @Test
        @DisplayName("strip 首尾空白并统一转为小写")
        void stripsAndLowercases() {
            assertThat(UserInputPolicy.normalizeUsername("  Traveler_01  ")).isEqualTo("traveler_01");
            assertThat(UserInputPolicy.normalizeUsername("AbC")).isEqualTo("abc");
            // 全角空格/制表符/换行同样属于 strip 范围。
            assertThat(UserInputPolicy.normalizeUsername("\tABC_DEF\n")).isEqualTo("abc_def");
            assertThat(UserInputPolicy.normalizeUsername("\u3000Travel_9\u3000")).isEqualTo("travel_9");
        }

        @Test
        @DisplayName("长度边界：3 与 50 个 ASCII 字符通过，未 strip 前超长但 strip 后合法也算通过")
        void acceptsLengthBoundaries() {
            assertThat(UserInputPolicy.normalizeUsername("abc")).isEqualTo("abc");
            String exactlyFifty = "a".repeat(50);
            assertThat(UserInputPolicy.normalizeUsername(exactlyFifty)).isEqualTo(exactlyFifty);
            String paddedFifty = "  " + "A".repeat(50) + "  ";
            assertThat(UserInputPolicy.normalizeUsername(paddedFifty)).isEqualTo("a".repeat(50));
        }

        @Test
        @DisplayName("拒绝 null、过短、过长、非 ASCII 与非法字符")
        void rejectsInvalidUsernames() {
            assertRejected(() -> UserInputPolicy.normalizeUsername(null), "null 用户名");
            assertRejected(() -> UserInputPolicy.normalizeUsername(""), "空用户名");
            assertRejected(() -> UserInputPolicy.normalizeUsername("   "), "全空白用户名");
            assertRejected(() -> UserInputPolicy.normalizeUsername("ab"), "2 字符用户名");
            assertRejected(() -> UserInputPolicy.normalizeUsername("  ab  "), "strip 后仅 2 字符");
            assertRejected(() -> UserInputPolicy.normalizeUsername("a".repeat(51)), "51 字符用户名");
            assertRejected(() -> UserInputPolicy.normalizeUsername("abc-def"), "含连字符");
            assertRejected(() -> UserInputPolicy.normalizeUsername("abc def"), "含内嵌空格");
            assertRejected(() -> UserInputPolicy.normalizeUsername("abc.def"), "含点号");
            assertRejected(() -> UserInputPolicy.normalizeUsername("abc@def"), "含符号");
            assertRejected(() -> UserInputPolicy.normalizeUsername("用户abc"), "含中文");
            assertRejected(() -> UserInputPolicy.normalizeUsername("abc中文"), "尾部中文");
            assertRejected(() -> UserInputPolicy.normalizeUsername("\uD83D\uDE00abc"), "含 emoji");
            // 下划线属于合法字符：边界验证避免把合法账号误判为非法。
            assertThat(UserInputPolicy.normalizeUsername("ABC_DEF_")).isEqualTo("abc_def_");
        }

        @Test
        @DisplayName("拒绝文案不包含被拒绝的原始值")
        void rejectionMessageDoesNotEchoInput() {
            String rejected = "Bad Name With Space";
            ApiException exception = rejection(() -> UserInputPolicy.normalizeUsername(rejected));
            assertThat(exception.getMessage()).doesNotContain(rejected).doesNotContain("Bad Name");
        }
    }

    // ---------------------------------------------------------------------
    // 密码：码点 vs UTF-8 字节，全空白，不 trim
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("validatePassword")
    class ValidatePassword {

        @Test
        @DisplayName("合法密码通过，且不 trim：带首尾空格的密码原样合法")
        void acceptsValidPasswordsWithoutTrimming() {
            assertThatCode(() -> UserInputPolicy.validatePassword("M1-Auth-2026-Secret"))
                    .as("常规强密码应通过").doesNotThrowAnyException();
            assertThatCode(() -> UserInputPolicy.validatePassword("12345678"))
                    .as("恰好 8 个 ASCII 码点应通过").doesNotThrowAnyException();
            assertThatCode(() -> UserInputPolicy.validatePassword("  Abcd1234  "))
                    .as("策略不 trim，首尾空格计入长度且不影响通过").doesNotThrowAnyException();
            assertThatCode(() -> UserInputPolicy.validatePassword("密 码 不 trim"))
                    .as("内嵌空格的多字节密码应通过").doesNotThrowAnyException();
        }

        @Test
        @DisplayName("拒绝 null 与全空白（含制表符、全角空格）")
        void rejectsNullAndBlank() {
            assertRejected(() -> UserInputPolicy.validatePassword(null), "null 密码");
            assertRejected(() -> UserInputPolicy.validatePassword(""), "空密码");
            assertRejected(() -> UserInputPolicy.validatePassword("        "), "8 个空格");
            assertRejected(() -> UserInputPolicy.validatePassword("\t\t\t\t\t\t"), "6 个制表符");
            assertRejected(() -> UserInputPolicy.validatePassword("\n\n\n\n\n\n\n\n\n"), "9 个换行");
            assertRejected(() -> UserInputPolicy.validatePassword("\u3000\u3000\u3000\u3000\u3000\u3000\u3000\u3000"),
                    "8 个全角空格");
        }

        @Test
        @DisplayName("按 Unicode 码点而不是 UTF-16 单元或字节计数：7 码点拒绝、8 码点通过")
        void countsUnicodeCodePoints() {
            // 6 个 ASCII + 1 个 emoji（2 个 UTF-16 单元、1 个码点）= 7 码点。
            String sevenCodePoints = "A1b2C3\uD83D\uDE00";
            assertThat(sevenCodePoints.codePointCount(0, sevenCodePoints.length())).isEqualTo(7);
            assertRejected(() -> UserInputPolicy.validatePassword(sevenCodePoints), "7 码点密码");

            // 6 个 ASCII + emoji(1) + 汉字(1) = 8 码点。
            String eightCodePoints = "A1b2c3\uD83D\uDE00\u4E2D";
            assertThat(eightCodePoints.codePointCount(0, eightCodePoints.length())).isEqualTo(8);
            assertThatCode(() -> UserInputPolicy.validatePassword(eightCodePoints))
                    .as("恰好 8 个码点（含 emoji/汉字）应通过").doesNotThrowAnyException();
        }

        @Test
        @DisplayName("UTF-8 72 字节边界：72 字节通过，73 字节拒绝")
        void enforcesUtf8ByteLimit() {
            String ascii72 = "a".repeat(PASSWORD_MAX_UTF8_BYTES);
            assertThat(ascii72.getBytes(StandardCharsets.UTF_8)).hasSize(72);
            assertThatCode(() -> UserInputPolicy.validatePassword(ascii72))
                    .as("72 个 ASCII 字节应通过").doesNotThrowAnyException();

            String ascii73 = "a".repeat(PASSWORD_MAX_UTF8_BYTES + 1);
            assertRejected(() -> UserInputPolicy.validatePassword(ascii73), "73 个 ASCII 字节");

            // 24 个汉字 × 3 字节 = 72 字节，码点数 24 >= 8。
            String cjk72Bytes = "密".repeat(24);
            assertThat(cjk72Bytes.getBytes(StandardCharsets.UTF_8)).hasSize(72);
            assertThat(cjk72Bytes.codePointCount(0, cjk72Bytes.length())).isEqualTo(24);
            assertThatCode(() -> UserInputPolicy.validatePassword(cjk72Bytes))
                    .as("72 字节的多字节密码应通过").doesNotThrowAnyException();

            // 25 个汉字 × 3 字节 = 75 字节，虽码点数足够，仍必须因字节超限被拒绝。
            String cjk75Bytes = "密".repeat(25);
            assertThat(cjk75Bytes.getBytes(StandardCharsets.UTF_8)).hasSize(75);
            assertRejected(() -> UserInputPolicy.validatePassword(cjk75Bytes), "75 个 UTF-8 字节");

            // 18 个 emoji × 4 字节 = 72 字节，18 个码点，边界内通过。
            String emoji72Bytes = "\uD83D\uDE00".repeat(18);
            assertThat(emoji72Bytes.getBytes(StandardCharsets.UTF_8)).hasSize(72);
            assertThatCode(() -> UserInputPolicy.validatePassword(emoji72Bytes))
                    .as("72 字节的 emoji 密码应通过").doesNotThrowAnyException();

            // 19 个 emoji × 4 字节 = 76 字节，必须拒绝。
            String emoji76Bytes = "\uD83D\uDE00".repeat(19);
            assertThat(emoji76Bytes.getBytes(StandardCharsets.UTF_8)).hasSize(76);
            assertRejected(() -> UserInputPolicy.validatePassword(emoji76Bytes), "76 个 UTF-8 字节");
        }

        @Test
        @DisplayName("拒绝文案不回显密码")
        void rejectionMessageDoesNotEchoPassword() {
            String tooLong = "Zz9-" + "x".repeat(70) + "-secret";
            ApiException exception = rejection(() -> UserInputPolicy.validatePassword(tooLong));
            assertThat(exception.getMessage()).doesNotContain(tooLong).doesNotContain("Zz9-");
        }
    }

    // ---------------------------------------------------------------------
    // 昵称：strip 后 1–50 个 Unicode 码点
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("normalizeNickname")
    class NormalizeNickname {

        @Test
        @DisplayName("strip 首尾空白，保留内部空白与大小写")
        void stripsOuterWhitespace() {
            assertThat(UserInputPolicy.normalizeNickname("  旅行者  ")).isEqualTo("旅行者");
            assertThat(UserInputPolicy.normalizeNickname("\tAlice Smith\n")).isEqualTo("Alice Smith");
            assertThat(UserInputPolicy.normalizeNickname("\u3000小 旅\u3000")).isEqualTo("小 旅");
        }

        @Test
        @DisplayName("码点边界：1 与 50 个码点通过（emoji 按 1 个码点计）")
        void acceptsCodePointBoundaries() {
            assertThat(UserInputPolicy.normalizeNickname("旅")).isEqualTo("旅");
            String fiftyAscii = "n".repeat(50);
            assertThat(UserInputPolicy.normalizeNickname(fiftyAscii)).isEqualTo(fiftyAscii);
            // 50 个 emoji = 50 码点 / 100 个 UTF-16 单元 / 200 字节，必须按码点判定通过。
            String fiftyEmoji = "\uD83D\uDE00".repeat(50);
            assertThat(fiftyEmoji.codePointCount(0, fiftyEmoji.length())).isEqualTo(50);
            assertThat(fiftyEmoji.length()).isEqualTo(100);
            assertThat(UserInputPolicy.normalizeNickname(fiftyEmoji)).isEqualTo(fiftyEmoji);
            // 首尾空白不计入长度。
            String paddedFifty = "  " + "N".repeat(50) + "  ";
            assertThat(UserInputPolicy.normalizeNickname(paddedFifty)).isEqualTo("N".repeat(50));
        }

        @Test
        @DisplayName("拒绝 null、空、全空白与超过 50 个码点")
        void rejectsInvalidNicknames() {
            assertRejected(() -> UserInputPolicy.normalizeNickname(null), "null 昵称");
            assertRejected(() -> UserInputPolicy.normalizeNickname(""), "空昵称");
            assertRejected(() -> UserInputPolicy.normalizeNickname("   "), "全空白昵称");
            assertRejected(() -> UserInputPolicy.normalizeNickname("\u3000\t\n"), "全为 Unicode 空白");
            assertRejected(() -> UserInputPolicy.normalizeNickname("n".repeat(51)), "51 个 ASCII 码点");
            assertRejected(() -> UserInputPolicy.normalizeNickname("长".repeat(51)), "51 个汉字码点");
            assertRejected(() -> UserInputPolicy.normalizeNickname("\uD83D\uDE00".repeat(51)), "51 个 emoji 码点");
        }
    }

    // ---------------------------------------------------------------------
    // 简介：null 保持 null，否则最多 300 个码点，不强制 trim
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("normalizeBio")
    class NormalizeBio {

        @Test
        @DisplayName("null 保持 null，其余不 trim、不改写")
        void keepsNullAndDoesNotTrim() {
            assertThat(UserInputPolicy.normalizeBio(null)).isNull();
            assertThat(UserInputPolicy.normalizeBio("")).as("空串是合法的 0 码点简介").isEmpty();
            String padded = "  前后空格应保留  ";
            assertThat(UserInputPolicy.normalizeBio(padded)).isEqualTo(padded);
            String multiline = "第一行\n第二行\r\n第三行";
            assertThat(UserInputPolicy.normalizeBio(multiline)).isEqualTo(multiline);
        }

        @Test
        @DisplayName("码点边界：300 通过（含 emoji），301 拒绝")
        void enforcesCodePointLimit() {
            String ascii300 = "b".repeat(300);
            assertThat(UserInputPolicy.normalizeBio(ascii300)).isEqualTo(ascii300);
            assertRejected(() -> UserInputPolicy.normalizeBio("b".repeat(301)), "301 个 ASCII 码点");

            String emoji300 = "\uD83D\uDE00".repeat(300);
            assertThat(emoji300.codePointCount(0, emoji300.length())).isEqualTo(300);
            assertThat(emoji300.length()).as("300 个 emoji 是 600 个 UTF-16 单元").isEqualTo(600);
            assertThat(UserInputPolicy.normalizeBio(emoji300)).isEqualTo(emoji300);
            assertRejected(() -> UserInputPolicy.normalizeBio("\uD83D\uDE00".repeat(301)), "301 个 emoji 码点");
        }
    }

    // ---------------------------------------------------------------------
    // 断言助手：只校验真实抛出的 ApiException 契约
    // ---------------------------------------------------------------------

    private static ApiException rejection(Runnable action) {
        Throwable thrown = catchThrowable(action::run);
        assertThat(thrown).as("非法输入必须抛出 ApiException").isInstanceOf(ApiException.class);
        ApiException exception = (ApiException) thrown;
        assertThat(exception.getErrorCode()).as("非法输入的业务码必须是 VALIDATION_FAILED")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(exception.getMessage()).as("非法输入必须带安全中文文案").isNotBlank();
        assertThat(exception.getMessage()).as("文案不得包含堆栈或 SQL 片段")
                .doesNotContain("Exception").doesNotContain("SELECT");
        return exception;
    }

    private static void assertRejected(Runnable action, String description) {
        Throwable thrown = catchThrowable(action::run);
        assertThat(thrown).as(description + " 必须被拒绝").isInstanceOf(ApiException.class);
        assertionOfRejection(description, (ApiException) thrown);
    }

    private static void assertionOfRejection(String description, ApiException exception) {
        assertThat(exception.getErrorCode()).as(description + " 的业务码").isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(exception.getMessage()).as(description + " 的文案").isNotBlank();
    }
}
