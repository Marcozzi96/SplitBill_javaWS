package it.javaWS.javaws.models.dto;

import lombok.Data;

@Data
public class AuthRequest {
    private String username;
    private String email;
    private String password;
    
    public AuthRequest() {}
    
    public AuthRequest(String username, String password) {
    	this.username = username;
    	this.password = password;
    }
    
    public AuthRequest(String username, String password, String email) {
    	this.username = username;
    	this.password = password;
    	this.email = email;
    }
}
