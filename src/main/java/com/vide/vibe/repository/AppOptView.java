package com.vide.vibe.repository;

import java.util.UUID;

/** Minimal projection of an App: just what the wish dropdown needs. */
public interface AppOptView {
    UUID getId();
    String getName();
    String getIconUrl();
}