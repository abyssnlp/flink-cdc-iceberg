.PHONY: db-setup iceberg-setup build-flink-job download-spark-jars read-table deploy-flink-job update-records setup-postgres setup-s3 setup-flink-operator setup-kafka setup-infra env-setup ensure-venv ensure-kubectl-helm

VERSION = $(shell git rev-parse --short HEAD)
export S3_ACCESS_KEY=hloGTnOVD3Al5cVlVlvE
export S3_SECRET_KEY=9o2VTSV8r6H6spTEwP5mY8WnWvaOXTsRZSfzfrCb

DOCKER_USER = ${docker_username}
FLINK_IMAGE = $(if $(DOCKER_USER),$(DOCKER_USER)/flink-cdc-iceberg:$(VERSION),$(error Docker registry username not set. Set docker_username variable))

ensure-kubectl-helm:
	@echo "Checking if kubectl and helm are installed..."
	@command -v kubectl >/dev/null 2>&1 || { echo >&2 "kubectl is not installed. Aborting."; exit 1; }
	@command -v helm >/dev/null 2>&1 || { echo >&2 "helm is not installed. Aborting."; exit 1; }

setup-postgres: ensure-kubectl-helm
	@echo "Setting up postgres, make sure to replace the values in k8s/postgres/values.yaml"
	helm repo add bitnami https://charts.bitnami.com/bitnami && \
    helm install postgres -f k8s/postgres/values.yaml bitnami/postgresql --version 15.5.35

setup-s3: ensure-kubectl-helm
	@echo "Setting up S3"
	kubectl apply -f k8s/s3

setup-flink-operator: ensure-kubectl-helm
	@echo "Setting up Flink operator"
	helm repo add flink-operator-repo https://downloads.apache.org/flink/flink-kubernetes-operator-1.10.0/ && \
    kubectl create -f https://github.com/jetstack/cert-manager/releases/download/v1.8.2/cert-manager.yaml && \
    helm install -f k8s/flink/values.yml flink-kubernetes-operator flink-operator-repo/flink-kubernetes-operator

setup-kafka: ensure-kubectl-helm
	@echo "Setting up Kafka and Kafka connect"
	curl -sL https://github.com/operator-framework/operator-lifecycle-manager/releases/download/v0.20.0/install.sh | bash -s v0.20.0 && \
    kubectl create -f https://operatorhub.io/install/strimzi-kafka-operator.yaml && \
    kubectl apply -f k8s/kafka/db-secrets.yml && \
    kubectl apply -f k8s/kafka/connector-config-role.yml && \
    kubectl apply -f k8s/kafka/connector-role-binding.yml && \
    kubectl apply -f k8s/kafka/kafka-node-pool.yml && \
    kubectl apply -f k8s/kafka/kafka-cluster.yml && \
    kubectl apply -f k8s/kafka/kafka-connect-cluster.yml && \
    kubectl apply -f k8s/kafka/postgres-connector.yml

setup-infra: setup-postgres setup-s3 setup-flink-operator setup-kafka
	@echo "Infrastructure setup complete"

env-setup:
	@echo "Setting up environment"
	python -m venv venv && \
	@. venv/bin/activate && \
	python -m pip install -r scripts/requirements.txt

ensure-venv:
	@if [ ! -d "venv" ]; then \
		echo "Error: Virtual environment not found. Run 'make env-setup' to create one." >&2 ;\
		exit 1 ;\
	fi

db-setup: ensure-venv
	@echo "Creating tables and seeding data, make sure to have pg running and relevant values populated in the script"
	@. venv/bin/activate &&python scripts/datagen.py

iceberg-setup:
	@echo "Creating iceberg table, make sure to have S3(minio) running and relevant ports forwarded")
	mvn compile && mvn exec:java -Dexec.mainClass=com.github.abyssnlp.setup.IcebergSetup

build-flink-job:
	@echo "Building Flink job and pushing to docker registry"
	@if [ -z "$(DOCKER_USER)" ]; then \
		echo "Error: Docker registry username not set. Set docker_username variable." ;\
		echo "ex. make build-flink-job docker_username=abyssnlp" >&2 ;\
		exit 1 ;\
	fi
	@echo "Docker user: $(DOCKER_USER)"
	docker buildx build -t $(FLINK_IMAGE) . && \
	docker push $(FLINK_IMAGE)

download-spark-jars:
	@echo "Checking for required spark jars..."
	@if [ ! -d "scripts/jars" ]; then \
		mkdir -p scripts/jars; \
	fi
	@cd scripts/jars && \
	if [ ! -f "hadoop-aws-3.3.4.jar" ] || \
	   [ ! -f "aws-java-sdk-bundle-1.12.262.jar" ] || \
	   [ ! -f "iceberg-spark-runtime-3.5_2.12-1.5.0.jar" ] || \
	   [ ! -f "minio-8.5.7.jar" ]; then \
		wget https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-aws/3.3.4/hadoop-aws-3.3.4.jar; \
		wget https://repo1.maven.org/maven2/com/amazonaws/aws-java-sdk-bundle/1.12.262/aws-java-sdk-bundle-1.12.262.jar; \
		wget https://repo1.maven.org/maven2/org/apache/iceberg/iceberg-spark-runtime-3.5_2.12/1.5.0/iceberg-spark-runtime-3.5_2.12-1.5.0.jar; \
		wget https://repo1.maven.org/maven2/io/minio/minio/8.5.7/minio-8.5.7.jar; \
	else \
		echo "Required jars found"; \
	fi

read-table: ensure-venv download-spark-jars
	@echo "Reading iceberg table; make sure to set the S3 credentials at the top of the Makefile"
	@. venv/bin/activate && python scripts/read_customers_table.py

deploy-flink-job: iceberg-setup build-flink-job
	@echo "Deploying Flink job"
	kubectl apply -f secrets.yml && \
	FLINK_IMAGE=$(FLINK_IMAGE) envsubst < deploy.yml | kubectl apply -f - && \
	kubectl port-forward svc/flink-cdc-iceberg-rest 8081:8081

remove-flink-job:
	@echo "Removing Flink job"
	kubectl delete -f deploy.yml

update-records: ensure-venv
	@echo "Updating records in the table"
	@. venv/bin/activate && python scripts/update_records.py
