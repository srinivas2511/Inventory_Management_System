package com.springmfg.ims.admin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import com.springmfg.ims.iam.UserRepository;

/**
 * Demo users, one per role (DESIGN.md section 11.2), for development and demonstrations only. Enabled by
 * {@code ims.demo.load-data=true} (the dev profile sets it) and refused outright under the prod profile.
 * Created through {@link UserAdminService}, so passwords obey the policy and every user must change theirs at
 * first sign-in. Users that already exist are left alone.
 */
@Component
@Order(20)
@ConditionalOnProperty(name = "ims.demo.load-data", havingValue = "true")
class DemoUserLoader implements ApplicationRunner {

    static final String PASSWORD = "Demo@123456!";
    private static final Logger log = LoggerFactory.getLogger(DemoUserLoader.class);

    /** username -> role, full name. Employee code S001 belongs to the supervisor (DESIGN 11.2). */
    private static final Map<String, String[]> DEMO = new LinkedHashMap<>();
    static {
        DEMO.put("admin", new String[] { "ADMIN", "Demo Admin" });
        DEMO.put("engineer1", new String[] { "ENGINEER", "Demo Engineer" });
        DEMO.put("prodmgr1", new String[] { "PRODUCTION_MANAGER", "Demo Production Manager" });
        DEMO.put("supervisor1", new String[] { "SUPERVISOR", "Demo Supervisor" });
        DEMO.put("operator1", new String[] { "OPERATOR", "Ravi Kumar" });
        DEMO.put("operator2", new String[] { "OPERATOR", "Demo Operator Two" });
        DEMO.put("quality1", new String[] { "QUALITY_MANAGER", "Demo Quality Manager" });
        DEMO.put("storemgr1", new String[] { "STORE_MANAGER", "Demo Store Manager" });
        DEMO.put("storeop1", new String[] { "STORE_OPERATOR", "Demo Store Operator" });
        DEMO.put("purchase1", new String[] { "PURCHASE_MANAGER", "Demo Purchase Manager" });
        DEMO.put("sales1", new String[] { "SALES", "Demo Sales" });
        DEMO.put("dispatch1", new String[] { "DISPATCH", "Demo Dispatch" });
        DEMO.put("maint1", new String[] { "MAINTENANCE", "Demo Maintenance Engineer" });
        DEMO.put("mgmt1", new String[] { "MANAGEMENT", "Demo Management" });
    }

    private final UserRepository users;
    private final UserAdminService userAdmin;

    DemoUserLoader(UserRepository users, UserAdminService userAdmin, Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("ims.demo.load-data must never be enabled under the prod profile");
        }
        this.users = users;
        this.userAdmin = userAdmin;
    }

    @Override
    public void run(ApplicationArguments args) {
        int created = 0;
        for (Map.Entry<String, String[]> entry : DEMO.entrySet()) {
            String username = entry.getKey();
            if (users.existsByUsernameIgnoreCase(username)) {
                continue;
            }
            userAdmin.create(new UserDtos.CreateUserRequest(username, "supervisor1".equals(username) ? "S001" : null,
                    entry.getValue()[1], username + "@demo.ims.local", null, Set.of(entry.getValue()[0]), PASSWORD));
            created++;
        }
        log.warn("Demo users loaded ({} new). Password for all: see DESIGN.md 11.2. Never enable this in production.", created);
    }
}
