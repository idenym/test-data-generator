package com.testdatagen.security;

/**
 * 通过 ThreadLocal 持有当前请求的登录用户信息。
 * 在拦截器中设置，在 Service/Controller 中读取。
 */
public class CurrentUserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 获取当前用户 ID，未登录返回 null
     */
    public static Long getUserId() {
        CurrentUser user = HOLDER.get();
        return user != null ? user.getId() : null;
    }

    /**
     * 判断当前用户是否是管理员
     */
    public static boolean isAdmin() {
        CurrentUser user = HOLDER.get();
        return user != null && user.isAdmin();
    }
}
