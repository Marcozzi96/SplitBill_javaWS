package it.javaWS.utils;

import java.io.Serial;

public class PendingSettlementsException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PendingSettlementsException(String message) {
        super(message);
    }
}
