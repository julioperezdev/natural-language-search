package dev.julioperez.nls;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NlsApplication {

	public static void main(String[] args) {
		SpringApplication.run(NlsApplication.class, args);
	}

}
