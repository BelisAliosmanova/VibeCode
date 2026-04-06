package com.vide.vibe.controller;

import com.vide.vibe.model.ReviewCategory;
import com.vide.vibe.model.ReviewSubCategory;
import com.vide.vibe.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/review-categories")
@RequiredArgsConstructor
public class ReviewCategoryController {

    private final ReviewService reviewService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("categories", reviewService.findAllCategories());
        return "review-categories/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("category", new ReviewCategory());
        return "review-categories/form";
    }

    @PostMapping
    public String create(@ModelAttribute ReviewCategory category) {
        reviewService.createCategory(category);
        return "redirect:/review-categories";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        model.addAttribute("category", reviewService.findCategoryById(id));
        return "review-categories/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable UUID id, @ModelAttribute ReviewCategory category) {
        reviewService.updateCategory(id, category);
        return "redirect:/review-categories";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id) {
        reviewService.deleteCategory(id);
        return "redirect:/review-categories";
    }

    @GetMapping("/{id}/sub-categories")
    public String subList(@PathVariable UUID id, Model model) {
        model.addAttribute("category", reviewService.findCategoryById(id));
        model.addAttribute("subCategories", reviewService.findSubCategoriesByCategoryId(id));
        model.addAttribute("newSub", new ReviewSubCategory());
        return "review-categories/sub-list";
    }

    @PostMapping("/{id}/sub-categories")
    public String createSub(@PathVariable UUID id,
                            @RequestParam String name,
                            @RequestParam(defaultValue = "0") Integer position,
                            @RequestParam(defaultValue = "false") Boolean hasDescriptionBox) {
        ReviewSubCategory sub = ReviewSubCategory.builder()
                .name(name)
                .position(position)
                .hasDescriptionBox(hasDescriptionBox)
                .build();
        reviewService.createSubCategory(id, sub);
        return "redirect:/review-categories/" + id + "/sub-categories";
    }

    @PostMapping("/{categoryId}/sub-categories/{subId}/delete")
    public String deleteSub(@PathVariable UUID categoryId, @PathVariable UUID subId) {
        reviewService.deleteSubCategory(subId);
        return "redirect:/review-categories/" + categoryId + "/sub-categories";
    }
}