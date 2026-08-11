package com.vide.vibe.controller;

import com.vide.vibe.model.Category;
import com.vide.vibe.model.CategoryEntry;
import com.vide.vibe.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    // ── Category CRUD ──────────────────────────────────────────────────────────

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

    /**
     * Merge one category into another: moves all of its entries onto the
     * target category, then removes the now-empty source category.
     */
    @PostMapping("/{id}/merge")
    public String merge(@PathVariable UUID id, @RequestParam UUID targetCategoryId) {
        categoryService.mergeCategories(id, targetCategoryId);
        return "redirect:/categories";
    }

    // ── Entries CRUD ───────────────────────────────────────────────────────────

    @GetMapping("/{id}/entries")
    public String entries(@PathVariable UUID id, Model model) {
        List<Category> allCategories = categoryService.findAll();

        model.addAttribute("category", categoryService.findById(id));
        model.addAttribute("entries", categoryService.findEntriesByCategoryId(id));
        model.addAttribute("newEntry", new CategoryEntry());
        model.addAttribute("otherCategories", allCategories.stream()
                .filter(c -> !c.getId().equals(id))
                .collect(Collectors.toList()));

        model.addAttribute("appCounts", categoryService.countAppsPerEntry(id));
        return "categories/entries";
    }

    @PostMapping("/{id}/entries")
    public String createEntry(
            @PathVariable UUID id,
            @RequestParam String name,
            @RequestParam String slug,
            @RequestParam(defaultValue = "0") Integer interest,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "0") Integer position,
            @RequestParam(defaultValue = "false") Boolean visibility,
            @RequestParam(required = false) MultipartFile icon) {

        CategoryEntry entry = CategoryEntry.builder()
                .name(name)
                .slug(slug)
                .interest(interest)
                .description(description)
                .position(position)
                .visibility(visibility)
                .build();

        categoryService.createEntry(id, entry, icon);
        return "redirect:/categories/" + id + "/entries";
    }

    @PostMapping("/{categoryId}/entries/{entryId}/icon")
    public String updateEntryIcon(
            @PathVariable UUID categoryId,
            @PathVariable UUID entryId,
            @RequestParam MultipartFile icon) {
        categoryService.updateEntryIcon(entryId, icon);
        return "redirect:/categories/" + categoryId + "/entries";
    }

    /**
     * Move a single entry to a different category. Stays on the source
     * category's entries page and shows a flash confirmation instead of
     * jumping over to the target category.
     */
    @PostMapping("/{categoryId}/entries/{entryId}/move")
    public String moveEntry(
            @PathVariable UUID categoryId,
            @PathVariable UUID entryId,
            @RequestParam UUID targetCategoryId,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        CategoryEntry moved = categoryService.moveEntry(entryId, targetCategoryId);
        redirectAttributes.addFlashAttribute("moveMessage",
                "\"" + moved.getName() + "\" moved to \"" + moved.getCategory().getName() + "\"");
        return "redirect:/categories/" + categoryId + "/entries";
    }

    @PostMapping("/{categoryId}/entries/{entryId}/delete")
    public String deleteEntry(@PathVariable UUID categoryId, @PathVariable UUID entryId) {
        categoryService.deleteEntry(entryId);
        return "redirect:/categories/" + categoryId + "/entries";
    }

    /**
     * Merge one entry into another within the same category. Moves app
     * selections over and removes the source entry.
     */
    @PostMapping("/{categoryId}/entries/{entryId}/merge")
    public String mergeEntry(
            @PathVariable UUID categoryId,
            @PathVariable UUID entryId,
            @RequestParam UUID targetEntryId,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        CategoryEntry target = categoryService.mergeEntries(entryId, targetEntryId);
        redirectAttributes.addFlashAttribute("moveMessage",
                "Merged into \"" + target.getName() + "\"");
        return "redirect:/categories/" + categoryId + "/entries";
    }

    /**
     * Inline-rename an entry. Called via fetch() from the entries page,
     * returns JSON so the pill can update without a full page reload.
     */
    @PostMapping("/{categoryId}/entries/{entryId}/rename")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, String> renameEntry(
            @PathVariable UUID categoryId,
            @PathVariable UUID entryId,
            @RequestParam String name) {
        CategoryEntry updated = categoryService.updateEntryName(entryId, name);
        return java.util.Map.of("id", updated.getId().toString(), "name", updated.getName());
    }

}