Flink CDC to Iceberg
===

This project is a simple example of how to use Flink to consume CDC events from a Postgres database and write them to an
Iceberg table. It accompanies the blog post [here](https://unskewdata.com/blog/stream-flink-3).
All components are running on a local Kubernetes cluster.

## Pre-requisites

The following tools are required to run this project:

- Docker
- Docker desktop (for local Kubernetes cluster)
- Helm
- Kubectl

## Infrastructure setup

We are going to use the local kubernetes cluster to run the following components. All manifests
are in the `k8s` [directory](./k8s).

- Postgres database
- Minio (S3)
- Flink operator
- Kafka operator
- Kafka cluster
- Kafka connect cluster
- Kafka UI (Provectus)
- Postgres connector

To set up the infrastructure:

```shell
make setup-infra
```

## Running the Flink job

The flink job can be deployed on k8s using the following command:

```shell
make deploy-flink-job
```

To remove the flink job:

```shell
make remove-flink-job
```

## Troubleshooting Infrastructure setup

1. If the postgres chart fails to install due to the custom image, you can uncomment this in the
   `k8s/postgres/values.yaml` file.

```shell
 global:
   security:
     allowInsecureImages: true
```

2. For the Kafka connect cluster here: `k8s/kafka/kafka-connect-cluster.yml`, if you do not want to build and push the
   debezium base image with
   the postgres plugin, you can use my pre-built image that is already pushed to docker hub. The manifest would become
   something like this:

```yaml
apiVersion: kafka.strimzi.io/v1beta2
kind: KafkaConnect
metadata:
  name: debezium-connect-cluster
  namespace: kafka
  annotations:
    strimzi.io/use-connector-resources: "true"
spec:
  version: 3.9.0
  image: abyssnlp/debezium-connect-postgresql:latest
  replicas: 1
  bootstrapServers: kluster-kafka-bootstrap:9092
  config:
    config.providers: secrets
    config.providers.secrets.class: io.strimzi.kafka.KubernetesSecretConfigProvider
    group.id: connect-cluster
    offset.storage.topic: connect-cluster-offsets
    config.storage.topic: connect-cluster-configs
    status.storage.topic: connect-cluster-status
    # -1 means fallback to the broker settings
    config.storage.replication.factor: -1
    offset.storage.replication.factor: -1
    status.storage.replication.factor: -1
```

3. If deploying the Flink job fails at the `iceberg-setup` task or if you don't have Apache Maven locally installed, you
   can open the
   `com.github.abyssnlp.setup.IcebergSetup`
   class on your IDE and run the main method. This will create the iceberg table and schema.

---

More details on generating the data, setting up the CDC, building and deploying the
flink application, querying the iceberg table and updating records can be found in
the [blog post](https://unskewdata.com/blog/flink-cdc-iceberg).
