package com.springmfg.ims.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.flywaydb.core.api.Location;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Adds the enabled industry verticals' SQL folders to Flyway (see {@link VerticalPacks}). */
@Configuration
public class VerticalFlywayConfig {

    @Bean
    public FlywayConfigurationCustomizer verticalPackLocations(@Value("${ims.verticals:}") String configured) {
        List<String> verticals = VerticalPacks.locations(VerticalPacks.parse(configured));
        return configuration -> {
            if (!verticals.isEmpty()) {
                List<String> locations = new ArrayList<>(
                        Arrays.stream(configuration.getLocations()).map(Location::getDescriptor).toList());
                locations.addAll(verticals);
                configuration.locations(locations.toArray(String[]::new));
            }
            // A vertical that is later switched off must not stop the application starting: its applied repeatable
            // seeds are simply no longer resolved, and its data stays, as with any seed. "*:future" keeps Flyway's
            // default tolerance of newer migrations, which setting any pattern would otherwise drop.
            configuration.ignoreMigrationPatterns("*:future", "repeatable:missing");
        };
    }
}
