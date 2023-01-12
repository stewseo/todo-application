package org.example;

import io.confluent.kafka.serializers.KafkaJsonDeserializer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.processor.internals.Task;
import org.apache.kafka.streams.test.TestRecord;
import org.example.model.Command;
import org.example.model.CreateTaskCommand;
import org.example.model.DeleteTaskCommand;
import org.example.model.RenameTaskCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class ProcessorTest
{
    private static final String TASK_COMMANDS_TOPIC = "task-commands";
    private static final String TASK_SNAPSHOTS_TOPIC = "task-snapshots";
    private static final String TASK_REPLIES_TOPIC = "task-replies";

    private TopologyTestDriver testDriver;

    private TestInputTopic<String, Command> commandsInTopic;
    private TestOutputTopic<String, Task> snapshotsOutTopic;
    private TestOutputTopic<String, Object> commandsResponseTopic;

    @BeforeEach
    public void setUp() {
        final StreamsBuilder builder = new StreamsBuilder();
        final CqrsTopology processor = new CqrsTopology();
        processor.taskCommandsTopic = TASK_COMMANDS_TOPIC;
        processor.taskSnapshotsTopic = TASK_SNAPSHOTS_TOPIC;
        processor.taskRepliesTopic = TASK_REPLIES_TOPIC;
        processor.buildPipeline(builder);

        final org.apache.kafka.streams.Topology topology = builder.build();
        final Properties props = new Properties();
        final Serde<String> serdesString = Serdes.String();
        try(serdesString){
            props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, serdesString.getClass().getName());
            props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, JsonSerde.class.getName());
            testDriver = new TopologyTestDriver(topology, props);
            commandsInTopic = testDriver.createInputTopic(TASK_COMMANDS_TOPIC,
                    new StringSerializer(), new JsonSerializer<>());
            StringDeserializer keyDeserializer = new StringDeserializer();
            KafkaJsonDeserializer<Task> snapshotDeserializer = new KafkaJsonDeserializer<>();
            snapshotDeserializer.configure(Collections.emptyMap(), false);
            snapshotsOutTopic = testDriver.createOutputTopic(TASK_SNAPSHOTS_TOPIC,
                    keyDeserializer, snapshotDeserializer);
            KafkaJsonDeserializer<Object> responseDeserializer = new KafkaJsonDeserializer<>();
            responseDeserializer.configure(Collections.emptyMap(), false);
            commandsResponseTopic = testDriver.createOutputTopic(TASK_REPLIES_TOPIC,
                    keyDeserializer, responseDeserializer);
        }

    }

    @AfterEach
    public void tearDown()
    {
        testDriver.close();
    }

    @Test
    public void shouldProcessCreateTaskCommand() {
        final Headers headers = new RecordHeaders(
                new Header[]{
                    new RecordHeader("domain-model", "CreateTaskCommand".getBytes()),
                    new RecordHeader("correlation-id", "1".getBytes()),
                    new RecordHeader("idempotency-key", "task1".getBytes()),
                    new RecordHeader(":path", "/task".getBytes())
                });

        CreateTaskCommand createTaskCommand = CreateTaskCommand.builder().name("Test").build();
        assertEquals(createTaskCommand.getName(), "Test");

        TestRecord<String, Command> testRecord = new TestRecord<>("task1", createTaskCommand, headers);
        assertEquals(testRecord.key(), "task1");
        assertEquals(testRecord.value(), createTaskCommand);

        AssertionFailedError exception = assertThrows(AssertionFailedError.class,
                () -> assertThat(new TestRecord<>(null, null, headers))
                        .isNull());

        assertThat(exception.getMessage().substring(2, 26).getBytes(StandardCharsets.UTF_8)).isEqualTo(
                new byte[]{101, 120, 112, 101, 99, 116, 101, 100, 58, 32, 110, 117, 108, 108, 13, 10, 32, 98, 117, 116, 32, 119, 97, 115}
        );

        commandsInTopic.pipeInput(testRecord);

        List<KeyValue<String, Object>> response = commandsResponseTopic.readKeyValuesToList();
        assertEquals(1, response.size());

        List<KeyValue<String, Task>> snapshots = snapshotsOutTopic.readKeyValuesToList();
        assertEquals(1, snapshots.size());
    }

    @Test
    public void shouldProcessUpdateTaskCommand() {
        final Headers createHeaders = new RecordHeaders(
                new Header[]{
                    new RecordHeader("domain-model", "CreateTaskCommand".getBytes()),
                    new RecordHeader("correlation-id", "1".getBytes()),
                    new RecordHeader("idempotency-key", "task1".getBytes()),
                    new RecordHeader(":path", "/task".getBytes())
                });
        commandsInTopic.pipeInput(new TestRecord<>("task1", CreateTaskCommand.builder()
                .name("Test")
                .build(), createHeaders));
        final Headers headers = new RecordHeaders(
                new Header[]{
                    new RecordHeader("domain-model", "RenameTaskCommand".getBytes()),
                    new RecordHeader("correlation-id", "1".getBytes()),
                    new RecordHeader("idempotency-key", "task1".getBytes()),
                    new RecordHeader(":path", "/task".getBytes())
                });
        commandsInTopic.pipeInput(new TestRecord<>("task1", RenameTaskCommand.builder()
                .name("Test")
                .build(), headers));
        List<KeyValue<String, Object>> response = commandsResponseTopic.readKeyValuesToList();
        assertEquals(2, response.size());
        List<KeyValue<String, Task>> snapshots = snapshotsOutTopic.readKeyValuesToList();
        assertEquals(2, snapshots.size());
    }

    @Test
    public void shouldProcessDeleteTaskCommand() {
        final Headers headers = new RecordHeaders(
                new Header[]{
                    new RecordHeader("domain-model", "DeleteTaskCommand".getBytes()),
                    new RecordHeader("correlation-id", "1".getBytes()),
                    new RecordHeader("idempotency-key", "task1".getBytes()),
                    new RecordHeader(":path", "/task".getBytes())
                });

        DeleteTaskCommand deleteTaskCommand = DeleteTaskCommand.builder().build();

        TestRecord<String,Command> testRecord = new TestRecord<>("task1", deleteTaskCommand, headers);

        commandsInTopic.pipeInput(testRecord);

        List<KeyValue<String, Object>> response = commandsResponseTopic.readKeyValuesToList();
        assertEquals(1, response.size());
        List<KeyValue<String, Task>> snapshots = snapshotsOutTopic.readKeyValuesToList();
        assertEquals(1, snapshots.size());
        assertNull(snapshots.get(0).value);
    }

    @Test
    public void shouldProcessTaskCommandWithIfMatch() {
        final Headers createHeaders = new RecordHeaders(
                new Header[]{
                    new RecordHeader("domain-model", "CreateTaskCommand".getBytes()),
                    new RecordHeader("correlation-id", "1".getBytes()),
                    new RecordHeader("idempotency-key", "task1".getBytes()),
                    new RecordHeader(":path", "/task".getBytes())
                });
        commandsInTopic.pipeInput(new TestRecord<>("task1", CreateTaskCommand.builder()
                .name("Test")
                .build(), createHeaders));
        final TestRecord<String, Task> testRecord = snapshotsOutTopic.readRecord();
        final Headers updateHeaders = new RecordHeaders(
                new Header[]{
                    new RecordHeader("domain-model", "RenameTaskCommand".getBytes()),
                    new RecordHeader("correlation-id", "1".getBytes()),
                    new RecordHeader("idempotency-key", "task1".getBytes()),
                    new RecordHeader("if-match", testRecord.headers().lastHeader("etag").value()),
                    new RecordHeader(":path", "/task".getBytes())
                });
        commandsInTopic.pipeInput(new TestRecord<>("task1", RenameTaskCommand.builder()
                .name("Test")
                .build(), updateHeaders));
        List<TestRecord<String, Object>> responses = commandsResponseTopic.readRecordsToList();
        assertArrayEquals("204".getBytes(), responses.get(1).headers().lastHeader(":status").value());
        List<KeyValue<String, Task>> snapshots = snapshotsOutTopic.readKeyValuesToList();
        assertEquals(1, snapshots.size());
        assertNotNull(snapshots.get(0).value);
    }

    @Test
    public void shouldProcessTaskCommandWithWrongIfMatch() {
        final Headers createHeaders = new RecordHeaders(
                new Header[]{
                    new RecordHeader("domain-model", "CreateTaskCommand".getBytes()),
                    new RecordHeader("correlation-id", "1".getBytes()),
                    new RecordHeader("idempotency-key", "task1".getBytes()),
                    new RecordHeader(":path", "/task".getBytes())
                });
        commandsInTopic.pipeInput(new TestRecord<>("task1", CreateTaskCommand.builder()
                .name("Test1")
                .build(), createHeaders));
        final Headers updateHeaders = new RecordHeaders(
                new Header[]{
                    new RecordHeader("domain-model", "RenameTaskCommand".getBytes()),
                    new RecordHeader("correlation-id", "1".getBytes()),
                    new RecordHeader("idempotency-key", "task1".getBytes()),
                    new RecordHeader("if-match", "wrong-etag".getBytes()),
                    new RecordHeader(":path", "/task".getBytes())
                });
        commandsInTopic.pipeInput(new TestRecord<>("task1", RenameTaskCommand.builder()
                .name("Test2")
                .build(), updateHeaders));
        List<TestRecord<String, Object>> responses = commandsResponseTopic.readRecordsToList();
        assertArrayEquals("412".getBytes(), responses.get(1).headers().lastHeader(":status").value());
    }

}
