package com.springmfg.ims.auth;

/** Where a request came from; stored with refresh tokens and passed to audit events. */
record ClientInfo(String ip, String userAgent) {

    private static final int MAX_USER_AGENT = 200;

    static ClientInfo of(String ip, String userAgent) {
        String ua = userAgent == null ? null
                : (userAgent.length() > MAX_USER_AGENT ? userAgent.substring(0, MAX_USER_AGENT) : userAgent);
        return new ClientInfo(ip, ua);
    }
}
