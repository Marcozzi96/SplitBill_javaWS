package it.javaWS.models.dto;

import java.time.LocalDateTime;

import it.javaWS.enums.StatoAmicizia;
import it.javaWS.models.entities.Friendship;
import lombok.Data;

@Data
public class FriendshipReqSenDTO {
	private Long friendshipId;
	private UserDTO recipient;
	private StatoAmicizia stato;
	private LocalDateTime dataRichiesta;
	private String messaggio;
	
	public FriendshipReqSenDTO(Friendship friendship) {
		this.friendshipId = friendship.getId();
		this.recipient = new UserDTO(friendship.getUserToBeConfirmed());
		this.stato = friendship.getStato();
		this.dataRichiesta = friendship.getDataRichiesta();
		this.messaggio = friendship.getMessaggio();
	}
}
