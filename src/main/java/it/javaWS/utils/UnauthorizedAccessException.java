package it.javaWS.utils;

import java.io.Serial;

public class UnauthorizedAccessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public UnauthorizedAccessException(String message) {
        super(message);
    }
}
