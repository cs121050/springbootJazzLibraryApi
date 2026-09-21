package com.nicosarr.jazzLibraryAPI.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;


import java.util.concurrent.atomic.AtomicLong;

@Configuration
public class HttpConfig {

    private static final String USER_AGENT =
        "JazzLibrary/1.0 (https://github.com/nicosarr/jazzLibraryAPI; nicko.sarr@gmail.com)";

    /** Minimum gap between any two outbound HTTP calls, in ms. Keeps us under Wikipedia's rate limit. */
    private static final long MIN_INTERVAL_MS = 150;

    /** Timestamp (epoch ms) of the last outbound call, shared across all threads. */
    private final AtomicLong lastCall = new AtomicLong(0);
    
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(15_000);

        RestTemplate rt = new RestTemplate(factory);
        rt.getInterceptors().add(userAgentInterceptor());
        return rt;
    }
    
    @Bean
    public org.springframework.transaction.support.TransactionTemplate transactionTemplate(
            org.springframework.transaction.PlatformTransactionManager tm) {
        return new org.springframework.transaction.support.TransactionTemplate(tm);
    }

    private ClientHttpRequestInterceptor userAgentInterceptor() {
        return (request, body, execution) -> {
            // Polite throttling: enforce a minimum gap between requests.
            long now  = System.currentTimeMillis();
            long prev = lastCall.get();
            long wait = MIN_INTERVAL_MS - (now - prev);
            if (wait > 0) {
                try { Thread.sleep(wait); } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            lastCall.set(System.currentTimeMillis());

            request.getHeaders().set("User-Agent", USER_AGENT);
            return execution.execute(request, body);
        };
    }
}