package com.allset.api.shared.storage.config;

import com.allset.api.shared.storage.domain.StorageBucket;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MinioBucketInitializer implements ApplicationRunner {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.autoCreateBuckets()) {
            log.info("event=minio_bucket_init skipped reason=auto-create-disabled");
            return;
        }

        for (StorageBucket bucket : StorageBucket.values()) {
            String name = bucket.fullName(properties.bucketPrefix());
            try {
                ensureBucket(name);
                if (bucket.visibility() == StorageBucket.Visibility.PUBLIC) {
                    applyPublicReadPolicy(name);
                }
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Falha ao inicializar bucket " + name + ": " + e.getMessage(), e);
            }
        }
    }

    private void ensureBucket(String name) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(name).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(name)
                    .region(properties.region())
                    .build());
            log.info("event=minio_bucket_created bucket={}", name);
        } else {
            log.info("event=minio_bucket_exists bucket={}", name);
        }
    }

    private void applyPublicReadPolicy(String name) throws Exception {
        String policy = """
                {
                  "Version": "2012-10-17",
                  "Statement": [{
                    "Effect": "Allow",
                    "Principal": "*",
                    "Action": ["s3:GetObject"],
                    "Resource": ["arn:aws:s3:::%s/*"]
                  }]
                }
                """.formatted(name);
        minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                .bucket(name)
                .config(policy)
                .build());
        log.info("event=minio_bucket_policy_applied bucket={} policy=public-read", name);
    }
}
