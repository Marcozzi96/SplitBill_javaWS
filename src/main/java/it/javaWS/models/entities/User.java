package it.javaWS.models.entities;

import java.io.Serial;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
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
	@Serial
    private static final long serialVersionUID = 9056374516475231401L;

	@EqualsAndHashCode.Include
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String username;

	// Email come inserita dall'utente (mai riscritta)
	@Column(nullable = false)
	private String email;

	// Chiave di unicità/lookup dell'email: lowercase; per Gmail/Googlemail senza
	// punti nel local part (Google li ignora). Nullable per le righe pregresse
	// (backfill via script SQL), sempre valorizzata dalle scritture dell'app.
	@Column(unique = true)
	private String emailCanonical;
	private String password;
	private LocalDate regDate;
    private boolean deleted = false;

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
    public boolean isEnabled() { return !deleted; }
}
