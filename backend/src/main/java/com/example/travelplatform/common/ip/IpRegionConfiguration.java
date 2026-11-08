package com.example.travelplatform.common.ip;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IpRegionConfiguration {
    @Bean
    @ConditionalOnMissingBean(IpRegionResolver.class)
    IpRegionResolver unknownIpRegionResolver() {
        // No licensed GeoIP source has been supplied. Do not query third parties or guess geography.
        return address -> null;
    }
}
