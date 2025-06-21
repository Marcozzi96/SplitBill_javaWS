package it.javaWS.models.entities;

import java.time.LocalDate;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
