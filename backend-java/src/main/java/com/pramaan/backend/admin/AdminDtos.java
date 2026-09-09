package com.pramaan.backend.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;

/**
 * Use Case 5 — ECS Admin (users, roles, applications). CRUD admin surface only:
 * an in-memory persona registry with <b>no secrets</b> and a read-only canonical
 * role catalogue. Applications are administered through the existing
 * {@code /api/v1/applications} API — not duplicated here.
 */
public final class AdminDtos {

    private AdminDtos() {}

    public record RoleView(String id, String description) {}

    public record UserUpsertRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9._-]{1,63}",
                    message = "username must be lower-case alphanumerics, '.', '_' or '-' (2-64 chars)")
            String username,
            @NotBlank String displayName,
            @Email String email,
            List<String> roles,
            Boolean active) {}

    public record UserView(
            String username,
            String displayName,
            String email,
            List<String> roles,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {}
}
