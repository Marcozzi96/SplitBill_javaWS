package it.javaWS.models.dto;

import it.javaWS.models.entities.User;
import lombok.Data;

@Data
public class UserDTO {
	private Long userId;
    private String username;
    private String email;
    // false per gli utenti registrati via Google finché non impostano una password
    private boolean hasPassword;
    
    public UserDTO(User user) {
    	this.userId = user.getId();
    	this.username = user.getUsername();
    	this.email = user.getEmail();
    	this.hasPassword = user.getPassword() != null;
    }
}
