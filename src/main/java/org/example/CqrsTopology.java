package org.example;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.example.model.Task;
import org.example.processor.ProcessValidCommandSupplier;
import org.example.processor.RejectInvalidCommandSupplier;
import org.example.processor.ValidateCommandSupplier;
import org.example.serde.CommandJsonDeserializer;
import org.example.serde.SerdeFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CqrsTopology {
    private final Serde<String> stringSerde = Serdes.String();

    private CommandJsonDeserializer commandDeserializer = new CommandJsonDeserializer();
    private final Serde<String> etagSerde = Serdes.String();
    private final Serde<Task> taskSerde = SerdeFactory.jsonSerdeFor(Task.class, false);
    private final Serde<String> responseSerde = Serdes.String();

    @Value("${task.commands.topic}")
    String taskCommandsTopic;

    @Value("${task.snapshots.topic}")
    String taskSnapshotsTopic;

    @Value("${task.replies.topic}")
    String taskRepliesTopic;


    @Autowired
    public void buildPipeline(StreamsBuilder streamsBuilder) {
        final String etagStoreName = "EtagStore";
        final String taskCommandsSource = "TaskCommandsSource";
        final String validateCommand = "ValidateCommand";
        final String taskRepliesSink = "TaskRepliesSink";
        final String rejectInvalidCommand = "RejectInvalidCommand";
        final String processValidCommand = "ProcessValidCommand";
        final String taskSnapshotsSink = "TaskSnapshotsSink";

        // create store
        final StoreBuilder commandStoreBuilder = Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(etagStoreName),
                Serdes.String(),
                etagSerde);

        Topology topologyBuilder = streamsBuilder.build();
        topologyBuilder.addSource(taskCommandsSource, stringSerde.deserializer(), commandDeserializer, taskCommandsTopic)
                .addProcessor(validateCommand, new ValidateCommandSupplier(etagStoreName,
                        processValidCommand, rejectInvalidCommand), taskCommandsSource)
                .addProcessor(processValidCommand,
                        new ProcessValidCommandSupplier(etagStoreName, taskSnapshotsSink, taskRepliesSink),
                        validateCommand)
                .addProcessor(rejectInvalidCommand, new RejectInvalidCommandSupplier(taskRepliesSink),
                        validateCommand)
                .addSink(taskRepliesSink, taskRepliesTopic, stringSerde.serializer(), responseSerde.serializer(),
                        processValidCommand, rejectInvalidCommand)
                .addSink(taskSnapshotsSink, taskSnapshotsTopic, stringSerde.serializer(), taskSerde.serializer(),
                        processValidCommand)
                .addStateStore(commandStoreBuilder, validateCommand, processValidCommand);
        System.out.println(topologyBuilder.describe());
    }
}

