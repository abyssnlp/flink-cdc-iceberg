package com.github.abyssnlp.setup;

import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.hadoop.HadoopCatalog;
import org.apache.iceberg.types.Types;

import java.util.HashMap;
import java.util.Map;

public class IcebergSetup {
    public static void main(String[] args) {
        Map<String, String> env = System.getenv();
        final String warehousePath = "s3a://data/";
        final String s3Endpoint = "http://localhost:9000";
        final String s3AccessKey = env.get("S3_ACCESS_KEY");
        final String s3SecretKey = env.get("S3_SECRET_KEY");

        final String databaseName = "default_database";
        final String tableName = "customers";

        Configuration hadoopConf = new Configuration();
        hadoopConf.set("fs.s3a.endpoint", s3Endpoint);
        hadoopConf.set("fs.s3a.access.key", s3AccessKey);
        hadoopConf.set("fs.s3a.secret.key", s3SecretKey);
        hadoopConf.set("fs.s3a.path.style.access", "true");
        hadoopConf.set("fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem");

        // Iceberg catalog
        HadoopCatalog catalog = new HadoopCatalog(hadoopConf, warehousePath);

        try {
            Namespace namespace = Namespace.of(databaseName);
            if(!catalog.namespaceExists(namespace)) {
                catalog.createNamespace(namespace);
                System.out.println("Created namespace :" + databaseName);
            }

            Schema schema = new Schema(
                    Types.NestedField.required(1, "id", Types.IntegerType.get()),
                    Types.NestedField.required(2, "name", Types.StringType.get()),
                    Types.NestedField.required(3, "date_of_joining", Types.DateType.get()),
                    Types.NestedField.required(4, "updated_at", Types.TimestampType.withoutZone()),
                    Types.NestedField.required(5, "address", Types.StringType.get())
            );

            TableIdentifier tableId = TableIdentifier.of(namespace, tableName);

            if(!catalog.tableExists(tableId)) {
                PartitionSpec spec = PartitionSpec.builderFor(schema)
                        .day("updated_at")
                        .build();

                // table properties
                Map<String, String> properties = new HashMap<>();
                properties.put("format-version", "2");
                properties.put("write.upsert.enabled", "true");

                // create table
                Table table = catalog.createTable(tableId, schema, spec, properties);
                System.out.println("Created table: " + tableName);
                System.out.println("Table location: " + table.location());
            } else {
                System.out.println("Table already exists: " + tableName);
            }
            System.out.println("Iceberg setup completed successfully");
        } catch (Exception e) {
            System.err.println("Error during Iceberg setup: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
