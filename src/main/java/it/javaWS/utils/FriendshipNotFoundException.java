package it.javaWS.utils;

import java.io.Serial;

public class FriendshipNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public FriendshipNotFoundException(String message) {
        super(message);
    }
}
