package iaf.ofek.sigma;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SigmaApplication {

    public static void main(String[] args) {
        SpringApplication.run(SigmaApplication.class, args);
    }
}
