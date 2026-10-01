package com.tgaws.access.tcp.codec;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MODBUS CRC16 单测（标准测试向量 "123456789" → 0x4B37）。
 */
class Crc16ModbusTest {

    @Test
    void knownVector() {
        assertEquals(0x4B37, Crc16Modbus.compute("123456789".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void verifyLowByteFirst() {
        byte[] payload = "123456789".getBytes(StandardCharsets.US_ASCII);
        assertTrue(Crc16Modbus.verify(payload, (byte) 0x37, (byte) 0x4B), "低字节先发");
        assertFalse(Crc16Modbus.verify(payload, (byte) 0x4B, (byte) 0x37), "字节序颠倒应失败");
        assertFalse(Crc16Modbus.verify(payload, (byte) 0x00, (byte) 0x00));
    }
}
