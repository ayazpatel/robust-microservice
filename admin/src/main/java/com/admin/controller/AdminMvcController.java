package com.admin.controller;

import com.admin.controller.dto.AdminRequest;
import com.admin.model.Admin;
import com.admin.service.AdminService;
import com.admin.service.AppsClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admins")
public class AdminMvcController {

    private final AdminService service;
    private final AppsClient appsClient;

    public AdminMvcController(AdminService service, AppsClient appsClient) {
        this.service = service;
        this.appsClient = appsClient;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("admins", service.findAll());
        return "admins/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("admin", new Admin());
        model.addAttribute("apps", appsClient.getApps());
        model.addAttribute("plans", Admin.ExpiryPlan.values());
        model.addAttribute("mode", "create");
        return "admins/form";
    }

    @PostMapping
    public String create(@ModelAttribute AdminRequest req) {
        service.create(req);
        return "redirect:/admins";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable("id") String id, Model model) {
        return service.findById(id)
                .map(a -> {
                    model.addAttribute("admin", a);
                    model.addAttribute("apps", appsClient.getApps());
                    model.addAttribute("plans", Admin.ExpiryPlan.values());
                    model.addAttribute("mode", "edit");
                    return "admins/form";
                })
                .orElse("redirect:/admins");
    }

    @PostMapping("/{id}")
    public String update(@PathVariable("id") String id, @ModelAttribute AdminRequest req) {
        service.update(id, req);
        return "redirect:/admins";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") String id) {
        service.deleteById(id);
        return "redirect:/admins";
    }
}
