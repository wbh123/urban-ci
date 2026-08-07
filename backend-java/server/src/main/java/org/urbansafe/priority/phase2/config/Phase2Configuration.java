package org.urbansafe.priority.phase2.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.urbansafe.priority.asset.config.StorageProperties;
import org.urbansafe.priority.map.config.AmapProperties;
import org.urbansafe.priority.map.config.MapProperties;

@Configuration
@EnableConfigurationProperties({MapProperties.class, AmapProperties.class, StorageProperties.class})
public class Phase2Configuration {
}
