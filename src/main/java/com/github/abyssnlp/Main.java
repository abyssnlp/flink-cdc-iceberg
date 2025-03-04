package com.github.abyssnlp;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        Map<String, String> envVars = System.getenv();

        String kafkaBootstrapServers = envVars.get("KAFKA_BOOTSTRAP_SERVERS");
        String kafkaGroupId = envVars.get("KAFKA_GROUP_ID");
        String kafkaTopicsString = envVars.get("KAFKA_CDC_TOPICS");

        if(kafkaBootstrapServers == null) {
            throw new IllegalArgumentException("KAFKA_BOOTSTRAP_SERVERS environment variable is not set");
        } else if(kafkaGroupId == null) {
            throw new IllegalArgumentException("KAFKA_GROUP_ID environment variable is not set");
        } else if(kafkaTopicsString == null) {
            throw new IllegalArgumentException("KAFKA_TOPICS environment variable is not set");
        }

        List<String> kafkaTopics = Arrays.stream(kafkaTopicsString.split(",")).toList();
        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaBootstrapServers)
                .setTopics(kafkaTopics)
                .setGroupId(kafkaGroupId)
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .setProperty("enable.auto.commit", "false")
                .build();

        env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source").print();
        env.execute("Flink CDC Iceberg");
    }
}
