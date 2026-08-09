package it.javaWS.models.entities;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserGroupIdTest {

    @Test
    void gettersAndSettersWork() {
        UserGroupId id = new UserGroupId();
        id.setUserId(1L);
        id.setGroupId(2L);

        assertThat(id.getUserId()).isEqualTo(1L);
        assertThat(id.getGroupId()).isEqualTo(2L);
    }

    @Test
    void constructor_setsValues() {
        UserGroupId id = new UserGroupId(1L, 2L);

        assertThat(id.getUserId()).isEqualTo(1L);
        assertThat(id.getGroupId()).isEqualTo(2L);
    }

    @Test
    void equals_sameObject_returnsTrue() {
        UserGroupId id = new UserGroupId(1L, 2L);
        assertThat(id.equals(id)).isTrue();
    }

    @Test
    void equals_equalValues_returnsTrue() {
        UserGroupId id1 = new UserGroupId(1L, 2L);
        UserGroupId id2 = new UserGroupId(1L, 2L);
        assertThat(id1.equals(id2)).isTrue();
    }

    @Test
    void equals_differentValues_returnsFalse() {
        UserGroupId id1 = new UserGroupId(1L, 2L);
        UserGroupId id2 = new UserGroupId(1L, 3L);
        assertThat(id1.equals(id2)).isFalse();
    }

    @Test
    void equals_nullOrDifferentType_returnsFalse() {
        UserGroupId id = new UserGroupId(1L, 2L);
        assertThat(id.equals(null)).isFalse();
        assertThat(id.equals("string")).isFalse();
    }

    @Test
    void hashCode_equalValues_match() {
        UserGroupId id1 = new UserGroupId(1L, 2L);
        UserGroupId id2 = new UserGroupId(1L, 2L);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }
}
