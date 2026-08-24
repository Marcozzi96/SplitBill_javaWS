package it.javaWS.models.dto;

import java.time.LocalDate;

import it.javaWS.models.entities.UserGroup;
import it.javaWS.models.enums.GroupRole;
import lombok.Data;

@Data
public class GroupMemberDTO {

    private Long userId;
    private String username;
    private String email;
    private GroupRole role;
    private LocalDate dataIngresso;
    // true per gli account eliminati (soft delete): i dati personali sono mascherati
    private boolean deleted;

    public GroupMemberDTO(UserGroup userGroup) {
        this.userId = userGroup.getUser().getId();
        this.deleted = userGroup.getUser().isDeleted();
        if (this.deleted) {
            this.username = "UtenteEliminato";
            this.email = null;
        } else {
            this.username = userGroup.getUser().getUsername();
            this.email = userGroup.getUser().getEmail();
        }
        this.role = userGroup.getRole();
        this.dataIngresso = userGroup.getDataIngresso();
    }
}
