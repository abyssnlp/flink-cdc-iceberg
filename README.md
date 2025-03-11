Flink CDC to Iceberg
===

This project is a simple example of how to use Flink to consume CDC events from a Postgres database and write them to an
Iceberg table.
All components are running on a local Kubernetes cluster.

```shell
docker buildx build -f Dockerfile -t flink-cdc-iceberg:0.1 .
docker tag flink-cdc-iceberg:0.1 abyssnlp/flink-cdc-iceberg:0.1 
docker push abyssnlp/flink-cdc-iceberg:0.1

# spark iceberg jars
wget https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-aws/3.3.4/hadoop-aws-3.3.4.jar
wget https://repo1.maven.org/maven2/com/amazonaws/aws-java-sdk-bundle/1.12.262/aws-java-sdk-bundle-1.12.262.jar
wget https://repo1.maven.org/maven2/org/apache/iceberg/iceberg-spark-runtime-3.5_2.12/1.5.0/iceberg-spark-runtime-3.5_2.12-1.5.0.jar
wget https://repo1.maven.org/maven2/io/minio/minio/8.5.7/minio-8.5.7.jar
```
