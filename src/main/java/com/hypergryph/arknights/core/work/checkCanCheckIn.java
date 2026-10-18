package com.hypergryph.arknights.core.work;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public class checkCanCheckIn {
    private static final int RESET_HOUR = 4;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private checkCanCheckIn() {}

    public static long checkInDayIndex(long epochSecond) {
        return Instant.ofEpochSecond(epochSecond)
                .atZone(ZONE)
                .minusHours(RESET_HOUR)
                .toLocalDate()
                .toEpochDay();
    }

    public static boolean canCheckIn(Long lastCheckInTime, long now) {
        if (lastCheckInTime == null || lastCheckInTime <= 0L) {
            return true;
        }
        return checkInDayIndex(now) > checkInDayIndex(lastCheckInTime);
    }

    public static long nextResetAt(long now) {
        LocalDate checkInDay = Instant.ofEpochSecond(now)
                .atZone(ZONE)
                .minusHours(RESET_HOUR)
                .toLocalDate();
        return checkInDay.plusDays(1)
                .atTime(RESET_HOUR, 0)
                .atZone(ZONE)
                .toEpochSecond();
    }

    public static long remainSeconds(long now) {
        return Math.max(0L, nextResetAt(now) - now);
    }

    public static long markCheckedIn(long now) {
        return now;
    }
}
