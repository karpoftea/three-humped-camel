# Kafka
```bash
# List broker topics
kcat -b localhost:29092 -L

# Consume messages from kafka topic
kcat -b localhost:29092 -C -t externalservice.events

# Create kafka topic
docker exec -it <container-id> /bin/bash
kafka-topics --bootstrap-server localhost:9092 --topic externalservice.events --create --if-not-exists --replication-factor 1
```