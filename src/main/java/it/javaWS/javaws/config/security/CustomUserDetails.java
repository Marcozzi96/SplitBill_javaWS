package it.javaWS.javaws.config.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class CustomUserDetails implements UserDetails {
	private static final long serialVersionUID = 7688575289284599402L;
	private Long id;
    private String username;
    private Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(Long id, String username) {
        this.id = id;
        this.username = username;
        this.authorities = List.of();
    }

    public Long getId() {
        return id;
    }

    // Altri metodi obbligatori
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return null; } // Non lo usi qui
}
