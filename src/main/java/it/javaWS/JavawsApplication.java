package it.javaWS;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JavawsApplication {

	public static void main(String[] args) {
		SpringApplication.run(JavawsApplication.class, args);
	}

}
