package it.javaWS.config.security;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.javaWS.models.dto.AuthRequest;
import jakarta.servlet.http.HttpServletRequest;

public class UserDetailsUtil {

	public static UserDetails extractUserDetails(HttpServletRequest request) throws IOException {
	    // Leggi il corpo della richiesta
	    StringBuilder body = new StringBuilder();
	    
	    StringBuilder stringBuilder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(request.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line);
        }
        body = stringBuilder;
        
	    

	    // Usa Jackson per deserializzare il corpo JSON
	    ObjectMapper objectMapper = new ObjectMapper();
	    AuthRequest authRequest = objectMapper.readValue(body.toString(), AuthRequest.class);

	    // Restituisci un oggetto UserDetails, che potrebbe essere basato su AuthRequest
	    UserDetails userDetails = new org.springframework.security.core.userdetails.User(
	        authRequest.getUsername(), 
	        authRequest.getPassword(), 
	        new ArrayList<>() // Passa un elenco vuoto di Authorities per ora, se necessario
	    );

	    return userDetails;
	}

}
