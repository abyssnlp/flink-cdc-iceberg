package com.github.abyssnlp;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers("kluster-kafka-bootstrap.kafka.svc.cluster.local:9092")
                .setTopics(List.of("postgres1.public.tester"))
                .setGroupId("flink-cdc-iceberg-consumer")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .setProperty("enable.auto.commit", "false")
                .build();

        env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source").print();
        env.execute("Flink CDC Iceberg");
    }
}
