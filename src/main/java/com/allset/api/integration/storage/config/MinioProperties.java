package com.allset.api.integration.storage.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "minio")
public record MinioProperties(

        @NotBlank(message = "MINIO_ENDPOINT é obrigatório")
        String endpoint,

        @NotBlank(message = "MINIO_PUBLIC_ENDPOINT é obrigatório")
        String publicEndpoint,

        @NotBlank(message = "MINIO_ACCESS_KEY é obrigatório")
        String accessKey,

        @NotBlank(message = "MINIO_SECRET_KEY é obrigatório")
        String secretKey,

        @NotBlank
        @DefaultValue("us-east-1")
        String region,

        @NotBlank
        @DefaultValue("allset")
        String bucketPrefix,

        @DefaultValue("true")
        boolean autoCreateBuckets,

        @Min(1)
        @DefaultValue("15")
        int presignedUrlTtlMinutes,

        @Min(1)
        @DefaultValue("24")
        int presignedUrlTtlLongHours,

        @Valid @NotNull
        MaxSizeMb maxSizeMb

) {

    public record MaxSizeMb(
            @Min(1) @DefaultValue("5")   int avatar,
            @Min(1) @DefaultValue("5")   int document,
            @Min(1) @DefaultValue("5")   int orderPhoto,
            @Min(1) @DefaultValue("5")   int chatAttachment,
            @Min(1) @DefaultValue("1")   int catalogIcon,
            @Min(1) @DefaultValue("5")   int disputeEvidence,
            @Min(1) @DefaultValue("100") int dataExport
    ) {}
}
