package kir.taixiu;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class TaiXiuRules {
    private static final DateTimeFormatter HISTORY_TIME =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private TaiXiuRules() {
    }

    public static boolean isTriple(int d1, int d2, int d3) {
        validateDice(d1, d2, d3);
        return d1 == d2 && d2 == d3;
    }

    public static int sum(int d1, int d2, int d3) {
        validateDice(d1, d2, d3);
        return d1 + d2 + d3;
    }

    public static String resultLabel(int sum) {
        if (sum < 3 || sum > 18) {
            throw new IllegalArgumentException("sum must be between 3 and 18");
        }
        return sum >= 11 ? "Tài" : "Xỉu";
    }

    public static boolean wins(TxBetType type, int number, int d1, int d2, int d3) {
        int sum = sum(d1, d2, d3);
        boolean triple = isTriple(d1, d2, d3);
        return switch (type) {
            case TAI_XIU -> number == 1 ? sum >= 11 : sum <= 10;
            case EVEN_ODD -> number == 0 ? sum % 2 == 0 : sum % 2 != 0;
            case TOTAL -> sum == number;
            case TRIPLE_ANY -> triple;
            case TRIPLE_EXACT -> triple && d1 == number;
        };
    }

    public static double parseMoney(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new NumberFormatException("empty amount");
        }
        String s = raw.trim().toLowerCase(Locale.ROOT).replace(",", "");
        double multiplier = 1;
        if (s.endsWith("k")) {
            multiplier = 1_000;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("m")) {
            multiplier = 1_000_000;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("b")) {
            multiplier = 1_000_000_000;
            s = s.substring(0, s.length() - 1);
        }
        double value = Double.parseDouble(s) * multiplier;
        if (!Double.isFinite(value) || value < 0) {
            throw new NumberFormatException("invalid amount");
        }
        return value;
    }

    public static String formatHistoryTimestamp(Instant instant, ZoneId zoneId) {
        return HISTORY_TIME.format(instant.atZone(zoneId));
    }

    private static void validateDice(int d1, int d2, int d3) {
        validateDie(d1);
        validateDie(d2);
        validateDie(d3);
    }

    private static void validateDie(int die) {
        if (die < 1 || die > 6) {
            throw new IllegalArgumentException("die must be between 1 and 6");
        }
    }
}
