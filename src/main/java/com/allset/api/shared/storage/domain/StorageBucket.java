package com.allset.api.shared.storage.domain;

import com.allset.api.shared.storage.config.MinioProperties;

import java.util.Set;

public enum StorageBucket {

    AVATARS("avatars",                   Visibility.PRIVATE, Set.of("image/jpeg", "image/png")),
    DOCUMENTS("documents",               Visibility.PRIVATE, Set.of("image/jpeg", "image/png", "application/pdf")),
    ORDER_PHOTOS("order-photos",         Visibility.PRIVATE, Set.of("image/jpeg", "image/png")),
    CHAT_ATTACHMENTS("chat-attachments", Visibility.PRIVATE, Set.of("image/jpeg", "image/png")),
    CATALOG_ICONS("catalog-icons",       Visibility.PUBLIC,  Set.of("image/png", "image/svg+xml")),
    DISPUTE_EVIDENCES("dispute-evidences", Visibility.PRIVATE, Set.of("image/jpeg", "image/png")),
    DATA_EXPORTS("data-exports",         Visibility.PRIVATE, Set.of("application/zip"));

    private final String suffix;
    private final Visibility visibility;
    private final Set<String> allowedMimeTypes;

    StorageBucket(String suffix, Visibility visibility, Set<String> allowedMimeTypes) {
        this.suffix = suffix;
        this.visibility = visibility;
        this.allowedMimeTypes = allowedMimeTypes;
    }

    public String fullName(String prefix) {
        return prefix + "-" + suffix;
    }

    public Visibility visibility() {
        return visibility;
    }

    public Set<String> allowedMimeTypes() {
        return allowedMimeTypes;
    }

    public long maxSizeBytes(MinioProperties.MaxSizeMb limits) {
        long mb = switch (this) {
            case AVATARS           -> limits.avatar();
            case DOCUMENTS         -> limits.document();
            case ORDER_PHOTOS      -> limits.orderPhoto();
            case CHAT_ATTACHMENTS  -> limits.chatAttachment();
            case CATALOG_ICONS     -> limits.catalogIcon();
            case DISPUTE_EVIDENCES -> limits.disputeEvidence();
            case DATA_EXPORTS      -> limits.dataExport();
        };
        return mb * 1_048_576L;
    }

    public enum Visibility { PUBLIC, PRIVATE }
}
