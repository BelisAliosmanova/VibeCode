package com.vide.vibe.controller;

import com.vide.vibe.model.App;
import com.vide.vibe.service.AppService;
import com.vide.vibe.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/apps")
public class AppController {

    private final AppService appService;
    private final UserService userService;

    public AppController(AppService appService, UserService userService) {
        this.appService = appService;
        this.userService = userService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("apps", appService.findAll());
        return "apps/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("app", new App());
        model.addAttribute("users", userService.findAll());
        model.addAttribute("statuses", App.Status.values());
        model.addAttribute("visibilities", App.Visibility.values());
        return "apps/form";
    }

    @PostMapping
    public String create(@ModelAttribute App app,
                         @RequestParam UUID ownerId) {
        appService.create(app, ownerId);
        return "redirect:/apps";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable UUID id, Model model) {
        model.addAttribute("app", appService.findById(id));
        return "apps/view";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        model.addAttribute("app", appService.findById(id));
        model.addAttribute("users", userService.findAll());
        model.addAttribute("statuses", App.Status.values());
        model.addAttribute("visibilities", App.Visibility.values());
        return "apps/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable UUID id, @ModelAttribute App app) {
        appService.update(id, app);
        return "redirect:/apps";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id) {
        appService.delete(id);
        return "redirect:/apps";
    }
}