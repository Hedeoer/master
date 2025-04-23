package com.zeta.firewall.util;

import java.util.UUID;

public class UUIDUtil {
    /**
     * 生成一个标准的 UUID（带有横杠）
     */
    public static String generateUuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * 生成一个去除横杠的 UUID
     */
    public static String generateSimpleUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
