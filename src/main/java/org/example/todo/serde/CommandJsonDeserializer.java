package org.example.todo.serde;

import org.example.todo.model.Command;
import org.example.todo.model.CreateTaskCommand;
import org.example.todo.model.DeleteTaskCommand;
import org.example.todo.model.RenameTaskCommand;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.serializers.jackson.Jackson;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandJsonDeserializer implements Deserializer<Command> {
    private final ObjectMapper objectMapper;
    private static final Logger logger = LoggerFactory.getLogger(CommandJsonDeserializer.class);

    public CommandJsonDeserializer() {

        this.objectMapper = Jackson.newObjectMapper();

        logger.info("Instantiating CommandJsonDeserializer with Jackson Object Mapper registered with modules: " +
                "    new ParameterNamesModule()), new Jdk8Module());, new JavaTimeModule();");
    }

    @Override
    public Command deserialize(String topic, Headers headers, byte[] data) {

        final Header domainModelHeader = headers.lastHeader("domain-model");

        final Header correlationId = headers.lastHeader("correlation-id");

        final String domainModel = new String(domainModelHeader.value());

        if (correlationId != null) {
            JavaType type = switch (domainModel) {
                case "CreateTaskCommand" -> objectMapper.getTypeFactory().constructType(CreateTaskCommand.class);
                case "RenameTaskCommand" -> objectMapper.getTypeFactory().constructType(RenameTaskCommand.class);
                case "DeleteTaskCommand" -> objectMapper.getTypeFactory().constructType(DeleteTaskCommand.class);
                default -> null;
            };
            return deserialize(data, type);
        }
        else {
            throw new IllegalArgumentException("Missing correlation-id header");
        }
    }

    @Override
    public Command deserialize(String s, byte[] bytes)
    {
        return null;
    }

    Command deserialize(byte[] bytes, JavaType type) {
        if (bytes != null && bytes.length != 0) {
            try {
                return this.objectMapper.readValue(bytes, type);
            }
            catch (Exception var4)
            {
                throw new SerializationException(var4);
            }
        }
        else {
            return new DeleteTaskCommand();
        }
    }
}
