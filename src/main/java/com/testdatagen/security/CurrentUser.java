package com.testdatagen.security;

import com.testdatagen.model.enums.UserRole;

/**
 * 当前登录用户信息 DTO，存储在 ThreadLocal 中。
 */
public class CurrentUser {

    private final Long id;
    private final String username;
    private final UserRole role;

    public CurrentUser(Long id, String username, UserRole role) {
        this.id = id;
        this.username = username;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public UserRole getRole() { return role; }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
