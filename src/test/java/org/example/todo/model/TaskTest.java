package org.example.todo.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class TaskTest {

    private final Task taskA = Task.builder().name("taskAName").build();

    private final Task taskB = new Task("taskBName");

    @Test
    void getName() {
        assertThat(taskA.getName()).isEqualTo("taskAName");
    }

    @Test
    void setName() {
        assertThat(taskB.getName()).isEqualTo("taskBName");
        taskB.setName("updatedName");
        assertThat(taskB.getName()).isEqualTo("updatedName");
    }

    @Test
    void testEquals() {
        assertThat(taskA.equals(taskB)).isFalse();
        taskB.setName("taskAName");
        assertThat(taskA.equals(taskB)).isTrue();
    }

    @Test
    void canEqual() {
    }

    @Test
    void testHashCode() {
        assertThat(taskA.hashCode() == taskB.hashCode()).isFalse();
        taskB.setName("taskAName");
        assertThat(taskA.hashCode() == taskB.hashCode()).isTrue();
    }

    @Test
    void testToString() {
        assertThat(taskA.toString()).isEqualTo("Task(name=taskAName)");
    }

    @Test
    void builder() {
    }
}