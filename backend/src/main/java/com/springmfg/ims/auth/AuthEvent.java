package com.springmfg.ims.auth;

import java.util.List;

/**
 * Published for every security-relevant authentication outcome. The audit module (task 1.6) turns these into
 * {@code audit_logs} rows; nothing here carries a password or token. {@code roles} are the roles the user held at
 * that moment (empty when the user is unknown).
 */
public record AuthEvent(Type type, Long userId, String username, List<String> roles, String ip, String detail) {

    public enum Type {
        LOGIN_SUCCESS, LOGIN_FAILURE, ACCOUNT_LOCKED, PASSWORD_CHANGE_REQUIRED, LOGOUT, TOKEN_REUSE_DETECTED,
        PASSWORD_CHANGED, PASSWORD_RESET_REQUESTED, PASSWORD_RESET_COMPLETED
    }
}
