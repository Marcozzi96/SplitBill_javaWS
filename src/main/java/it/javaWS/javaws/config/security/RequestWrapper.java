package it.javaWS.javaws.config.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;


public class RequestWrapper extends HttpServletRequestWrapper {


    public RequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        
    }


}
