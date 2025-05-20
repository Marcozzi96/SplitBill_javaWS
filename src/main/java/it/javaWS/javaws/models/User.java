package it.javaWS.javaws.models;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

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
    @OneToMany(mappedBy = "utente1")
    private Set<Friendship> richiesteInviate;

    // Amicizie dove l'utente è id_utente2
    @OneToMany(mappedBy = "utente2")
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
