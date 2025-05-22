package it.javaWS.javaws.utils;

import io.jsonwebtoken.*;
import it.javaWS.javaws.models.User;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.function.Function;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

@Component
public class JwtUtil {
    
    private SecretKey secretKey = null;
    
    @Value("${jwt.secret}")
    private String jwtSecret;
    
    @Value("${jwt.validity}")
    private long jwtValidity;
    
    // Convert the string secret key to a SecretKey object
    private SecretKey getSigningKey() {
    	if(this.secretKey == null) {
    		byte[] keyBytes = jwtSecret.getBytes();
    		this.secretKey = new SecretKeySpec(keyBytes, SignatureAlgorithm.HS512.getJcaName());
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
		return Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token).getBody();
	}

	public String generateToken(UserDetails userDetails) {
		// Attenzione: assicurati che UserDetails sia una tua classe custom con getId()
		var user = (User) userDetails;

		Map<String, Object> claims = Map.of("userId", user.getId());

		return Jwts.builder().setClaims(claims).setSubject(user.getUsername()).setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + 1000 * jwtValidity)) // 24 ore
				.signWith(getSigningKey(), SignatureAlgorithm.HS512).compact();
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
