package com.springmfg.ims.common.exception;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String entity, Object id) {
        super(entity + " not found: " + id);
    }
}
