package com.example.travelplatform.user.validation;

import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.common.error.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * M1 用户输入策略：账号规范化、密码强度、昵称与简介边界。
 *
 * <p>所有方法对非法输入统一抛出 {@link ApiException}({@link ErrorCode#VALIDATION_FAILED})，
 * 消息为面向用户的安全中文文案，<b>不回显被拒绝的原始值</b>，也不包含 SQL、路径或堆栈。
 *
 * <p>约定：
 * <ul>
 *   <li>账号：strip 后按 {@link Locale#ROOT} 转小写，再校验 ASCII {@code [a-z0-9_]{3,50}}；</li>
 *   <li>密码：不 trim；非 null、非全空白、至少 8 个 Unicode 码点、UTF-8 不超过 72 字节（BCrypt 有效长度）；</li>
 *   <li>昵称：strip 首尾空白后 1–50 个 Unicode 码点；</li>
 *   <li>简介：null 保持 null，其余不强制 trim，最多 300 个 Unicode 码点。</li>
 * </ul>
 *
 * <p>长度一律按 Unicode 码点而不是 UTF-16 单元或字节计数，避免 emoji/汉字被误判。
 */
public final class UserInputPolicy {

    /** 账号允许的字符与长度：规范化后的 ASCII 小写字母、数字与下划线。 */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9_]{3,50}$");

    /** 密码最少 Unicode 码点数。 */
    private static final int PASSWORD_MIN_CODE_POINTS = 8;

    /** 密码最多 UTF-8 字节数：BCrypt 只使用前 72 字节，超出必须拒绝而不是静默截断。 */
    private static final int PASSWORD_MAX_UTF8_BYTES = 72;

    /** 昵称最少 Unicode 码点数。 */
    private static final int NICKNAME_MIN_CODE_POINTS = 1;

    /** 昵称最多 Unicode 码点数。 */
    private static final int NICKNAME_MAX_CODE_POINTS = 50;

    /** 简介最多 Unicode 码点数。 */
    private static final int BIO_MAX_CODE_POINTS = 300;

    private static final String USERNAME_INVALID_MESSAGE = "账号格式不正确：需为 3–50 位字母、数字或下划线";
    private static final String PASSWORD_REQUIRED_MESSAGE = "密码不能为空，也不能全部为空白字符";
    private static final String PASSWORD_TOO_SHORT_MESSAGE = "密码至少需要 8 个字符";
    private static final String PASSWORD_TOO_LONG_MESSAGE = "密码过长，请控制在 72 字节以内";
    private static final String NICKNAME_INVALID_MESSAGE = "昵称去首尾空白后需为 1–50 个字符";
    private static final String BIO_TOO_LONG_MESSAGE = "简介最多 300 个字符";

    private UserInputPolicy() {
    }

    /**
     * 规范化账号：strip 首尾空白 → {@link Locale#ROOT} 小写 → 校验 ASCII {@code [a-z0-9_]{3,50}}。
     *
     * @param username 原始账号，可为 null
     * @return 规范化后的账号
     * @throws ApiException 账号为 null 或规范化后不符合规则
     */
    public static String normalizeUsername(String username) {
        if (username == null) {
            throw invalid(USERNAME_INVALID_MESSAGE);
        }
        String normalized = username.strip().toLowerCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            throw invalid(USERNAME_INVALID_MESSAGE);
        }
        return normalized;
    }

    /**
     * 校验密码：不 trim，只做非空、非全空白、码点下限与 UTF-8 字节上限检查。
     *
     * @param password 原始密码，可为 null
     * @throws ApiException 密码为 null、全空白、少于 8 个码点或超过 72 字节
     */
    public static void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw invalid(PASSWORD_REQUIRED_MESSAGE);
        }
        if (codePointLength(password) < PASSWORD_MIN_CODE_POINTS) {
            throw invalid(PASSWORD_TOO_SHORT_MESSAGE);
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_UTF8_BYTES) {
            throw invalid(PASSWORD_TOO_LONG_MESSAGE);
        }
    }

    /**
     * 规范化昵称：strip 首尾空白后校验 1–50 个 Unicode 码点，保留内部空白与大小写。
     *
     * @param nickname 原始昵称，可为 null
     * @return strip 后的昵称
     * @throws ApiException 昵称为 null、空、全空白或超过 50 个码点
     */
    public static String normalizeNickname(String nickname) {
        if (nickname == null) {
            throw invalid(NICKNAME_INVALID_MESSAGE);
        }
        String stripped = nickname.strip();
        int codePoints = codePointLength(stripped);
        if (codePoints < NICKNAME_MIN_CODE_POINTS || codePoints > NICKNAME_MAX_CODE_POINTS) {
            throw invalid(NICKNAME_INVALID_MESSAGE);
        }
        return stripped;
    }

    /**
     * 规范化简介：null 保持 null，其余不 trim（换行与内部空格原样保留），最多 300 个码点。
     *
     * @param bio 原始简介，可为 null
     * @return 原样的简介或 null
     * @throws ApiException 超过 300 个码点
     */
    public static String normalizeBio(String bio) {
        if (bio == null) {
            return null;
        }
        if (codePointLength(bio) > BIO_MAX_CODE_POINTS) {
            throw invalid(BIO_TOO_LONG_MESSAGE);
        }
        return bio;
    }

    private static int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }
}
