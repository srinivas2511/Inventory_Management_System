package com.springmfg.ims.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Industry vertical packs (R20). A pack is a folder of repeatable SQL seeds under {@code db/vertical/<name>}; it is
 * applied only when its name appears in {@code ims.verticals} (env {@code IMS_VERTICALS}, comma separated). A default
 * deployment therefore never receives another industry's operations or rejection reasons, and adding a new vertical
 * is a new folder of SQL with no Java change.
 */
public final class VerticalPacks {

    public static final String ROOT = "db/vertical/";

    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,29}");

    private VerticalPacks() {
    }

    /** Parses the configured list: trims, lower-cases, drops blanks and duplicates. */
    public static List<String> parse(String configured) {
        List<String> names = new ArrayList<>();
        if (configured == null) {
            return names;
        }
        for (String raw : configured.split(",")) {
            String name = raw.trim().toLowerCase();
            if (!name.isEmpty() && !names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    /** Flyway location descriptors for the named verticals; fails fast on a bad or unknown name. */
    public static List<String> locations(List<String> names) {
        List<String> locations = new ArrayList<>();
        for (String name : names) {
            if (!NAME.matcher(name).matches()) {
                throw new IllegalStateException("Invalid vertical name '" + name
                        + "': use lower-case letters, digits and underscores (ims.verticals / IMS_VERTICALS)");
            }
            if (!exists(name)) {
                throw new IllegalStateException("Unknown vertical '" + name + "': no SQL found under classpath:"
                        + ROOT + name + "/ (ims.verticals / IMS_VERTICALS)");
            }
            locations.add("classpath:" + ROOT + name);
        }
        return locations;
    }

    private static boolean exists(String name) {
        try {
            return new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:" + ROOT + name + "/*.sql").length > 0;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot inspect vertical pack " + name, e);
        }
    }
}
