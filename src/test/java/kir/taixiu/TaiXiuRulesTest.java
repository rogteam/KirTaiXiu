package kir.taixiu;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class TaiXiuRulesTest {
    @Test
    void resultLabelUsesVietnameseReadableText() {
        assertEquals("Xỉu", TaiXiuRules.resultLabel(3));
        assertEquals("Xỉu", TaiXiuRules.resultLabel(10));
        assertEquals("Tài", TaiXiuRules.resultLabel(11));
        assertEquals("Tài", TaiXiuRules.resultLabel(18));
        assertThrows(IllegalArgumentException.class, () -> TaiXiuRules.resultLabel(2));
        assertThrows(IllegalArgumentException.class, () -> TaiXiuRules.resultLabel(19));
    }

    @Test
    void taiXiuWinsAcrossAllDiceOutcomes() {
        for (int d1 = 1; d1 <= 6; d1++) {
            for (int d2 = 1; d2 <= 6; d2++) {
                for (int d3 = 1; d3 <= 6; d3++) {
                    int sum = d1 + d2 + d3;
                    assertEquals(sum >= 11, TaiXiuRules.wins(TxBetType.TAI_XIU, 1, d1, d2, d3));
                    assertEquals(sum <= 10, TaiXiuRules.wins(TxBetType.TAI_XIU, 0, d1, d2, d3));
                }
            }
        }
    }

    @Test
    void evenOddWinsAcrossAllDiceOutcomes() {
        for (int d1 = 1; d1 <= 6; d1++) {
            for (int d2 = 1; d2 <= 6; d2++) {
                for (int d3 = 1; d3 <= 6; d3++) {
                    int sum = d1 + d2 + d3;
                    assertEquals(sum % 2 == 0, TaiXiuRules.wins(TxBetType.EVEN_ODD, 0, d1, d2, d3));
                    assertEquals(sum % 2 != 0, TaiXiuRules.wins(TxBetType.EVEN_ODD, 1, d1, d2, d3));
                }
            }
        }
    }

    @Test
    void totalBetOnlyWinsExactTotal() {
        assertTrue(TaiXiuRules.wins(TxBetType.TOTAL, 12, 3, 4, 5));
        assertFalse(TaiXiuRules.wins(TxBetType.TOTAL, 13, 3, 4, 5));
        assertTrue(TaiXiuRules.wins(TxBetType.TOTAL, 3, 1, 1, 1));
        assertTrue(TaiXiuRules.wins(TxBetType.TOTAL, 18, 6, 6, 6));
    }

    @Test
    void tripleBetsAreStrict() {
        assertTrue(TaiXiuRules.wins(TxBetType.TRIPLE_ANY, 0, 4, 4, 4));
        assertFalse(TaiXiuRules.wins(TxBetType.TRIPLE_ANY, 0, 4, 4, 5));
        assertTrue(TaiXiuRules.wins(TxBetType.TRIPLE_EXACT, 4, 4, 4, 4));
        assertFalse(TaiXiuRules.wins(TxBetType.TRIPLE_EXACT, 5, 4, 4, 4));
        assertFalse(TaiXiuRules.wins(TxBetType.TRIPLE_EXACT, 4, 4, 4, 5));
    }

    @Test
    void parseMoneySupportsHumanSuffixes() {
        assertEquals(1_000, TaiXiuRules.parseMoney("1k"));
        assertEquals(125_000, TaiXiuRules.parseMoney("125k"));
        assertEquals(1_500_000, TaiXiuRules.parseMoney("1.5m"));
        assertEquals(2_000_000_000, TaiXiuRules.parseMoney("2b"));
        assertEquals(1234567, TaiXiuRules.parseMoney("1,234,567"));
        assertThrows(NumberFormatException.class, () -> TaiXiuRules.parseMoney(""));
        assertThrows(NumberFormatException.class, () -> TaiXiuRules.parseMoney("-1"));
    }

    @Test
    void historyTimestampIsReadableAndLocalizable() {
        Instant instant = Instant.parse("2026-08-02T09:29:54Z");
        assertEquals("02/08/2026 16:29:54", TaiXiuRules.formatHistoryTimestamp(instant, ZoneId.of("Asia/Saigon")));
        assertEquals("02/08/2026 09:29:54", TaiXiuRules.formatHistoryTimestamp(instant, ZoneId.of("UTC")));
    }

    @Test
    void invalidDiceAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> TaiXiuRules.sum(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> TaiXiuRules.sum(1, 7, 1));
        assertThrows(IllegalArgumentException.class, () -> TaiXiuRules.isTriple(1, 1, 9));
    }
}
