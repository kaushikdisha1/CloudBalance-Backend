package com.example.cloudBalanceBackend.amqp;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import java.io.StringReader;
import java.util.*;

@Component
public class WorkerListener {

    private final RabbitPublisher publisher;

    public WorkerListener(RabbitPublisher publisher) {
        this.publisher = publisher;
    }

    @SuppressWarnings("unchecked")
    @RabbitListener(queues = RabbitConfig.BULK_CSV_QUEUE)
    public void handleBulkJob(Map<String, Object> payload) {
        try {
            if (!"bulk_csv_job".equals(payload.get("type"))) return;
            String csvBase64 = (String) payload.get("csv");
            String actorId = (String) payload.get("actorId");
            String csvText = new String(Base64.getDecoder().decode(csvBase64));
            CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().withTrim().parse(new StringReader(csvText));
            for (CSVRecord r : parser) {
                String name = r.get("name");
                String email = r.get("email");
                String password = r.get("password");
                String role = r.get("role");
                String accountIds = r.isMapped("accountIds") ? r.get("accountIds") : null;
                if (name == null || email == null || password == null || role == null) continue;
                if (!Set.of("ADMIN","READ_ONLY","CUSTOMER").contains(role)) continue;
                List<String> aids = new ArrayList<>();
                if (accountIds != null && !accountIds.isBlank()) {
                    for (String a : accountIds.split(",")) if (!a.isBlank()) aids.add(a.trim());
                }
                Map<String,Object> createUserPayload = Map.of("type","create_user","user",Map.of("name",name,"email",email,"password",password,"role",role,"accountIds",aids),"actorId",actorId);
                publisher.publishCreateUser(createUserPayload);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}