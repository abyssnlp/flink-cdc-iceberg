Flink CDC to Iceberg
===

This project is a simple example of how to use Flink to consume CDC events from a Postgres database and write them to an
Iceberg table. It accompanies the blog post [here](https://unskewdata.com/blog/flink-cdc-iceberg).
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

- Postgres database (Debezium requires the `decoderbufs` plugin. We will have to build a custom image
  with the plugin installed)

```shell
helm repo add bitnami https://charts.bitnami.com/bitnami
helm install postgres -f helm/postgres/values.yaml bitnami/postgresql --version 15.5.35
```

- Minio (S3 compatible storage)

```shell
# pvc, service, deployment
kubectl apply -f s3/
```

- Flink operator

```shell
helm repo add flink-operator-repo https://downloads.apache.org/flink/flink-kubernetes-operator-1.10.0/
kubectl create -f https://github.com/jetstack/cert-manager/releases/download/v1.8.2/cert-manager.yaml
helm install -f flink/values.yml flink-kubernetes-operator flink-operator-repo/flink-kubernetes-operator
```

- Kafka and Kafka connect (Strimzi)

```shell
curl -sL https://github.com/operator-framework/operator-lifecycle-manager/releases/download/v0.20.0/install.sh | bash -s v0.20.0
kubectl create -f https://operatorhub.io/install/strimzi-kafka-operator.yaml
kubectl apply -f kafka/db-secrets.yml
kubectl apply -f kafka/connector-config-role.yml
kubectl apply -f kafka/connector-role-binding.yml
kubectl apply -f kafka/kafka-node-pool.yml
kubectl apply -f kafka/kafka-cluster.yml # kafka cluster with kraft (3.9.0)
kubectl apply -f kafka/kafka-connect-cluster.yml # kafka connect cluster
kubectl apply -f kafka/postgres-connector.yml # postgres connector
```

## Running the Flink job

The flink job can be deployed on k8s using the following command:

```shell
# secrets first as we provide them as env vars to the flink job
kubectl apply -f secrets.yml
# deploy the flink job
kubectl apply -f deploy.yml
# port-forward the flink job to access the web ui
kubectl port-forward svc/flink-cdc-iceberg 8081:8081
```

More details on generating the data, setting up the CDC, building and deploying the
flink application, querying the iceberg table and updating records can be found in
the [blog post](https://unskewdata.com/blog/flink-cdc-iceberg).

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
