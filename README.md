# General 
Simple Spring Boot web-app with Apache Camel, that receives data from Kafka endpoint, filters, transforms it and then
publishes to http endpoint.

# Useful commands
## Kafka
```bash
# List broker topics
kcat -b localhost:29092 -L

# Consume messages from kafka topic
kcat -b localhost:29092 -C -t externalservice.events

# Create kafka topic
docker exec -it <container-id> /bin/bash
kafka-topics --bootstrap-server localhost:9092 --topic externalservice.events --create --if-not-exists --replication-factor 1
```