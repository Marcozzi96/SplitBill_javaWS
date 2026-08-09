package it.javaWS.utils;

import java.io.Serial;

public class InvalidBillException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidBillException(String message) {
        super(message);
    }
}
