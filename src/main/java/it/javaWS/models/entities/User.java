package it.javaWS.models.entities;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
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
@Table(name = "users") // "user" è parola riservata in alcuni DB
public class User implements UserDetails {
	private static final long serialVersionUID = 9056374516475231401L;

	@EqualsAndHashCode.Include
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String username;
	private String email;
	private String password;
	private LocalDate regDate;
//    private Boolean deleted;

	@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<UserGroup> userGroups;

	@OneToMany(mappedBy = "user")
	private List<Transaction> transactions;

	@OneToMany(mappedBy = "buyer")
	private List<Bill> billsCredit;
	
    // Amicizie dove l'utente è id_utente1
    @OneToMany(mappedBy = "user1")
    private Set<Friendship> richiesteInviate;

    // Amicizie dove l'utente è id_utente2
    @OneToMany(mappedBy = "user2")
    private Set<Friendship> richiesteRicevute;

	
//	@OneToMany(mappedBy = "client")
//	private List<Bill> billsDebit;

	@Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
