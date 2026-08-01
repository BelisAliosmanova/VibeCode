package com.vide.vibe.service;

import com.vide.vibe.model.App;
import com.vide.vibe.model.AppMedia;
import com.vide.vibe.model.RankingConfig;
import com.vide.vibe.repository.RankingConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RankingService {

    private final RankingConfigRepository rankingConfigRepository;

    @Transactional
    public RankingConfig getConfig() {
        return rankingConfigRepository.findById(RankingConfig.SINGLETON_ID)
                .orElseGet(() -> rankingConfigRepository.save(RankingConfig.defaults()));
    }

    @Transactional
    public RankingConfig updateConfig(RankingConfig updated) {
        RankingConfig existing = getConfig();
        existing.copyEditableFieldsFrom(updated);
        return rankingConfigRepository.save(existing);
    }

    public int computeScore(App app) {
        return computeScore(app, getConfig());
    }

    public int computeScore(App app, RankingConfig cfg) {
        int score = 0;

        if (app.getDescription() != null && app.getDescription().length() > cfg.getMinDescriptionLength()) {
            score += cfg.getDescriptionLengthPoints();
        }

        if (app.getIconUrl() != null && !app.getIconUrl().isBlank()) {
            score += cfg.getFavIconPoints();
        }

        long videoCount = app.getMedia().stream()
                .filter(m -> m.getType() == AppMedia.MediaType.VIDEO).count();
        if (videoCount > 0) {
            score += cfg.getVideoPoints();
        }

        long workflowCount = app.getWorkflows().size();
        if (workflowCount > 0) {
            score += cfg.getWorkflowPoints();
            score += (int) (cfg.getAdditionalWorkflowPoints() * (workflowCount - 1));
        }

        long imageCount = app.getMedia().stream()
                .filter(m -> m.getType() == AppMedia.MediaType.SCREENSHOT).count();
        if (imageCount > 0) {
            score += cfg.getImagePoints();
            score += (int) (cfg.getAdditionalImagePoints() * (imageCount - 1));
        }

        if (app.getGithubUrl() != null && !app.getGithubUrl().isBlank()) {
            score += cfg.getGithubListingPoints();
        }

        if (app.getCategorySelections() != null && app.getCategorySelections().size() >= cfg.getMinTagsForBonus()) {
            score += cfg.getTagsPoints();
        }

        if (app.isClaimed()) {
            score += cfg.getClaimedPoints();
        }

        if (app.getVerifiedScore() != null) {
            score += cfg.getVerifiedPoints();
        }

        if (app.getUserRatingAvg() != null && app.getUserRatingAvg() > cfg.getMinRatingForBonus()
                && app.getUserRatingCount() != null && app.getUserRatingCount() >= cfg.getMinRatersForBonus()) {
            score += cfg.getHighRatingPoints();
        }

        return score;
    }
}