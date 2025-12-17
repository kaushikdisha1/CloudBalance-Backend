package com.example.cloudBalanceBackend.amqp;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class RabbitPublisher {
    private final RabbitTemplate rabbit;

    public RabbitPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    public void publishBulkCsvJob(String jobId, String csvBase64, String actorId) {
        var payload = Map.of("type","bulk_csv_job","jobId",jobId,"csv",csvBase64,"actorId",actorId);
        rabbit.convertAndSend(RabbitConfig.BULK_CSV_QUEUE, payload);
    }

    public void publishCreateUser(Map<String,Object> userPayload) {
        rabbit.convertAndSend(RabbitConfig.USER_CREATION_QUEUE, userPayload);
    }
}