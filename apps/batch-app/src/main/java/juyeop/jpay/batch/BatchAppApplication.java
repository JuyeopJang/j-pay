package juyeop.jpay.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BatchAppApplication {
	public static void main(String[] args) {
		SpringApplication.run(BatchAppApplication.class, args);
	}
}