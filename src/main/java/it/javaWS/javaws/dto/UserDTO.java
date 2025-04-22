package it.javaWS.javaws.dto;

import java.time.LocalDate;

import it.javaWS.javaws.models.User;
import lombok.Data;

@Data
public class UserDTO {
	private Long id;
    private String username;
    private String email;
    private LocalDate startDate; //Data di ingresso nel gruppo interpellato
    private LocalDate endDate; //Data di uscita dal gruppo interpellato
    private LocalDate regDate; //Data di registrazione dell'utente
    
    public UserDTO(User user) {
    	this.id = user.getId();
    	this.username = user.getUsername();
    	this.email = user.getEmail();
    	this.regDate = user.getRegDate();
    }
}
