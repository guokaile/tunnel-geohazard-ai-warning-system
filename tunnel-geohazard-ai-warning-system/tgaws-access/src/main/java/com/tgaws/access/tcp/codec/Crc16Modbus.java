package com.tgaws.access.tcp.codec;

/**
 * MODBUS CRC16（《4.接口设计说明书》4.4.1：覆盖 TYPE 至 ITEMS 末，低字节先发）。
 */
public final class Crc16Modbus {

    private Crc16Modbus() {
    }

    /** 计算 CRC16-MODBUS（多项式 0xA001，初值 0xFFFF） */
    public static int compute(byte[] data) {
        int crc = 0xFFFF;
        for (byte b : data) {
            crc ^= (b & 0xFF);
            for (int i = 0; i < 8; i++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ 0xA001;
                } else {
                    crc >>>= 1;
                }
            }
        }
        return crc & 0xFFFF;
    }

    /** 校验：帧内 CRC 字段为低字节在前 */
    public static boolean verify(byte[] payload, byte crcLow, byte crcHigh) {
        int expected = compute(payload);
        return (expected & 0xFF) == (crcLow & 0xFF)
                && ((expected >>> 8) & 0xFF) == (crcHigh & 0xFF);
    }
}
