package com.example.travelplatform.common.ip;
/** Global disclosure gate. Keep historical snapshots; enable only after licensed GeoIP acceptance. */
public final class IpRegionVisibility {
    public static final boolean ENABLED=false;
    private IpRegionVisibility() { }
    public static String publicValue(String value){return ENABLED?value:null;}
}
