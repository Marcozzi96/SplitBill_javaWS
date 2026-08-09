package it.javaWS.utils;

import java.io.Serial;

public class NotGroupAdminException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public NotGroupAdminException(String message) {
        super(message);
    }
}
