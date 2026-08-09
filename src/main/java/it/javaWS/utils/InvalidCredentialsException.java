package it.javaWS.utils;

import java.io.Serial;

public class InvalidCredentialsException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
