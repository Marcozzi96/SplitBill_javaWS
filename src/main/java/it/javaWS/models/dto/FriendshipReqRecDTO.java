package it.javaWS.models.dto;

import java.time.LocalDateTime;

import it.javaWS.enums.StatoAmicizia;
import it.javaWS.models.entities.Friendship;
import lombok.Data;

@Data
public class FriendshipReqRecDTO { //requests received
	private Long friendshipId;
	private UserDTO applicant;
	private StatoAmicizia stato;
	private LocalDateTime dataRichiesta;
	
	public FriendshipReqRecDTO(Friendship friendship) {
		this.friendshipId = friendship.getId();
		this.applicant = new UserDTO(
				friendship.getUser1().equals(friendship.getUserToBeConfirmed())?
				friendship.getUser2():friendship.getUser1());
		this.stato = friendship.getStato();
		this.dataRichiesta = friendship.getDataRichiesta();
	}
}
