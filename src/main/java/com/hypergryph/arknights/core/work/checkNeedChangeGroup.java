package com.hypergryph.arknights.core.work;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class checkNeedChangeGroup {

    private static final int RESET_HOUR = 4;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private checkNeedChangeGroup() {}

    public static int getMonthKey(long epochSecond) {
        LocalDate d = Instant.ofEpochSecond(epochSecond)
                .atZone(ZONE)
                .minusHours(RESET_HOUR)
                .toLocalDate();
        return d.getYear() * 100 + d.getMonthValue();
    }

    public static boolean needChange(long lastLoginTime, long nowLoginTime) {
        if (lastLoginTime <= 0L) {
            return true;
        }
        return getMonthKey(nowLoginTime) > getMonthKey(lastLoginTime);
    }

    private static final Pattern PREFIX_NUM = Pattern.compile("^(.*?)(\\d+)$");

    public static String nextGroupId(String groupId) {
        if (groupId == null) return null;
        Matcher m = PREFIX_NUM.matcher(groupId);
        if (!m.matches()) {
            return groupId;
        }
        String prefix = m.group(1);
        String numStr = m.group(2);
        int width = numStr.length();
        int num = Integer.parseInt(numStr);
        int next = (num <= 0) ? 1 : num + 1;

        return prefix + String.format("%0" + width + "d", next);
    }
}

