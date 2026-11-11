package com.example.travelplatform.user.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.common.error.ErrorCode;
import com.example.travelplatform.common.validation.RequestValues;
import com.example.travelplatform.file.ManagedImagePaths;
import com.example.travelplatform.user.dto.LoginRequest;
import com.example.travelplatform.user.dto.ProfilePatchRequest;
import com.example.travelplatform.user.dto.RegisterRequest;
import com.example.travelplatform.user.entity.User;
import com.example.travelplatform.user.mapper.UserMapper;
import com.example.travelplatform.user.validation.UserInputPolicy;
import com.example.travelplatform.user.vo.CurrentUserVO;
import com.example.travelplatform.user.vo.LoginVO;
import com.example.travelplatform.user.vo.PublicProfileVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Service
public class UserService {
    private final UserMapper users;
    private final PasswordEncoder passwords;
    private final ManagedImagePaths images;
    private final String dummyHash;

    public UserService(UserMapper users, PasswordEncoder passwords, ManagedImagePaths images) {
        this.users = users;
        this.passwords = passwords;
        this.images = images;
        this.dummyHash = passwords.encode("unavailable-account-placeholder");
    }

    @Transactional
    public CurrentUserVO register(RegisterRequest request) {
        String username = UserInputPolicy.normalizeUsername(request.username());
        UserInputPolicy.validatePassword(request.password());
        String nickname = request.nickname() == null ? username
                : UserInputPolicy.normalizeNickname(request.nickname());
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwords.encode(request.password()));
        user.setNickname(nickname);
        user.setRole("USER");
        user.setStatus("ACTIVE");
        user.setIsDeleted(0);
        user.setCreatedAt(now());
        user.setUpdatedAt(user.getCreatedAt());
        // The unique key includes logically deleted usernames and arbitrates concurrent registrations.
        users.insert(user);
        return CurrentUserVO.from(users.selectById(user.getId()));
    }

    public LoginVO login(LoginRequest request) {
        if (request.username() == null || request.password() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "请提供账号和密码");
        }
        String username;
        try {
            username = UserInputPolicy.normalizeUsername(request.username());
            UserInputPolicy.validatePassword(request.password());
        } catch (ApiException exception) {
            throw new ApiException(ErrorCode.BAD_CREDENTIALS);
        }
        User user = users.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        boolean matches = passwords.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
        if (!matches || !active(user)) throw new ApiException(ErrorCode.BAD_CREDENTIALS);
        StpUtil.login(user.getId());
        return new LoginVO(LoginVO.TOKEN_NAME, StpUtil.getTokenValue(), CurrentUserVO.from(user));
    }

    public User requireCurrentUser() {
        StpUtil.checkLogin();
        User user = users.selectById(StpUtil.getLoginIdAsLong());
        if (!active(user)) throw new ApiException(ErrorCode.UNAUTHORIZED);
        return user;
    }

    public User requireAdministrator() {
        User user=requireCurrentUser();
        if(!"ADMIN".equals(user.getRole()))throw new ApiException(ErrorCode.FORBIDDEN);
        return user;
    }

    public CurrentUserVO current() {
        return CurrentUserVO.from(requireCurrentUser());
    }

    public PublicProfileVO publicProfile(String id) {
        User user=users.selectById(RequestValues.positiveId(id));
        if(user==null)throw new ApiException(ErrorCode.NOT_FOUND);
        return PublicProfileVO.from(user);
    }

    /** Public creator lookup never returns disabled or logically deleted accounts. */
    public User publicUserByUsername(String rawUsername) {
        String username=UserInputPolicy.normalizeUsername(rawUsername);
        User user=users.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername,username));
        if(!active(user))throw new ApiException(ErrorCode.NOT_FOUND);
        return user;
    }

    @Transactional
    public CurrentUserVO updateProfile(ProfilePatchRequest request) {
        User user = requireCurrentUser();
        if (!request.isNicknamePresent() && !request.isAvatarUrlPresent() && !request.isBioPresent()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "请至少修改一项资料");
        }
        var update = Wrappers.<User>lambdaUpdate().eq(User::getId, user.getId())
                .eq(User::getStatus, "ACTIVE").set(User::getUpdatedAt, now());
        if (request.isNicknamePresent()) {
            update.set(User::getNickname, UserInputPolicy.normalizeNickname(request.getNickname()));
        }
        if (request.isBioPresent()) {
            update.set(User::getBio, UserInputPolicy.normalizeBio(request.getBio()));
        }
        if (request.isAvatarUrlPresent()) update.set(User::getAvatarUrl, images.validate(request.getAvatarUrl()));
        // Explicit SET preserves PATCH null-clearing instead of MyBatis' default null omission.
        if (users.update(null, update) != 1) throw new ApiException(ErrorCode.UNAUTHORIZED);
        return CurrentUserVO.from(users.selectById(user.getId()));
    }

    public void logout() {
        requireCurrentUser();
        StpUtil.logoutByTokenValue(StpUtil.getTokenValue());
    }

    private static boolean active(User user) {
        return user != null && "ACTIVE".equals(user.getStatus()) && Integer.valueOf(0).equals(user.getIsDeleted());
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MILLIS);
    }
}
