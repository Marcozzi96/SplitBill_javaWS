package it.javaWS.javaws.models.entities;

import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

@Data
@NoArgsConstructor
@Embeddable
public class UserGroupId implements Serializable {
	
	private static final long serialVersionUID = 4906061652309636187L;
	
	private Long userId;
    private Long groupId;

    public UserGroupId(Long userId, Long groupId) {
    	this.userId = userId;
    	this.groupId = groupId;
    }
    // equals e hashCode sono fondamentali per le chiavi composite
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserGroupId)) return false;
        UserGroupId that = (UserGroupId) o;
        return Objects.equals(userId, that.userId) &&
               Objects.equals(groupId, that.groupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, groupId);
    }
}
