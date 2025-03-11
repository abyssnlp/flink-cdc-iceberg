import os
from pyspark.sql import SparkSession

current_dir = os.path.dirname(os.path.abspath(__file__))
jars_dir = os.path.join(current_dir, "jars")

jars = [
    os.path.join(jars_dir, "hadoop-aws-3.3.4.jar"),
    os.path.join(jars_dir, "aws-java-sdk-bundle-1.12.262.jar"),
    os.path.join(jars_dir, "iceberg-spark-runtime-3.5_2.12-1.5.0.jar"),
    os.path.join(jars_dir, "minio-8.5.7.jar")
]

# Create a Spark session with Iceberg and S3 support
spark = SparkSession.builder \
    .appName("Iceberg Reader") \
    .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions") \
    .config("spark.sql.catalog.iceberg", "org.apache.iceberg.spark.SparkCatalog") \
    .config("spark.sql.catalog.iceberg.type", "hadoop") \
    .config("spark.sql.catalog.iceberg.warehouse", "s3a://data") \
    .config("spark.hadoop.fs.s3a.endpoint", "http://localhost:9000") \
    .config("spark.hadoop.fs.s3a.access.key", "hloGTnOVD3Al5cVlVlvE") \
    .config("spark.hadoop.fs.s3a.secret.key", "9o2VTSV8r6H6spTEwP5mY8WnWvaOXTsRZSfzfrCb") \
    .config("spark.hadoop.fs.s3a.path.style.access", "true") \
    .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem") \
    .config("spark.sql.defaultCatalog", "iceberg") \
    .config("spark.jars", ",".join(jars)) \
    .getOrCreate()

# Read Iceberg table
df = spark.sql("SELECT * FROM iceberg.default_database.customers where id=10000")
df.show(truncate=False)

# Stop the Spark session
spark.stop()
