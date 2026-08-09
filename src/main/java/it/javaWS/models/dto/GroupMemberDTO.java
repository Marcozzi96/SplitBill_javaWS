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

    public GroupMemberDTO(UserGroup userGroup) {
        this.userId = userGroup.getUser().getId();
        this.username = userGroup.getUser().getUsername();
        this.email = userGroup.getUser().getEmail();
        this.role = userGroup.getRole();
        this.dataIngresso = userGroup.getDataIngresso();
    }
}
