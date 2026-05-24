package juyeop.jpay.transfer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication(scanBasePackages = "juyeop.jpay")
@EntityScan(basePackages = "juyeop.jpay")
public class TransferServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(TransferServiceApplication.class, args);
	}
}