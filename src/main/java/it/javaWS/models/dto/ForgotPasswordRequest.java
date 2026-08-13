package it.javaWS.models.dto;

import lombok.Data;

@Data
public class ForgotPasswordRequest {
    private String email;

    public ForgotPasswordRequest() {}

    public ForgotPasswordRequest(String email) {
        this.email = email;
    }
}
