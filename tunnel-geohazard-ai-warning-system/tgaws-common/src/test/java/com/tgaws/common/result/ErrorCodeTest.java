package com.tgaws.common.result;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-301 验收：错误码全集与《4》4.6 总表逐条对应（50 项）。
 */
class ErrorCodeTest {

    private static final Pattern CODE_PATTERN = Pattern.compile("^(00000|[ABCD]\\d{4})$");

    @Test
    void totalCountIs68() {
        assertEquals(68, ErrorCode.values().length,
                "错误码总数必须与《4》4.6 总表一致（68 项，含 D0009、T-606 B0504~B0508、T-607 B0802、T-708 B0204、T-811 B0114~B0116、T-813 A0013~A0019）");
    }

    @Test
    void codesUnique() {
        Set<String> codes = new HashSet<>();
        for (ErrorCode ec : ErrorCode.values()) {
            assertTrue(codes.add(ec.getCode()), "错误码重复: " + ec.getCode());
        }
    }

    @Test
    void codeFormatValid() {
        for (ErrorCode ec : ErrorCode.values()) {
            assertTrue(CODE_PATTERN.matcher(ec.getCode()).matches(),
                    "错误码格式非法: " + ec.getCode());
        }
    }

    @Test
    void byCodeRoundTrip() {
        for (ErrorCode ec : ErrorCode.values()) {
            assertEquals(ec, ErrorCode.byCode(ec.getCode()));
        }
        assertNotNull(ErrorCode.byCode("B0302"));
    }

    @Test
    void resultWrapsErrorCode() {
        Result<Void> r = Result.fail(ErrorCode.B0302);
        assertEquals("B0302", r.getCode());
        assertEquals(ErrorCode.B0302.getMessage(), r.getMessage());
    }
}
