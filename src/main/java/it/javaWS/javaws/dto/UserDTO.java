package it.javaWS.javaws.dto;

import it.javaWS.javaws.models.User;
import lombok.Data;

@Data
public class UserDTO {
	private Long userId;
    private String username;
    private String email;
    
    public UserDTO(User user) {
    	this.userId = user.getId();
    	this.username = user.getUsername();
    	this.email = user.getEmail();
    }
}
