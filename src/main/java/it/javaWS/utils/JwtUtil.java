package it.javaWS.utils;

import java.util.Date;
import java.util.Map;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import it.javaWS.models.entities.User;

@Component
public class JwtUtil {

	private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

	private SecretKey secretKey;

	@Value("${jwt.secret}")
	private String jwtSecret;

	@Value("${jwt.validity}")
	private long jwtValidity;

	private SecretKey getSigningKey() {
		if (this.secretKey == null) {
			if (jwtSecret == null || jwtSecret.isBlank()) {
				// Solo per sviluppo locale: chiave effimera, i token si invalidano ad ogni riavvio
				log.warn("JWT_SECRET non impostata: generata chiave effimera casuale (solo per sviluppo). I token saranno invalidati ad ogni riavvio.");
				this.secretKey = Jwts.SIG.HS512.key().build();
			} else {
				this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
			}
		}
		return this.secretKey;
	}

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
