Flink CDC to Iceberg
===

This project is a simple example of how to use Flink to consume CDC events from a Postgres database and write them to an Iceberg table.
All components are running on a local Kubernetes cluster.

```shell
docker buildx build -f Dockerfile -t flink-cdc-iceberg:0.1 .
docker tag flink-cdc-iceberg:0.1 abyssnlp/flink-cdc-iceberg:0.1 
docker push abyssnlp/flink-cdc-iceberg:0.1
```
