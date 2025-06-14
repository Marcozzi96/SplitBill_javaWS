package it.javaWS.javaws.dto;

import java.time.LocalDateTime;

import it.javaWS.javaws.enums.StatoAmicizia;
import it.javaWS.javaws.models.Friendship;
import lombok.Data;

@Data
public class FriendshipReqSenDTO {
	private Long friendshipId;
	private UserDTO recipient;
	private StatoAmicizia stato;
	private LocalDateTime dataRichiesta;
	
	public FriendshipReqSenDTO(Friendship friendship) {
		this.friendshipId = friendship.getId();
		this.recipient = new UserDTO(friendship.getUserToBeConfirmed());
		this.stato = friendship.getStato();
		this.dataRichiesta = friendship.getDataRichiesta();
	}
}
