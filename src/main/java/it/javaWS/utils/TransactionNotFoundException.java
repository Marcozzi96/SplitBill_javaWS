package it.javaWS.utils;

import java.io.Serial;

public class TransactionNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TransactionNotFoundException(String message) {
        super(message);
    }
}
