package dev.julioperez.nls.products.infrastructure.aws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

@Configuration(proxyBeanMethods = false)
public class AwsSecretsManagerConfiguration {
    @Bean(destroyMethod = "close")
    SecretsManagerClient secretsManagerClient(@Value("${nls.aws.region:us-east-1}") String region) {
        // AWS SDK resolves the developer's local identity or the runtime role through its default chain.
        return SecretsManagerClient.builder()
                .region(Region.of(region))
                .build();
    }
}
