package my.threehumpedcamel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import my.threehumpedcamel.model.Event;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;

public class Utils {

    private Utils() {
    }

    public static void writeAvroToFile(Event event, String path) throws IOException {
        Files.write(Paths.get(path), toBytes(event).toByteArray());
    }

    public static ByteArrayOutputStream toBytes(Event event) throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DatumWriter<Object> datum = new SpecificDatumWriter<>(Event.SCHEMA$);
        Encoder encoder = EncoderFactory.get().binaryEncoder(outputStream, null);
        datum.write(event, encoder);
        encoder.flush();
        return outputStream;
    }

    public static void main(String[] args) throws IOException {
        String path = args[0];

        writeValidEvent(path + "/valid-event.avro");
        writeInvalidEvent(path + "/invalid-event.avro");
    }

    private static void writeInvalidEvent(String path) throws IOException {
        Files.write(Paths.get(path), new byte[] {1, 2, 3});
    }

    private static void writeValidEvent(String path) throws IOException {
        final Map<String, String> properties = new HashMap<>();
        properties.put("value", "test-value");
        final Event event = Event.newBuilder()
            .setId(42L)
            .setType("test")
            .setProperties(properties)
            .build();

        Utils.writeAvroToFile(event, path);
    }
}
