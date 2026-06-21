package com.vide.vibe.service;

import com.vide.vibe.model.App;
import com.vide.vibe.model.HomeSection;
import com.vide.vibe.model.HomeSectionApp;
import com.vide.vibe.repository.HomeSectionAppRepository;
import com.vide.vibe.repository.HomeSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HomeSectionService {

    private final HomeSectionRepository    homeSectionRepository;
    private final HomeSectionAppRepository homeSectionAppRepository;
    private final AppService               appService;

    public List<HomeSection> findAllOrdered() {
        return homeSectionRepository.findAllByDeletedAtIsNullOrderByPositionAsc();
    }

    public HomeSection findById(UUID id) {
        return homeSectionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Home section not found: " + id));
    }

    /** apps in LIST slot, in order (max 5 enforced on the way in). */
    public List<App> findListApps(UUID sectionId) {
        return homeSectionAppRepository.findAllByHomeSectionIdOrderByPositionAsc(sectionId)
                .stream()
                .filter(hsa -> hsa.getSlot() == HomeSectionApp.Slot.LIST)
                .map(HomeSectionApp::getApp)
                .toList();
    }

    public App findFeaturedApp(UUID sectionId) {
        return homeSectionAppRepository.findAllByHomeSectionIdOrderByPositionAsc(sectionId)
                .stream()
                .filter(hsa -> hsa.getSlot() == HomeSectionApp.Slot.FEATURED)
                .map(HomeSectionApp::getApp)
                .findFirst()
                .orElse(null);
    }

    /** Convenience for the homepage: sectionId -> {listApps, featuredApp}. */
    public Map<UUID, List<App>> findAllListAppsBySection() {
        Map<UUID, List<App>> result = new LinkedHashMap<>();
        for (HomeSection section : findAllOrdered()) {
            result.put(section.getId(), findListApps(section.getId()));
        }
        return result;
    }

    public Map<UUID, App> findAllFeaturedAppsBySection() {
        Map<UUID, App> result = new LinkedHashMap<>();
        for (HomeSection section : findAllOrdered()) {
            result.put(section.getId(), findFeaturedApp(section.getId()));
        }
        return result;
    }

    @Transactional
    public HomeSection create(String title, HomeSection.Layout layout,
                              List<UUID> listAppIds, UUID featuredAppId) {
        List<HomeSection> existing = findAllOrdered();

        HomeSection section = HomeSection.builder()
                .title(title)
                .layout(layout != null ? layout : HomeSection.Layout.FIVE_PLUS_ONE)
                .position(existing.size())
                .build();
        section = homeSectionRepository.save(section);

        saveAppSelections(section.getId(), listAppIds, featuredAppId);
        return section;
    }

    @Transactional
    public HomeSection update(UUID id, String title, HomeSection.Layout layout,
                              List<UUID> listAppIds, UUID featuredAppId) {
        HomeSection section = findById(id);
        if (title != null && !title.isBlank()) section.setTitle(title.trim());
        if (layout != null) section.setLayout(layout);
        homeSectionRepository.save(section);

        saveAppSelections(id, listAppIds, featuredAppId);
        return section;
    }

    @Transactional
    public void saveAppSelections(UUID sectionId, List<UUID> listAppIds, UUID featuredAppId) {
        HomeSection section = findById(sectionId);
        homeSectionAppRepository.deleteAllByHomeSectionId(sectionId);

        List<UUID> ids = listAppIds != null ? listAppIds : new ArrayList<>();
        if (ids.size() > 5) ids = ids.subList(0, 5);

        int pos = 0;
        for (UUID appId : ids) {
            App app = appService.findById(appId);
            homeSectionAppRepository.save(HomeSectionApp.builder()
                    .homeSection(section)
                    .app(app)
                    .slot(HomeSectionApp.Slot.LIST)
                    .position(pos++)
                    .build());
        }

        if (featuredAppId != null) {
            App featured = appService.findById(featuredAppId);
            homeSectionAppRepository.save(HomeSectionApp.builder()
                    .homeSection(section)
                    .app(featured)
                    .slot(HomeSectionApp.Slot.FEATURED)
                    .position(0)
                    .build());
        }
    }

    @Transactional
    public void delete(UUID id) {
        HomeSection section = findById(id);
        section.softDelete();
        homeSectionRepository.save(section);
    }

    /** Persist a new drag-and-drop order for the custom homepage sections. */
    @Transactional
    public void reorder(List<UUID> orderedIds) {
        int pos = 0;
        for (UUID id : orderedIds) {
            HomeSection section = findById(id);
            section.setPosition(pos++);
            homeSectionRepository.save(section);
        }
    }
}