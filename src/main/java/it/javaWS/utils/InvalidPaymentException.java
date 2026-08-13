package it.javaWS.utils;

import java.io.Serial;

public class InvalidPaymentException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidPaymentException(String message) {
        super(message);
    }
}
