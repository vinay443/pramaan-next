package com.pramaan.backend.admin;

import com.pramaan.backend.admin.AdminDtos.RoleView;
import com.pramaan.backend.admin.AdminDtos.UserUpsertRequest;
import com.pramaan.backend.admin.AdminDtos.UserView;
import com.pramaan.backend.common.ApiException;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Use Case 5 — ECS Admin console API: personas + canonical role catalogue. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    @GetMapping("/roles")
    public List<RoleView> roles() {
        return service.roles();
    }

    @GetMapping("/users")
    public List<UserView> users() {
        return service.listUsers();
    }

    @GetMapping("/users/{username}")
    public UserView user(@PathVariable String username) {
        return service.getUser(username);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@Valid @RequestBody UserUpsertRequest req) {
        return service.upsert(req);
    }

    @PutMapping("/users/{username}")
    public UserView update(@PathVariable String username, @Valid @RequestBody UserUpsertRequest req) {
        if (!username.equalsIgnoreCase(req.username())) {
            throw ApiException.badRequest("username in path and body must match");
        }
        return service.upsert(req);
    }

    @PutMapping("/users/{username}/active")
    public UserView setActive(@PathVariable String username, @RequestParam boolean value) {
        return service.setActive(username, value);
    }

    @DeleteMapping("/users/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String username) {
        service.delete(username);
    }
}
