package com.example.demo.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Enables background task execution. Document ingestion (PDF text extraction +
 * the multi-hop call to the AI gateway for embedding) runs on this pool so the
 * upload HTTP response returns as soon as the file is persisted, instead of
 * blocking the request thread for the full embedding round-trip.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "aiIngestionExecutor")
    public Executor aiIngestionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ai-ingest-");
        executor.initialize();
        return executor;
    }
}
