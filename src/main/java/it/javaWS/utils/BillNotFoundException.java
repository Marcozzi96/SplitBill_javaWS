package it.javaWS.utils;

import java.io.Serial;

public class BillNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BillNotFoundException(String message) {
        super(message);
    }
}
