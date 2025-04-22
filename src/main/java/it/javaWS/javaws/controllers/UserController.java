package it.javaWS.javaws.controllers;

import it.javaWS.javaws.dto.UserDTO;
import it.javaWS.javaws.models.User;
import it.javaWS.javaws.services.UserService;
import jakarta.persistence.PostUpdate;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public User createUser(@RequestBody User user) {
    	user.setRegDate(LocalDate.now());
        return userService.createUser(user);
    }

    @GetMapping
    public List<UserDTO> getAllUsers() {
        return userService.getAllUsers().stream().map(user->new UserDTO(user)).toList();
    }

    @GetMapping("/{id}")
    public UserDTO getUser(@PathVariable Long id) {
    	Optional<User> userOpt = userService.getUser(id);
    	if(userOpt.isPresent())
    		return new UserDTO(userOpt.get());
    	
    	return null;
        
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
