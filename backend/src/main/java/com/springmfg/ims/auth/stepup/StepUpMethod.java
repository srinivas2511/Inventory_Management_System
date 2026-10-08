package com.springmfg.ims.auth.stepup;

/** One way to prove who is at the keyboard again. Password today; an MFA method is another implementation. */
public interface StepUpMethod {

    /** The value of {@code security.stepup.method} that selects this method. */
    String name();

    boolean verify(Long userId, String credential);
}
