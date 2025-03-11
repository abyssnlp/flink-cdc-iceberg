package com.github.abyssnlp;

import com.github.abyssnlp.serde.DebeziumJsonDeserializer;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.core.execution.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.data.RowData;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.flink.TableLoader;
import org.apache.iceberg.flink.sink.FlinkSink;
import org.apache.iceberg.hadoop.HadoopCatalog;
import org.apache.kafka.clients.consumer.ConsumerConfig;

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

        // Iceberg table details
        final String databaseName = "default_database";
        final String tableName = "customers";

        // s3
        String s3Endpoint = envVars.get("S3_ENDPOINT");
        String s3AccessKey = envVars.get("S3_ACCESS_KEY");
        String s3SecretKey = envVars.get("S3_SECRET_KEY");
        final String warehousePath = "s3a://data/";

        // exactly-once processing
        env.enableCheckpointing(120000, CheckpointingMode.EXACTLY_ONCE);
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(5000);
        env.getCheckpointConfig().setCheckpointTimeout(120000);
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
        env.getCheckpointConfig().setTolerableCheckpointFailureNumber(3);


        if (kafkaBootstrapServers == null) {
            throw new IllegalArgumentException("KAFKA_BOOTSTRAP_SERVERS environment variable is not set");
        } else if (kafkaGroupId == null) {
            throw new IllegalArgumentException("KAFKA_GROUP_ID environment variable is not set");
        } else if (kafkaTopicsString == null) {
            throw new IllegalArgumentException("KAFKA_TOPICS environment variable is not set");
        }

        List<String> kafkaTopics = Arrays.stream(kafkaTopicsString.split(",")).toList();
        KafkaSource<RowData> source = KafkaSource.<RowData>builder()
                .setBootstrapServers(kafkaBootstrapServers)
                .setTopics(kafkaTopics)
                .setGroupId(kafkaGroupId)
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new DebeziumJsonDeserializer())
                .setProperty("enable.auto.commit", "false")
                .setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
                .setProperty(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed")
                .build();

        DataStream<RowData> sourceStream = env
                .fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source")
                .uid("kafka-cdc-source")
                .name("Debezium CDC Source");


        // TODO: placeholder for transformations (if any)

        Configuration hadoopConf = new Configuration();
        hadoopConf.set("fs.s3a.endpoint", s3Endpoint);
        hadoopConf.set("fs.s3a.access.key", s3AccessKey);
        hadoopConf.set("fs.s3a.secret.key", s3SecretKey);
        hadoopConf.set("fs.s3a.path.style.access", "true");
        hadoopConf.set("fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem");
        hadoopConf.set("fs.s3a.connection.ssl.enabled", "false");
        hadoopConf.set("fs.s3a.aws.credentials.provider", "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider");

        // catalog, table-loader and sink
        HadoopCatalog catalog = new HadoopCatalog(hadoopConf, warehousePath);
        TableIdentifier tableIdentifier = TableIdentifier.of(databaseName, tableName);

        TableLoader tableLoader = TableLoader.fromHadoopTable(
                catalog.loadTable(tableIdentifier).location(),
                hadoopConf
        );


        FlinkSink.forRowData(sourceStream)
                .tableLoader(tableLoader)
                .writeParallelism(1)
                .upsert(true)
                .equalityFieldColumns(List.of("id", "updated_at"))
                .append();

        env.execute("Flink CDC Iceberg");

    }
}
