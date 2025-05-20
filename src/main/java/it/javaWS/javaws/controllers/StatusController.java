package it.javaWS.javaws.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/status")
public class StatusController {
	
	@GetMapping("/isOn")
	public String prov() {
		return "ok";
	}

}
