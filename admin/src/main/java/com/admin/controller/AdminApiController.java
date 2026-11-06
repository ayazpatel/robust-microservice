package com.admin.controller;

import com.admin.controller.dto.AdminRequest;
import com.admin.controller.dto.AdminResponse;
import com.admin.model.Admin;
import com.admin.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admins")
public class AdminApiController {

    private final AdminService service;

    public AdminApiController(AdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminResponse> all() {
        return service.findAll().stream().map(AdminResponse::from).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminResponse> get(@PathVariable("id") String id) {
        return service.findById(id)
                .map(AdminResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<AdminResponse> create(@Valid @RequestBody AdminRequest req) {
        Admin created = service.create(req);
        return ResponseEntity.created(URI.create("/api/admins/" + created.getAdminId()))
                .body(AdminResponse.from(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminResponse> update(@PathVariable("id") String id, @Valid @RequestBody AdminRequest req) {
        return service.update(id, req)
                .map(AdminResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
