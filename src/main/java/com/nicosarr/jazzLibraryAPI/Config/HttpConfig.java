package com.nicosarr.jazzLibraryAPI.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class HttpConfig {

    private static final String USER_AGENT =
        "JazzLibrary/1.0 (https://github.com/nicosarr/jazzLibraryAPI; nicko.sarr@gmail.com)";

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(15_000);

        RestTemplate rt = new RestTemplate(factory);
        rt.getInterceptors().add(userAgentInterceptor());
        return rt;
    }

    private ClientHttpRequestInterceptor userAgentInterceptor() {
        return (request, body, execution) -> {
            request.getHeaders().set("User-Agent", USER_AGENT);
            return execution.execute(request, body);
        };
    }
}