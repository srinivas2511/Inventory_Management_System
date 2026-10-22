package com.mfg.ims.prodcalendar;

import java.sql.Time;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.settings.SystemSettingService;

/**
 * When machines are planned to run: the weekly offs and time zone the business already configures for HR, and the
 * shifts named by {@code mfg.calendar.shift_codes}. Shifts are read with SQL so that production planning stays
 * independent of the HR module's classes.
 */
@Component
public class ProductionCalendar {

    /** A shift as a time of day; an end at or before the start means it runs past midnight. */
    public record ShiftWindow(LocalTime start, LocalTime end) {
        public long minutes() {
            long m = Duration.between(start, end).toMinutes();
            return m <= 0 ? m + 24 * 60 : m;
        }
    }

    /** A concrete half-open interval [start, end). */
    public record Window(Instant start, Instant end) {
        public long minutes() {
            return Duration.between(start, end).toMinutes();
        }
    }

    private final SystemSettingService settings;
    private final NamedParameterJdbcTemplate jdbc;

    public ProductionCalendar(SystemSettingService settings, NamedParameterJdbcTemplate jdbc) {
        this.settings = settings;
        this.jdbc = jdbc;
    }

    public ZoneId zone() {
        String id = settings.get("hrm.timezone", "Asia/Kolkata").trim();
        try {
            return ZoneId.of(id);
        } catch (Exception e) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Setting hrm.timezone is not a valid zone: " + id);
        }
    }

    public Set<DayOfWeek> weeklyOff() {
        Set<DayOfWeek> off = EnumSet.noneOf(DayOfWeek.class);
        for (String token : settings.get("hrm.weekly_off_days", "SUNDAY").split(",")) {
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

    public boolean isWorkingDay(LocalDate date) {
        return !weeklyOff().contains(date.getDayOfWeek());
    }

    /** The configured shifts, earliest first. At least one must exist, or nothing can be planned. */
    public List<ShiftWindow> shifts() {
        String raw = settings.get("mfg.calendar.shift_codes", "GENERAL");
        List<String> codes = Arrays.stream(raw.split(",")).map(String::trim).filter(c -> !c.isEmpty()).toList();
        List<ShiftWindow> shifts = codes.isEmpty() ? List.of() : jdbc.query(
                "SELECT start_time, end_time FROM ims.shifts WHERE active AND shift_code IN (:codes) ORDER BY start_time",
                new MapSqlParameterSource("codes", codes),
                (rs, i) -> new ShiftWindow(toLocalTime(rs.getTime("start_time")), toLocalTime(rs.getTime("end_time"))));
        if (shifts.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "No active shift matches setting mfg.calendar.shift_codes=" + raw);
        }
        return shifts;
    }

    public long shiftMinutesPerDay() {
        return shifts().stream().mapToLong(ShiftWindow::minutes).sum();
    }

    /** The intervals the machines may run on a date, in the business time zone; empty on a weekly off. */
    public List<Window> windowsOn(LocalDate date) {
        return isWorkingDay(date) ? shiftWindowsOn(date) : List.of();
    }

    /** The configured shifts laid out on a date whether or not it is a weekly off (for overtime on an off day). */
    public List<Window> shiftWindowsOn(LocalDate date) {
        ZoneId zone = zone();
        List<Window> windows = new ArrayList<>();
        for (ShiftWindow s : shifts()) {
            ZonedDateTime start = date.atTime(s.start()).atZone(zone);
            windows.add(new Window(start.toInstant(), start.plusMinutes(s.minutes()).toInstant()));
        }
        return windows;
    }

    private static LocalTime toLocalTime(Time time) {
        return time.toLocalTime();
    }
}
