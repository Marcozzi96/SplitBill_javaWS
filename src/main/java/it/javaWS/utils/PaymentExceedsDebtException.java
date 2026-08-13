package it.javaWS.utils;

import java.io.Serial;

public class PaymentExceedsDebtException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PaymentExceedsDebtException(String message) {
        super(message);
    }
}
