package it.javaWS.utils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import it.javaWS.models.entities.User;

@Component
public class JwtUtil {

	private SecretKey secretKey;

	@Value("${jwt.secret}")
	private String jwtSecret;

	@Value("${jwt.validity}")
	private long jwtValidity;

	private SecretKey getSigningKey() {
		if (this.secretKey == null) {
			this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
		}
		return this.secretKey;
	}

	public String extractUsername(String token) {
		return extractClaim(token, Claims::getSubject);
	}

	public Long extractUserId(String token) {
		return extractAllClaims(token).get("userId", Long.class);
	}

	public String extractPassword(String token) {
		return extractAllClaims(token).get("password", String.class);
	}

	public String extractEmail(String token) {
		return extractAllClaims(token).get("email", String.class);
	}

	public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
		final Claims claims = extractAllClaims(token);
		return claimsResolver.apply(claims);
	}

	public Claims extractAllClaims(String token) {
		return Jwts.parser()
				.verifyWith(getSigningKey())
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}

	public String generateToken(UserDetails userDetails) {
		var user = (User) userDetails;

		Map<String, Object> claims = Map.of("userId", user.getId());

		return Jwts.builder()
				.claims(claims)
				.subject(user.getUsername())
				.issuedAt(new Date())
				.expiration(new Date(System.currentTimeMillis() + 1000 * jwtValidity))
				.signWith(getSigningKey())
				.compact();
	}

	public String generateEmailToken(String username, String password, String email) {
		Map<String, Object> claims = new HashMap<>();
		claims.put("password", password);
		claims.put("email", email);
		return Jwts.builder()
				.claims(claims)
				.subject(username)
				.issuedAt(new Date())
				.expiration(new Date(System.currentTimeMillis() + 1000 * jwtValidity))
				.signWith(getSigningKey())
				.compact();
	}

	public boolean validateToken(String token, UserDetails userDetails) {
		final String username = extractUsername(token);
		return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
	}

	public boolean isTokenExpired(String token) {
		return extractExpiration(token).before(new Date());
	}

	private Date extractExpiration(String token) {
		return extractClaim(token, Claims::getExpiration);
	}
}
