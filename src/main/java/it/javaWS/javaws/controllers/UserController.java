package it.javaWS.javaws.controllers;

import it.javaWS.javaws.dto.UserDTO;
import it.javaWS.javaws.models.User;
import it.javaWS.javaws.security.JwtUtil;
import it.javaWS.javaws.services.UserService;
import jakarta.persistence.PostUpdate;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/user")
@PreAuthorize("isAuthenticated()")
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    public UserController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
		this.jwtUtil = jwtUtil;
    }


    @GetMapping("/me")
    public UserDTO getUser(@RequestHeader("Authorization") String authHeader) {

    	String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        
    	return new UserDTO(userService.getUser(jwtUtil.extractUserId(token)).orElseThrow()) ;
    	
    }
    
    @PutMapping()
    public UserDTO updateUser(@RequestBody User user) {
    	Optional<User> userOpt = userService.getUser(user.getId());
    	if(!userOpt.isPresent())
    		return null;
    	User userFromDB = userOpt.get();
    	if(user.getEmail() != null)
    		userFromDB.setEmail(user.getEmail());
    	if(user.getUsername() != null)
    		userFromDB.setUsername(user.getUsername());
    	
        return new UserDTO(userService.updateUser(userFromDB));
    }
    
    
    @DeleteMapping("/{id}")
    public Boolean deleteUser(@PathVariable Long id) {
        return userService.deleteUser(id);
    }
    
}
