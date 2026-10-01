package com.springmfg.ims.auth;

import java.util.List;

/** The principal of an authenticated request: who, with which roles, at which permission version. */
public record AuthenticatedUser(long id, String username, List<String> roles, int permissionVersion) {
}
