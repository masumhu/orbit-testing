package com.devhub.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class ProjectRequests {
    private ProjectRequests() { }
    public record CreateProjectRequest(@NotBlank @Size(max = 120) String name, @Size(max = 4000) String description,
                                       List<String> seniorManagerIds) { }
    public record UpdateProjectRequest(@NotBlank @Size(max = 120) String name, @Size(max = 4000) String description,
                                       List<String> seniorManagerIds, ProjectStatus status) { }
}
