package com.example.travelplatform.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 资料部分更新请求（PATCH /api/v1/users/me）。
 *
 * <p>只允许 nickname、avatarUrl、bio 三个字段，且必须区分「字段缺失」与「显式 null」：
 * 缺失表示保持原值，{@code bio=null}/{@code avatarUrl=null} 表示清空。
 * 因此这里使用普通 getter/setter + 字段存在标记，而不是 nullable record
 * （record 的 null 无法与缺失区分）。
 *
 * <p>存在标记只供 Service 内部读取，并用 {@link JsonIgnore} 标注；配合全局
 * {@code fail-on-ignored-properties=true}，客户端若注入 {@code nicknamePresent} 之类的
 * 标记字段会直接得到 400，无法伪造「字段出现过」。
 *
 * <p>setter 只要被 Jackson 调用（含显式 null）就把对应标记置为 true；三个标记一旦为 true
 * 就不会回退，保证同名字段重复出现时仍按「出现过」处理。
 */
public class ProfilePatchRequest {

    private String nickname;

    private String avatarUrl;

    private String bio;

    private boolean nicknamePresent;

    private boolean avatarUrlPresent;

    private boolean bioPresent;

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
        this.nicknamePresent = true;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
        this.avatarUrlPresent = true;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
        this.bioPresent = true;
    }

    /** 内部读取：nickname 是否出现在请求中（显式 null 也为 true）。 */
    @JsonIgnore
    public boolean isNicknamePresent() {
        return nicknamePresent;
    }

    /** 内部读取：avatarUrl 是否出现在请求中（显式 null 也为 true）。 */
    @JsonIgnore
    public boolean isAvatarUrlPresent() {
        return avatarUrlPresent;
    }

    /** 内部读取：bio 是否出现在请求中（显式 null 也为 true）。 */
    @JsonIgnore
    public boolean isBioPresent() {
        return bioPresent;
    }
}
