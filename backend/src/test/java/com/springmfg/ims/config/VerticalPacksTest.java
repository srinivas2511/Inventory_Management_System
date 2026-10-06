package com.springmfg.ims.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VerticalPacksTest {

    @Test
    @DisplayName("The configured list is trimmed, lower-cased and de-duplicated; blank means none")
    void parse() {
        assertThat(VerticalPacks.parse(" Automotive, furniture,,AUTOMOTIVE ")).containsExactly("automotive", "furniture");
        assertThat(VerticalPacks.parse("")).isEmpty();
        assertThat(VerticalPacks.parse(null)).isEmpty();
    }

    @Test
    @DisplayName("The three shipped verticals resolve to classpath locations")
    void shippedVerticals() {
        assertThat(VerticalPacks.locations(List.of("automotive", "furniture", "electronics")))
                .containsExactly("classpath:db/vertical/automotive", "classpath:db/vertical/furniture",
                        "classpath:db/vertical/electronics");
    }

    @Test
    @DisplayName("An unknown vertical fails fast with a clear message instead of being ignored")
    void unknownVertical() {
        assertThatThrownBy(() -> VerticalPacks.locations(List.of("aerospace")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown vertical 'aerospace'");
    }

    @Test
    @DisplayName("A name that could escape the vertical folder is refused")
    void invalidNames() {
        for (String bad : List.of("../db", "auto/motive", "Automotive", "1auto", "a b", "")) {
            assertThatThrownBy(() -> VerticalPacks.locations(List.of(bad)))
                    .as("name '%s'", bad)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Invalid vertical name");
        }
    }

    @Test
    @DisplayName("The customizer adds only the enabled verticals to Flyway's locations and tolerates a removed one")
    void customizerLocations() {
        FluentConfiguration none = Flyway.configure().locations("classpath:db/migration");
        new VerticalFlywayConfig().verticalPackLocations("").customize(none);
        assertThat(descriptors(none)).containsExactly("classpath:db/migration");

        FluentConfiguration some = Flyway.configure().locations("classpath:db/migration");
        new VerticalFlywayConfig().verticalPackLocations("furniture,electronics").customize(some);
        assertThat(descriptors(some)).containsExactlyInAnyOrder("classpath:db/migration",
                "classpath:db/vertical/furniture", "classpath:db/vertical/electronics");
    }

    private static List<String> descriptors(FluentConfiguration c) {
        return java.util.Arrays.stream(c.getLocations()).map(Location::getDescriptor).toList();
    }
}
