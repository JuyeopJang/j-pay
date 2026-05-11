package juyeop.jpay.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication(scanBasePackages = "juyeop.jpay")
@EntityScan(basePackages = "juyeop.jpay")
public class PaymentApiApplication {
	public static void main(String[] args) {
		SpringApplication.run(PaymentApiApplication.class, args);
	}
}