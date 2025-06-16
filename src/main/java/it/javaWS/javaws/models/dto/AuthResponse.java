package it.javaWS.javaws.models.dto;


public class AuthResponse {
    private String token;
    private UserDTO user;
    
    public AuthResponse(String token, UserDTO user) { 
    	this.token = token; 
    	this.user = user;
    }
    public String getToken() { 
    	return token; 
    }
    public UserDTO getUser() {
    	return user;
    }
}