package com.vide.vibe.repository;

import java.util.UUID;

/** Light projection for the "An app like…" dropdown: no description, no owner, nothing heavy. */
public interface AppOptView {
    UUID getId();
    String getName();
    String getIconUrl();
}