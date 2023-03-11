# Todo application using a CQRS design pattern that's backed by Apache Kafka and Zilla as the event-driven API gateway

- Define a Tasks API to send commands to the Todo service via Kafka and retrieve a continuously updated stream of tasks from Kafka as needed by the Tasks UX

![Screenshot_20230311_075309](https://user-images.githubusercontent.com/54422342/224494518-63f5609c-8871-4f49-9767-e095d8e355b4.png)


#### This Todo Application has the following goals:
- Provide a list of Todo tasks that is shared by all clients
- Support optimistic locking with conflict detection when attempting to update a Todo task
- Deliver updates in near real-time when a Todo task is created, modified, or deleted
- Demonstrate a user interface driving the Tasks API
- Support scaling Todo task reads and writes

#### Prerequisites
- Docker 20.10.14
- Git 2.32.0
- npm 8.3.1  and above

### Basic infrastructure components for our event-driven architecture
- Run docker swarm init to initiateSwarm orchestrator.
- Create stack.yml and add Apache Kafka.

### Spin up Apache Kafka and create the following topics
```
docker stack deploy -c stack.yml example --resolve-image never
```
| Topic | Description |
| :---         |     :---:      |
| task-commands   | Queues commands to be processed by the Todo service    |
| task-replies     | Queues the response from the Todo service after processing each command      |
| task-snapshots     | Captures the latest snapshot of each task entity      |

#### Verify that the Kafka topics have been successfully created
```
docker service logs example_init-topics --follow --raw
```

### Todo service that is implemented using Spring boot + Kafka Streams to process commands and generate relevant output
- This Todo service can deliver near real-time updates when a Task is created, renamed, or deleted, and produces a message to the Kafka task-snapshots topic with the updated value.
- Combining this with cleanup-policy: compact for the task-snapshots topic causes the topic to behave more like a table, where only the most recent message for each distinct message key is retained.
- This approach is used as the source of truth for the current state of our Todo service, setting the Kafka message key to the Task identifier to retain all the distinct Tasks.
- When a Task is deleted, we will produce a tombstone message (null value) to the task-snapshots topic causing that Task identifier to no longer be retained in Kafka.
- Commands arrive at the Tasks service via the task-commands topic and correlated replies are sent to the task-replies topic with the same correlation-id value that was received with the inbound command message.

### Implementing the Todo domain using these topics gives us the following Kafka Streams topology

![Screenshot_20230311_081510](https://user-images.githubusercontent.com/54422342/224495327-acfda215-3f98-4e84-b86a-83675e405aaa.png)

### Expose the API that is based on Kafka Streams

### Secure the Tasks API using JWT guard to enforce authorization of the read:tasks and write:tasks roles
