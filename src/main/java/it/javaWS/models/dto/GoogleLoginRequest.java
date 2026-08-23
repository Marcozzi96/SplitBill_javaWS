package it.javaWS.models.dto;

import lombok.Data;

@Data
public class GoogleLoginRequest {
    private String idToken;

    public GoogleLoginRequest() {}

    public GoogleLoginRequest(String idToken) {
        this.idToken = idToken;
    }
}
