package com.vide.vibe.controller;

import com.vide.vibe.model.*;
import com.vide.vibe.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Controller
@RequestMapping("/submit")
@RequiredArgsConstructor
public class SubmitController {

    private final AppService appService;
    private final UserService userService;
    private final CategoryService categoryService;

    @GetMapping
    public String landing() {
        return "submit/landing";
    }

    @PostMapping("/start")
    public String start(@RequestParam String appUrl, Model model) {
        App app = new App();
        app.setUrl(appUrl);
        model.addAttribute("app", app);
        return "submit/details";
    }

    @GetMapping("/details")
    public String detailsForm(@RequestParam(required = false) UUID appId, Model model) {
        App app = appId != null ? appService.findById(appId) : new App();
        model.addAttribute("app", app);
        return "submit/details";
    }

    @PostMapping("/details")
    public String saveDetails(@ModelAttribute App app,
                              @RequestParam String ownerEmail,
                              @RequestParam(required = false) MultipartFile icon) {
        User owner;
        try {
            owner = userService.findByEmail(ownerEmail);
        } catch (RuntimeException e) {
            owner = userService.create(User.builder()
                    .email(ownerEmail)
                    .passwordHash("")
                    .role(User.Role.USER)
                    .status(User.Status.PENDING)
                    .build());
        }

        App saved = app.getId() != null
                ? appService.update(app.getId(), app)
                : appService.create(app, owner.getId());

        List<Category> categories = categoryService.findAllVisible();
        if (categories.isEmpty()) {
            return "redirect:/submit/success?appId=" + saved.getId();
        }
        return "redirect:/submit/step?appId=" + saved.getId() + "&categoryId=" + categories.get(0).getId();
    }

    @GetMapping("/step")
    public String categoryStep(@RequestParam UUID appId,
                               @RequestParam UUID categoryId,
                               Model model) {
        App app = appService.findById(appId);
        Category category = categoryService.findById(categoryId);
        List<CategoryEntry> entries = categoryService.findVisibleEntriesByCategoryId(categoryId);
        List<UUID> selectedIds = categoryService.findSelectedEntryIds(appId, categoryId);

        List<Category> allCategories = categoryService.findAllVisible();
        int currentIndex = allCategories.indexOf(category);
        currentIndex = findIndexById(allCategories, categoryId);

        Category prevCategory = currentIndex > 0 ? allCategories.get(currentIndex - 1) : null;
        Category nextCategory = currentIndex < allCategories.size() - 1 ? allCategories.get(currentIndex + 1) : null;

        model.addAttribute("app", app);
        model.addAttribute("category", category);
        model.addAttribute("entries", entries);
        model.addAttribute("selectedIds", selectedIds);
        model.addAttribute("prevCategory", prevCategory);
        model.addAttribute("nextCategory", nextCategory);
        model.addAttribute("stepNumber", currentIndex + 2); // +2 because step 1 is details
        model.addAttribute("totalSteps", allCategories.size() + 1);
        model.addAttribute("isLastStep", nextCategory == null);

        return "submit/step";
    }

    @PostMapping("/step")
    public String saveStep(@RequestParam UUID appId,
                           @RequestParam UUID categoryId,
                           @RequestParam(required = false) List<UUID> entryIds,
                           @RequestParam(required = false) UUID nextCategoryId) {

        App app = appService.findById(appId);
        categoryService.saveAppSelections(app, categoryId, entryIds != null ? entryIds : List.of());

        if (nextCategoryId != null) {
            return "redirect:/submit/step?appId=" + appId + "&categoryId=" + nextCategoryId;
        }

        appService.submit(appId);
        return "redirect:/submit/success?appId=" + appId;
    }

    @GetMapping("/success")
    public String success(@RequestParam UUID appId, Model model) {
        model.addAttribute("app", appService.findById(appId));
        return "submit/success";
    }

    private int findIndexById(List<Category> categories, UUID id) {
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).getId().equals(id)) return i;
        }
        return -1;
    }
}