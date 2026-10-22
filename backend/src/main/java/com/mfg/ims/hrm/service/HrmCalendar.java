package com.mfg.ims.hrm.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.settings.SystemSettingService;

/** Working-day arithmetic driven by the hrm.weekly_off_days and hrm.timezone settings. */
@Component
public class HrmCalendar {

    private final SystemSettingService settings;

    public HrmCalendar(SystemSettingService settings) {
        this.settings = settings;
    }

    public Set<DayOfWeek> weeklyOff() {
        Set<DayOfWeek> off = EnumSet.noneOf(DayOfWeek.class);
        String raw = settings.get("hrm.weekly_off_days", "SUNDAY");
        for (String token : raw.split(",")) {
            String name = token.trim();
            if (name.isEmpty()) {
                continue;
            }
            try {
                off.add(DayOfWeek.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "Setting hrm.weekly_off_days contains an unknown day: " + name);
            }
        }
        return off;
    }

    public ZoneId zone() {
        String id = settings.get("hrm.timezone", "Asia/Kolkata");
        try {
            return ZoneId.of(id.trim());
        } catch (Exception e) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Setting hrm.timezone is not a valid zone: " + id);
        }
    }

    public LocalDate toLocalDate(Instant instant) {
        return instant.atZone(zone()).toLocalDate();
    }

    public static boolean isWorkingDay(LocalDate date, Set<DayOfWeek> weeklyOff) {
        return !weeklyOff.contains(date.getDayOfWeek());
    }

    /** Working days in [from, to] after weekly offs; the HOLIDAY attendance marker is applied per employee. */
    public int countWorkingDays(LocalDate from, LocalDate to) {
        Set<DayOfWeek> off = weeklyOff();
        int n = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (isWorkingDay(d, off)) {
                n++;
            }
        }
        return n;
    }
}
