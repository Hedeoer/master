package com.zeta.firewall.util;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * IP地址工具类
 */
public class IpUtils {

    /**
     * 判断字符串是否为有效的IP地址（IPv4或IPv6）
     * 
     * @param ip IP地址字符串
     * @return 是否为有效IP地址
     */
    public static boolean isValidIpAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        
        // 处理CIDR格式
        String ipAddress = ip;
        if (ip.contains("/")) {
            String[] parts = ip.split("/");
            if (parts.length != 2) {
                return false;
            }
            
            try {
                int prefixLength = Integer.parseInt(parts[1]);
                // IPv4前缀长度范围：0-32，IPv6前缀长度范围：0-128
                if (prefixLength < 0 || prefixLength > 128) {
                    return false;
                }
                
                // 如果前缀长度超过32且不是IPv6地址，则无效
                if (prefixLength > 32 && !isIpv6Address(parts[0])) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
            
            ipAddress = parts[0];
        }
        
        try {
            InetAddress.getByName(ipAddress);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * 判断字符串是否为有效的IPv4地址
     * 
     * @param ip IP地址字符串
     * @return 是否为有效IPv4地址
     */
    public static boolean isIpv4Address(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        
        // 处理CIDR格式
        String ipAddress = ip;
        if (ip.contains("/")) {
            String[] parts = ip.split("/");
            if (parts.length != 2) {
                return false;
            }
            
            try {
                int prefixLength = Integer.parseInt(parts[1]);
                // IPv4前缀长度范围：0-32
                if (prefixLength < 0 || prefixLength > 32) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
            
            ipAddress = parts[0];
        }
        
        try {
            InetAddress inetAddress = InetAddress.getByName(ipAddress);
            return inetAddress instanceof Inet4Address;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * 判断字符串是否为有效的IPv6地址
     * 
     * @param ip IP地址字符串
     * @return 是否为有效IPv6地址
     */
    public static boolean isIpv6Address(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        
        // 处理CIDR格式
        String ipAddress = ip;
        if (ip.contains("/")) {
            String[] parts = ip.split("/");
            if (parts.length != 2) {
                return false;
            }
            
            try {
                int prefixLength = Integer.parseInt(parts[1]);
                // IPv6前缀长度范围：0-128
                if (prefixLength < 0 || prefixLength > 128) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
            
            ipAddress = parts[0];
        }
        
        try {
            InetAddress inetAddress = InetAddress.getByName(ipAddress);
            return inetAddress instanceof Inet6Address;
        } catch (UnknownHostException e) {
            return false;
        }
    }
    
    /**
     * 判断CIDR格式的IP地址是否有效
     * 
     * @param cidr CIDR格式的IP地址
     * @return 是否有效
     */
    public static boolean isValidCidr(String cidr) {
        if (cidr == null || cidr.isEmpty() || !cidr.contains("/")) {
            return false;
        }
        
        String[] parts = cidr.split("/");
        if (parts.length != 2) {
            return false;
        }
        
        String ipAddress = parts[0];
        
        try {
            int prefixLength = Integer.parseInt(parts[1]);
            InetAddress inetAddress = InetAddress.getByName(ipAddress);
            
            if (inetAddress instanceof Inet4Address) {
                // IPv4前缀长度范围：0-32
                return prefixLength >= 0 && prefixLength <= 32;
            } else if (inetAddress instanceof Inet6Address) {
                // IPv6前缀长度范围：0-128
                return prefixLength >= 0 && prefixLength <= 128;
            }
            
            return false;
        } catch (NumberFormatException | UnknownHostException e) {
            return false;
        }
    }


    /**
     * 根据传入的ip和offerIpType判断是否匹配
     *
     * @param ip ip
     * @param offerIpType offerIpType
     * @return 是否匹配
     */
    public static boolean isMappingIpType(String ip, String offerIpType){

        boolean result = true;

        if (offerIpType == null || offerIpType.isEmpty() || ip == null || ip.isEmpty()) {
            return false;
        }

        String[] ipArr = ip.split(",");
        for (String singleIp : ipArr) {

            if (!isValidIpAddress(singleIp)) {
                return false;
            }

            if (offerIpType.equals("ipv4")) {
                result=  isIpv4Address(singleIp);
            } else if (offerIpType.equals("ipv6")) {
                result =  isIpv6Address(singleIp);
            } else {
                return false;
            }

            if (!result) {
                break;
            }
        }

        return result;
    }
}