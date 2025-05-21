package it.javaWS.javaws.controllers;

import it.javaWS.javaws.dto.AuthResponse;
import it.javaWS.javaws.dto.UserDTO;
import it.javaWS.javaws.models.User;
import it.javaWS.javaws.security.JwtUtil;
import it.javaWS.javaws.services.UserService;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@PreAuthorize("isAuthenticated()")
public class UserController {

	private final UserService userService;
	private final JwtUtil jwtUtil;
	private final PasswordEncoder passwordEncoder;

	public UserController(UserService userService, JwtUtil jwtUtil, PasswordEncoder passwordEncoder) {
		this.userService = userService;
		this.jwtUtil = jwtUtil;
		this.passwordEncoder = passwordEncoder;
	}

	@GetMapping("/me")
	public ResponseEntity<?> getUser(@RequestHeader("Authorization") String authHeader) {

		String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;

		User user = userService.getUser(jwtUtil.extractUserId(token)).orElse(null);
		if (user == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Token non valido"));
		}
		return ResponseEntity.ok(new UserDTO(user));

	}

	@PutMapping("/update")
	public ResponseEntity<?> updateUser(@RequestHeader("Authorization") String authHeader,
			@RequestBody User updatedUser) {
		String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;

		User userFromDB = userService.getUser(jwtUtil.extractUserId(token)).orElse(null);
		if (userFromDB == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Token non valido"));
		}

		if (updatedUser.getEmail() != null)
			userFromDB.setEmail(updatedUser.getEmail());
		if (updatedUser.getUsername() != null)
			userFromDB.setUsername(updatedUser.getUsername());
		if (updatedUser.getPassword() != null) {
			userFromDB.setPassword(passwordEncoder.encode(updatedUser.getPassword()));
		}
		User updated = userService.updateUser(userFromDB);

		// CREA UN NUOVO TOKEN
		String newToken = jwtUtil.generateToken(updated);

		return ResponseEntity.ok(new AuthResponse(newToken, new UserDTO(updated)));

	}

//	@DeleteMapping("/{id}")
//	public Boolean deleteUser(@PathVariable Long id) {
//		return userService.deleteUser(id);
//	}

}
