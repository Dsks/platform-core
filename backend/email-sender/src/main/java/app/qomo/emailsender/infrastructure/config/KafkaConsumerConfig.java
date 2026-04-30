package app.qomo.emailsender.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration
@EnableKafka
public class KafkaConsumerConfig {

  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper().findAndRegisterModules();
  }

  @Bean
  public ConsumerFactory<String, String> consumerFactory(
      org.springframework.core.env.Environment environment) {
    Map<String, Object> props = new HashMap<>();
    props.put(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
        environment.getProperty("spring.kafka.bootstrap-servers", "kafka:9092"));
    props.put(
        ConsumerConfig.GROUP_ID_CONFIG,
        environment.getProperty("spring.kafka.consumer.group-id", "qomo-email-sender"));
    props.put(
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
        environment.getProperty("spring.kafka.consumer.auto-offset-reset", "earliest"));
    props.put(
        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
        Boolean.parseBoolean(
            environment.getProperty("spring.kafka.consumer.enable-auto-commit", "false")));
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    return new DefaultKafkaConsumerFactory<>(props);
  }

  @Bean(name = "kafkaListenerContainerFactory")
  public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
      ConsumerFactory<String, String> consumerFactory,
      org.springframework.core.env.Environment environment) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
    factory.setAutoStartup(Boolean.parseBoolean(
        environment.getProperty("spring.kafka.listener.auto-startup", "true")));
    return factory;
  }
}