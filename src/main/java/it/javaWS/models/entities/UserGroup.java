package it.javaWS.models.entities;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "user_group")
public class UserGroup {

	@EmbeddedId
    @EqualsAndHashCode.Include
    private UserGroupId id = new UserGroupId();

    @ManyToOne
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @MapsId("groupId")
    @JoinColumn(name = "group_id")
    private Group group;

    private LocalDate dataIngresso;
    private LocalDate dataUscita;
    
    @PrePersist
    @PreUpdate
    private void updateEmbeddedId() {
        if (user != null && group != null) {
            id.setUserId(user.getId());
            id.setGroupId(group.getId());
        }
    }
}
