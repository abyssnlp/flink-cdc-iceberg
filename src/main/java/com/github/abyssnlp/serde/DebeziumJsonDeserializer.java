package com.github.abyssnlp.serde;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.table.data.GenericRowData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.StringData;
import org.apache.flink.table.data.TimestampData;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.types.RowKind;
import org.apache.flink.util.Collector;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDate;

public class DebeziumJsonDeserializer implements DeserializationSchema<RowData> {
    private static final ObjectMapper objectMapper = new ObjectMapper();


    @Override
    public void open(InitializationContext context) throws Exception {
        DeserializationSchema.super.open(context);
    }

    @Override
    public RowData deserialize(byte[] message) throws IOException {
        if(message == null) {
            return null;
        }

        try {
            JsonNode rootNode = objectMapper.readTree(message);

            // debezium op
            String op = rootNode.get("payload").path("op").asText();
            RowKind rowKind = getRowKind(op);

            JsonNode dataNode = op.equals("d") ?
                    rootNode.path("payload").path("before")
                    : rootNode.path("payload").path("after");

            GenericRowData rowData = new GenericRowData(5);
            rowData.setRowKind(rowKind);

            rowData.setField(0, dataNode.path("id").asInt());
            rowData.setField(1, StringData.fromString(dataNode.path("name").asText()));
            rowData.setField(2, LocalDate.parse(dataNode.path("date_of_joining").asText()).toEpochDay());
            rowData.setField(3, TimestampData.fromTimestamp(Timestamp.valueOf(dataNode.path("updated_at").asText())));
            rowData.setField(4, StringData.fromString(dataNode.path("address").asText()));

            return rowData;
        } catch(Exception e) {
            throw new IOException("Failed to deserialize debezium JSON message ", e);
        }
    }

    private RowKind getRowKind(String op) {
        return switch (op) {
            case "c", "r" -> RowKind.INSERT;
            case "u" -> RowKind.UPDATE_AFTER;
            case "d" -> RowKind.DELETE;
            default -> throw new IllegalArgumentException("Unknown debezium operator type: " + op);
        };
    }

    @Override
    public void deserialize(byte[] message, Collector<RowData> out) throws IOException {
        DeserializationSchema.super.deserialize(message, out);
    }

    @Override
    public boolean isEndOfStream(RowData rowData) {
        return false;
    }

    @Override
    public TypeInformation<RowData> getProducedType() {
        RowType rowType = RowType.of(
                new LogicalType[]{
                        new org.apache.flink.table.types.logical.IntType(),
                        new org.apache.flink.table.types.logical.VarCharType(Integer.MAX_VALUE),
                        new org.apache.flink.table.types.logical.DateType(),
                        new org.apache.flink.table.types.logical.TimestampType(3),
                        new org.apache.flink.table.types.logical.VarCharType(Integer.MAX_VALUE)
                },
                new String[]{"id", "name", "date_of_joining", "updated_at", "address"}
        );

        return TypeInformation.of(RowData.class);
    }
}
