package org.example.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.model.Command;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class RenameTaskCommand implements Command {
    String name;
}
