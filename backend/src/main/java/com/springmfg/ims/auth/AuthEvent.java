package com.springmfg.ims.auth;

/**
 * Published for every security-relevant authentication outcome. The audit module (task 1.6) turns these into
 * {@code audit_logs} rows; nothing here carries a password or token.
 */
public record AuthEvent(Type type, Long userId, String username, String ip, String detail) {

    public enum Type {
        LOGIN_SUCCESS, LOGIN_FAILURE, ACCOUNT_LOCKED, PASSWORD_CHANGE_REQUIRED, LOGOUT, TOKEN_REUSE_DETECTED,
        PASSWORD_CHANGED, PASSWORD_RESET_REQUESTED, PASSWORD_RESET_COMPLETED
    }
}
