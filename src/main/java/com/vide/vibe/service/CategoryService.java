package com.vide.vibe.service;

import com.vide.vibe.model.*;
import com.vide.vibe.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryEntryRepository categoryEntryRepository;
    private final AppCategoryEntryRepository appCategoryEntryRepository;

    // ── Categories ────────────────────────────────────────────────────────────

    public List<Category> findAll() {
        return categoryRepository.findAllByDeletedAtIsNullOrderByPositionAsc();
    }

    public List<Category> findAllVisible() {
        return categoryRepository.findAllByVisibilityTrueAndDeletedAtIsNullOrderByPositionAsc();
    }

    public Category findById(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found: " + id));
    }

    public Category findBySlug(String slug) {
        return categoryRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new RuntimeException("Category not found: " + slug));
    }

    @Transactional
    public Category createCategory(Category category) {
        return categoryRepository.save(category);
    }

    @Transactional
    public Category updateCategory(UUID id, Category updated) {
        Category existing = findById(id);
        existing.setName(updated.getName());
        existing.setSlug(updated.getSlug());
        existing.setVisibility(updated.getVisibility());
        existing.setMaxSelected(updated.getMaxSelected());
        existing.setPosition(updated.getPosition());
        existing.setDescription(updated.getDescription());
        existing.setAllowCustom(updated.getAllowCustom());
        return categoryRepository.save(existing);
    }

    @Transactional
    public void deleteCategory(UUID id) {
        Category category = findById(id);
        category.softDelete();
        categoryRepository.save(category);
    }

    // ── Category Entries ──────────────────────────────────────────────────────

    public List<CategoryEntry> findEntriesByCategoryId(UUID categoryId) {
        return categoryEntryRepository
                .findAllByCategoryIdAndDeletedAtIsNullOrderByPositionAscInterestDesc(categoryId);
    }

    public List<CategoryEntry> findVisibleEntriesByCategoryId(UUID categoryId) {
        return categoryEntryRepository
                .findAllByCategoryIdAndVisibilityTrueAndDeletedAtIsNullOrderByPositionAscInterestDesc(categoryId);
    }

    public CategoryEntry findEntryById(UUID id) {
        return categoryEntryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("CategoryEntry not found: " + id));
    }

    @Transactional
    public CategoryEntry createEntry(UUID categoryId, CategoryEntry entry) {
        Category category = findById(categoryId);
        entry.setCategory(category);
        return categoryEntryRepository.save(entry);
    }

    @Transactional
    public CategoryEntry updateEntry(UUID id, CategoryEntry updated) {
        CategoryEntry existing = findEntryById(id);
        existing.setName(updated.getName());
        existing.setSlug(updated.getSlug());
        existing.setInterest(updated.getInterest());
        existing.setPosition(updated.getPosition());
        existing.setVisibility(updated.getVisibility());
        return categoryEntryRepository.save(existing);
    }

    @Transactional
    public void deleteEntry(UUID id) {
        CategoryEntry entry = findEntryById(id);
        entry.softDelete();
        categoryEntryRepository.save(entry);
    }

    // ── App Category Selections ───────────────────────────────────────────────

    /**
     * Save the user's selections for one category step.
     * Replaces any previous selections for that category.
     */
    @Transactional
    public void saveAppSelections(App app, UUID categoryId, List<UUID> entryIds) {
        // Clear previous selections for this category
        appCategoryEntryRepository.deleteByAppIdAndCategoryId(app.getId(), categoryId);

        // Validate against max_selected
        Category category = findById(categoryId);
        if (category.getMaxSelected() != null && entryIds.size() > category.getMaxSelected()) {
            throw new RuntimeException("Too many selections for category: " + category.getName());
        }

        // Save new selections
        entryIds.forEach(entryId -> {
            CategoryEntry entry = findEntryById(entryId);
            AppCategoryEntry selection = AppCategoryEntry.builder()
                    .app(app)
                    .entry(entry)
                    .build();
            appCategoryEntryRepository.save(selection);
        });
    }

    /**
     * Get all selected entry IDs for an app within a specific category.
     */
    public List<UUID> findSelectedEntryIds(UUID appId, UUID categoryId) {
        return appCategoryEntryRepository
                .findByAppIdAndCategoryId(appId, categoryId)
                .stream()
                .map(ace -> ace.getEntry().getId())
                .toList();
    }

    /**
     * Get all selections for an app across all categories.
     */
    public List<AppCategoryEntry> findAllSelectionsForApp(UUID appId) {
        return appCategoryEntryRepository.findAllByAppId(appId);
    }
}