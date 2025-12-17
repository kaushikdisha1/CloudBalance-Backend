package com.example.cloudBalanceBackend.amqp;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String USER_CREATION_QUEUE = "user_creation_queue";
    public static final String BULK_CSV_QUEUE = "bulk_csv_jobs";

    @Bean
    public Queue userCreationQueue() {
        return new Queue(USER_CREATION_QUEUE, true);
    }

    @Bean
    public Queue bulkCsvQueue() {
        return new Queue(BULK_CSV_QUEUE, true);
    }
}