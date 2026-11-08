package com.example.travelplatform.common.ip;

import java.net.InetAddress;

/** An audited offline provider may return province (CN) or country (overseas), never a city/IP. */
@FunctionalInterface
public interface IpRegionResolver {
    String resolve(InetAddress address);
}
