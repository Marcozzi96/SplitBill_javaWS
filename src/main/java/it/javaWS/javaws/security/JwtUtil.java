package it.javaWS.javaws.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.function.Function;

import javax.crypto.SecretKey;

@Component
public class JwtUtil {

    private final SecretKey SECRET =  Keys.secretKeyFor(SignatureAlgorithm.HS512); // meglio da properties/env
    
	public String extractUsername(String token) {
		return extractClaim(token, Claims::getSubject);
	}

	public Long extractUserId(String token) {
		return extractAllClaims(token).get("userId", Long.class);
	}

	public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
		final Claims claims = extractAllClaims(token);
		return claimsResolver.apply(claims);
	}

	public Claims extractAllClaims(String token) {
		return Jwts.parserBuilder().setSigningKey(SECRET).build().parseClaimsJws(token).getBody();
	}

	public String generateToken(UserDetails userDetails) {
		// Attenzione: assicurati che UserDetails sia una tua classe custom con getId()
		var user = (it.javaWS.javaws.models.User) userDetails;

		Map<String, Object> claims = Map.of("userId", user.getId());

		return Jwts.builder().setClaims(claims).setSubject(user.getUsername()).setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24)) // 24 ore
				.signWith(SECRET, SignatureAlgorithm.HS512).compact();
	}

	public boolean validateToken(String token, UserDetails userDetails) {
		final String username = extractUsername(token);
		return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
	}

	private boolean isTokenExpired(String token) {
		return extractExpiration(token).before(new Date());
	}

	private Date extractExpiration(String token) {
		return extractClaim(token, Claims::getExpiration);
	}
}
