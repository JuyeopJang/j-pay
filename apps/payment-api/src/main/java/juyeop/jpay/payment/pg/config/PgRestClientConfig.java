package juyeop.jpay.payment.pg.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * PG 호출 전용 RestClient. baseUrl + connect/read timeout 명시.
 * 같은 프로세스 내 mock 컨트롤러를 호출 (Week 2 한정).
 */
@Configuration
public class PgRestClientConfig {

    @Bean
    public RestClient pgRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));

        return RestClient.builder()
                .baseUrl("http://localhost:8080")
                .requestFactory(factory)
                .build();
    }
}