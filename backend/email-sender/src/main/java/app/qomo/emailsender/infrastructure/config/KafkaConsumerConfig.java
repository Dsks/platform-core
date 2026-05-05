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

/**
 * Defines the Kafka consumer infrastructure used by email-sender listeners.
 *
 * <p>The class centralizes JSON mapping and listener container construction for messages consumed
 * by the module. It reads operational Kafka settings from Spring configuration, including bootstrap
 * servers, group identity, offset reset behavior, auto-commit, and listener startup. Message
 * handling and email business decisions should remain in listener/application components, not in
 * this infrastructure wiring.
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

  /**
   * Provides the JSON mapper shared by Kafka payload handling and retry flows.
   *
   * <p>Registered Jackson modules allow Java time and other discovered platform types to be
   * serialized consistently without each adapter creating its own mapper.
   */
  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper().findAndRegisterModules();
  }

  /**
   * Builds the string-based Kafka consumer factory for inbound email messages.
   *
   * <p>The consumer configuration is resolved from the Spring environment with local defaults for
   * runtime operation. Serialization is intentionally kept at string boundaries so downstream
   * listeners can decide how to parse and validate each payload.
   */
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

  /**
   * Creates the listener container factory used by {@code @KafkaListener} methods in this module.
   *
   * <p>The factory uses the module consumer factory, manual acknowledgement, and an externally
   * controlled auto-startup flag. Those runtime decisions define listener behavior without changing
   * topic names, group IDs, or message processing logic here.
   */
  @Bean(name = "kafkaListenerContainerFactory")
  public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
      ConsumerFactory<String, String> consumerFactory,
      org.springframework.core.env.Environment environment) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
    factory.setAutoStartup(
        Boolean.parseBoolean(
            environment.getProperty("spring.kafka.listener.auto-startup", "true")));
    return factory;
  }
}
