package com.example.travelplatform.comment.validation;

import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.common.error.ErrorCode;

/**
 * 评论纯文本输入规范化策略。
 *
 * <p>顺序固定，任何一步都不能提前改写输入以免吞掉应拒绝的字符：
 * <ol>
 *   <li>按 Unicode 码点拒绝 C0/C1 控制字符（{@link Character#isISOControl(int)}），
 *       仅放行 TAB、LF、CR；U+0085 等 C1 控制符同样拒绝。</li>
 *   <li>CRLF 与孤立 CR 统一为 LF。</li>
 *   <li>去除首尾 Unicode 空白（含 NBSP、全角空格、U+2007、U+202F）以及 U+200B、U+FEFF；
 *       内部字符与内部空白保持原样，U+200C/U+200D 与组合字符保留。</li>
 *   <li>按码点计数校验长度 1–2000，不按 UTF-16 {@code length()} 计算，
 *       因此 emoji 记 1 个码点、组合序列中的基础字符与组合符分别计数。</li>
 * </ol>
 *
 * <p>本类只做纯文本规范化，不解析、不转义、不替换 HTML 或 SQL 样式文本：
 * 片段原样返回，由前端 Vue 转义与 Mapper 参数化查询承担安全职责。
 * 所有非法输入统一抛出 {@link ErrorCode#VALIDATION_FAILED}，且不回显原始值。
 */
public final class CommentInputPolicy {

    private static final int MIN_CODE_POINTS = 1;
    private static final int MAX_CODE_POINTS = 2000;

    private CommentInputPolicy() {
        throw new AssertionError("工具类禁止实例化");
    }

    /**
     * 规范化评论纯文本。
     *
     * @param raw 原始评论文本，可为 {@code null}
     * @return 规范化后的非空文本，长度按码点计 1–2000
     * @throws ApiException 输入为 {@code null}、仅含不可见边界空白、含非允许控制字符
     *                      或码点长度越界时抛出，业务码固定为 {@link ErrorCode#VALIDATION_FAILED}
     */
    public static String normalize(String raw) {
        if (raw == null) {
            throw rejected();
        }

        // 1. 先拒绝控制字符，避免第 3 步的 strip 把应拒绝的字符当作空白吞掉。
        rejectUnsupportedControls(raw);

        // 2. 换行规范化：CRLF 与孤立 CR 统一为 LF。
        String unified = unifyLineEndings(raw);

        // 3. 去除首尾空白类字符，内部内容保持不变。
        int start = 0;
        int end = unified.length();
        while (start < end) {
            int cp = unified.codePointAt(start);
            if (!isBoundaryTrimmable(cp)) {
                break;
            }
            start += Character.charCount(cp);
        }
        while (end > start) {
            int cp = unified.codePointBefore(end);
            if (!isBoundaryTrimmable(cp)) {
                break;
            }
            end -= Character.charCount(cp);
        }
        String normalized = unified.substring(start, end);

        // 4. 按码点校验长度，边界空白不计入内容长度。
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints < MIN_CODE_POINTS || codePoints > MAX_CODE_POINTS) {
            throw rejected();
        }
        return normalized;
    }

    private static void rejectUnsupportedControls(String raw) {
        int index = 0;
        int length = raw.length();
        while (index < length) {
            int cp = raw.codePointAt(index);
            if (isUnsupportedControl(cp)) {
                throw rejected();
            }
            index += Character.charCount(cp);
        }
    }

    private static boolean isUnsupportedControl(int codePoint) {
        if (codePoint == '\t' || codePoint == '\n' || codePoint == '\r') {
            return false;
        }
        return Character.isISOControl(codePoint);
    }

    private static String unifyLineEndings(String raw) {
        StringBuilder builder = null;
        int index = 0;
        int length = raw.length();
        while (index < length) {
            char current = raw.charAt(index);
            if (current == '\r') {
                if (builder == null) {
                    builder = new StringBuilder(length).append(raw, 0, index);
                }
                builder.append('\n');
                index++;
                if (index < length && raw.charAt(index) == '\n') {
                    index++;
                }
                continue;
            }
            if (builder != null) {
                builder.append(current);
            }
            index++;
        }
        return builder == null ? raw : builder.toString();
    }

    /**
     * 首尾可去除的空白类字符：Unicode 空白（{@code isWhitespace} 或 {@code isSpaceChar}，
     * 覆盖 NBSP/U+2007/U+202F 与全角空格）以及零宽空格 U+200B、BOM U+FEFF。
     */
    private static boolean isBoundaryTrimmable(int codePoint) {
        return codePoint == 0x200B
                || codePoint == 0xFEFF
                || Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint);
    }

    /** 统一构造不带原始值的校验失败异常，避免回显用户输入。 */
    private static ApiException rejected() {
        return new ApiException(ErrorCode.VALIDATION_FAILED);
    }
}
