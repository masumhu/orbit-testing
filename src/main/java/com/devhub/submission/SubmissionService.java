package com.devhub.submission;

import com.devhub.auth.RegistrationRole;
import com.devhub.auth.UserAccount;
import com.devhub.project.Project;
import com.devhub.project.ProjectService;
import com.devhub.task.Task;
import com.devhub.task.TaskService;
import com.devhub.task.TaskStatus;
import com.devhub.ticket.TicketPriority;
import com.devhub.ticket.TicketService;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class SubmissionService {
    private final CodeSubmissionRepository submissions;
    private final ProjectService projects;
    private final TaskService tasks;
    private final TicketService tickets;

    public SubmissionService(CodeSubmissionRepository submissions, ProjectService projects, TaskService tasks, TicketService tickets) {
        this.submissions = submissions; this.projects = projects; this.tasks = tasks; this.tickets = tickets;
    }

    public CodeSubmission submit(String userId, String taskId, SubmissionRequests.CreateSubmissionRequest request) {
        UserAccount developer = projects.requireRole(userId, RegistrationRole.DEVELOPER);
        Task task = tasks.find(taskId);
        if (!developer.id().equals(task.assignedDeveloperId())) throw new IllegalArgumentException("Only the assigned Developer can submit work for this task");
        projects.requireView(developer, projects.find(task.projectId()));
        if (task.status() == TaskStatus.COMPLETED || task.status() == TaskStatus.CANCELLED) throw new IllegalArgumentException("Completed or cancelled tasks do not accept submissions");
        if (blank(request.repositoryUrl()) && blank(request.storageReference()) && blank(request.pullRequestUrl()) && blank(request.commitSha())) {
            throw new IllegalArgumentException("Provide a repository, pull request, commit, or external storage reference");
        }
        validateHttps(request.repositoryUrl(), "repositoryUrl");
        validateHttps(request.pullRequestUrl(), "pullRequestUrl");
        projects.find(task.projectId());
        Instant now = Instant.now();
        CodeSubmission saved = submissions.save(new CodeSubmission(null, task.organizationId(), task.projectId(), task.id(), developer.id(),
                clean(request.repositoryUrl()), clean(request.branch()), clean(request.commitSha()), clean(request.pullRequestUrl()),
                clean(request.storageReference()), null, SubmissionStatus.SUBMITTED, now, now));
        if (task.status() != TaskStatus.SUBMITTED) tasks.markSubmitted(task);
        return saved;
    }

    public List<CodeSubmission> listProject(String userId, String projectId) {
        Project project = projects.find(projectId);
        UserAccount user = projects.user(userId);
        projects.requireView(user, project);
        List<CodeSubmission> values = submissions.findByProjectId(projectId);
        if (user.role() == RegistrationRole.OWNER) return values;
        if (user.role() == RegistrationRole.PRODUCT_MANAGER) return values.stream()
                .filter(s -> s.status() != SubmissionStatus.SUBMITTED).toList();
        if (user.role() == RegistrationRole.SENIOR_MANAGER) return values;
        List<String> ticketSubmissionIds = tickets.listProject(userId, projectId).stream()
                .map(com.devhub.ticket.Ticket::submissionId).filter(java.util.Objects::nonNull).toList();
        return values.stream().filter(s -> s.developerId().equals(user.id()) || ticketSubmissionIds.contains(s.id())).toList();
    }

    public CodeSubmission forward(String userId, String submissionId) {
        UserAccount senior = projects.requireRole(userId, RegistrationRole.SENIOR_MANAGER);
        CodeSubmission submission = find(submissionId);
        Task task = tasks.find(submission.taskId());
        projects.requireView(senior, projects.find(submission.projectId()));
        if (!task.createdBySeniorManagerId().equals(senior.id())) throw new IllegalArgumentException("Only the task's Senior Manager can forward this submission");
        if (submission.status() != SubmissionStatus.SUBMITTED) throw new IllegalArgumentException("Only a new submission can be forwarded");
        return update(submission, SubmissionStatus.FORWARDED_TO_PRODUCT_MANAGER, clean(submission.reviewNote()));
    }

    public CodeSubmission review(String userId, String submissionId, SubmissionRequests.ReviewSubmissionRequest request) {
        UserAccount pm = projects.requireRole(userId, RegistrationRole.PRODUCT_MANAGER);
        CodeSubmission submission = find(submissionId);
        Project project = projects.find(submission.projectId());
        projects.requireView(pm, project);
        if (submission.status() != SubmissionStatus.FORWARDED_TO_PRODUCT_MANAGER) throw new IllegalArgumentException("Senior Manager must forward the submission before Product Manager review");
        Task task = tasks.find(submission.taskId());
        if (request.decision() == SubmissionRequests.ReviewDecision.ACCEPT) {
            tasks.markCompleted(task);
            return update(submission, SubmissionStatus.ACCEPTED, clean(request.note()));
        }
        if (blank(request.ticketTitle()) || blank(request.ticketDescription()) || request.priority() == null || blank(request.codeLocation())) {
            throw new IllegalArgumentException("A change request requires ticket title, description, priority, and code location");
        }
        String title = request.ticketTitle().trim();
        String description = request.ticketDescription().trim();
        String codeLocation = request.codeLocation().trim();
        tickets.createFromSubmission(pm.id(), project, submission.id(), task.createdBySeniorManagerId(),
                title, description, request.priority(), codeLocation);
        return update(submission, SubmissionStatus.CHANGES_REQUESTED, clean(request.note()));
    }

    public CodeSubmission find(String id) { return submissions.findById(id).orElseThrow(() -> new NoSuchElementException("Code submission was not found")); }
    private CodeSubmission update(CodeSubmission old, SubmissionStatus status, String note) {
        return submissions.save(new CodeSubmission(old.id(), old.organizationId(), old.projectId(), old.taskId(), old.developerId(),
                old.repositoryUrl(), old.branch(), old.commitSha(), old.pullRequestUrl(), old.storageReference(), note,
                status, old.createdAt(), Instant.now()));
    }
    private void validateHttps(String value, String field) {
        if (blank(value)) return;
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) throw new IllegalArgumentException();
        } catch (RuntimeException ex) { throw new IllegalArgumentException(field + " must be a valid HTTPS URL"); }
    }
    private String clean(String value) { return value == null ? null : value.trim(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
