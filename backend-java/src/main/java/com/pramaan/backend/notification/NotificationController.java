package com.pramaan.backend.notification;

import com.pramaan.backend.notification.NotificationService.NotificationList;
import com.pramaan.backend.notification.NotificationService.NotificationView;
import com.pramaan.backend.notification.NotificationService.SettingsView;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Role scoping: a caller only sees and marks notifications addressed to their own X-Pramaan-Role. */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final String ROLE = "X-Pramaan-Role";

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public NotificationList list(@RequestHeader(ROLE) String role,
                                 @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return service.list(role.trim(), unreadOnly);
    }

    @PostMapping("/{id}/read")
    public NotificationView read(@PathVariable UUID id, @RequestHeader(ROLE) String role) {
        return service.markRead(id, role.trim());
    }

    @PostMapping("/read-all")
    public NotificationList readAll(@RequestHeader(ROLE) String role) {
        return service.markAllRead(role.trim());
    }

    @GetMapping("/settings")
    public SettingsView settings() {
        return service.getSettings();
    }

    @PutMapping("/settings")
    public SettingsView saveSettings(@RequestBody(required = false) SettingsView req) {
        return service.saveSettings(req);
    }
}
