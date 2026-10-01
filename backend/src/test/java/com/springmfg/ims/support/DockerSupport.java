package com.springmfg.ims.support;

import org.testcontainers.DockerClientFactory;

public final class DockerSupport {

    private DockerSupport() {
    }

    /** Returns true only when the Docker engine is fully initialized (NCPU > 0). */
    public static boolean isAvailable() {
        try {
            com.github.dockerjava.api.model.Info info =
                    DockerClientFactory.instance().client().infoCmd().exec();
            return info.getNCPU() != null && info.getNCPU() > 0;
        } catch (Throwable t) {
            return false;
        }
    }
}
