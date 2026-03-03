package com.vide.vibe.controller;

import com.vide.vibe.model.Category;
import com.vide.vibe.model.CategoryEntry;
import com.vide.vibe.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("categories", categoryService.findAll());
        return "categories/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("category", new Category());
        return "categories/form";
    }

    @PostMapping
    public String create(@ModelAttribute Category category) {
        categoryService.createCategory(category);
        return "redirect:/categories";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        model.addAttribute("category", categoryService.findById(id));
        return "categories/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable UUID id, @ModelAttribute Category category) {
        categoryService.updateCategory(id, category);
        return "redirect:/categories";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id) {
        categoryService.deleteCategory(id);
        return "redirect:/categories";
    }

    @GetMapping("/{id}/entries")
    public String entries(@PathVariable UUID id, Model model) {
        model.addAttribute("category", categoryService.findById(id));
        model.addAttribute("entries", categoryService.findEntriesByCategoryId(id));
        model.addAttribute("newEntry", new CategoryEntry());
        return "categories/entries";
    }

    @PostMapping("/{id}/entries")
    public String createEntry(@PathVariable UUID id, @ModelAttribute CategoryEntry entry) {
        categoryService.createEntry(id, entry);
        return "redirect:/categories/" + id + "/entries";
    }

    @PostMapping("/{categoryId}/entries/{entryId}")
    public String updateEntry(@PathVariable UUID categoryId,
                              @PathVariable UUID entryId,
                              @ModelAttribute CategoryEntry entry) {
        categoryService.updateEntry(entryId, entry);
        return "redirect:/categories/" + categoryId + "/entries";
    }

    @PostMapping("/{categoryId}/entries/{entryId}/delete")
    public String deleteEntry(@PathVariable UUID categoryId, @PathVariable UUID entryId) {
        categoryService.deleteEntry(entryId);
        return "redirect:/categories/" + categoryId + "/entries";
    }
}