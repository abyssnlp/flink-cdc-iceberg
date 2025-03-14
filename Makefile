.PHONY: db-setup iceberg-setup build-flink-job download-spark-jars read-table deploy-flink-job update-records

VERSION = $(shell git rev-parse --short HEAD)
export S3_ACCESS_KEY=hloGTnOVD3Al5cVlVlvE
export S3_SECRET_KEY=9o2VTSV8r6H6spTEwP5mY8WnWvaOXTsRZSfzfrCb

DOCKER_USER = ${docker_username}
FLINK_IMAGE = $(if $(DOCKER_USER),$(DOCKER_USER)/flink-cdc-iceberg:$(VERSION),$(error Docker registry username not set. Set docker_username variable))

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
	python scripts/datagen.py

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
	kubectl apply -f secrets.yaml && \
	FLINK_IMAGE=$(FLINK_IMAGE) envsubst < deploy.yml | kubectl apply -f -

update-records: ensure-venv
	@echo "Updating records in the table"
	@. venv/bin/activate && python scripts/update_records.py
