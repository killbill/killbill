package org.killbill.billing.jaxrs;

public final class KillbillVersion {

    private static final String API_VERSION = "${killbill-api.version}";
    private static final String PLUGIN_API_VERSION = "${killbill-plugin-api.version}";
    private static final String COMMON_VERSION = "${killbill-commons.version}";
    private static final String PLATFORM_VERSION = "${killbill-platform.version}";

    public static String getApiVersion() {
        return API_VERSION;
    }

    public static String getPluginApiVersion() {
        return PLUGIN_API_VERSION;
    }

    public static String getCommonVersion() {
        return COMMON_VERSION;
    }

    public static String getPlatformVersion() {
        return PLATFORM_VERSION;
    }
}