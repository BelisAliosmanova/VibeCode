package com.vide.vibe.service;

import com.vide.vibe.model.*;
import com.vide.vibe.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryEntryRepository categoryEntryRepository;
    private final AppCategoryEntryRepository appCategoryEntryRepository;
    private final MediaService mediaService;

    public List<Category> findAll() {
        return categoryRepository.findAllByDeletedAtIsNullOrderByPositionAsc();
    }

    public List<Category> findAllVisible() {
        return categoryRepository.findAllByVisibilityTrueAndDeletedAtIsNullOrderByPositionAsc();
    }

    public List<Category> findAllFilterVisible() {
        return categoryRepository.findAllByFilterVisibleTrueAndDeletedAtIsNullOrderByPositionAsc();
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
        existing.setFilterVisible(updated.getFilterVisible());
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
    public CategoryEntry createEntry(UUID categoryId, CategoryEntry entry, MultipartFile icon) {
        Category category = findById(categoryId);
        entry.setCategory(category);

        if (icon != null && !icon.isEmpty()) {
            try {
                String iconUrl = mediaService.upload(icon, "entry-icons");
                entry.setIconUrl(iconUrl);
            } catch (IOException e) {
                throw new RuntimeException("Failed to upload entry icon: " + e.getMessage(), e);
            }
        }

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
    public CategoryEntry updateEntryIcon(UUID id, MultipartFile icon) {
        CategoryEntry existing = findEntryById(id);
        if (existing.getIconUrl() != null) {
            mediaService.delete(existing.getIconUrl());
        }
        try {
            String iconUrl = mediaService.upload(icon, "entry-icons");
            existing.setIconUrl(iconUrl);
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload entry icon: " + e.getMessage(), e);
        }
        return categoryEntryRepository.save(existing);
    }

    @Transactional
    public void deleteEntry(UUID id) {
        CategoryEntry entry = findEntryById(id);
        entry.softDelete();
        categoryEntryRepository.save(entry);
    }

    @Transactional
    public void saveAppSelections(App app, UUID categoryId, List<UUID> entryIds) {
        appCategoryEntryRepository.deleteByAppIdAndCategoryId(app.getId(), categoryId);

        Category category = findById(categoryId);
        if (category.getMaxSelected() != null && entryIds.size() > category.getMaxSelected()) {
            throw new RuntimeException("Too many selections for category: " + category.getName());
        }

        entryIds.forEach(entryId -> {
            CategoryEntry entry = findEntryById(entryId);
            AppCategoryEntry selection = AppCategoryEntry.builder()
                    .app(app)
                    .entry(entry)
                    .build();
            appCategoryEntryRepository.save(selection);
        });
    }

    public List<UUID> findSelectedEntryIds(UUID appId, UUID categoryId) {
        return appCategoryEntryRepository
                .findByAppIdAndCategoryId(appId, categoryId)
                .stream()
                .map(ace -> ace.getEntry().getId())
                .toList();
    }

    public List<AppCategoryEntry> findAllSelectionsForApp(UUID appId) {
        return appCategoryEntryRepository.findAllByAppId(appId);
    }
}