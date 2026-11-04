package com.example.travelplatform.security;

import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.common.error.ErrorCode;
import com.example.travelplatform.user.entity.User;
import com.example.travelplatform.user.service.UserService;
import org.springframework.stereotype.Component;

@Component
public class AdminAccess {
    private final UserService users;
    public AdminAccess(UserService users) { this.users = users; }

    public User require() {
        User user = users.requireCurrentUser();
        if (!"ADMIN".equals(user.getRole())) throw new ApiException(ErrorCode.FORBIDDEN);
        return user;
    }
}
