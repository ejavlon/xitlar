package uz.xitlar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class XitlarApplication {

	public static void main(String[] args) {
		SpringApplication.run(XitlarApplication.class, args);

		//bu yerda o'zgarishlar bo'ldi
	}

}
