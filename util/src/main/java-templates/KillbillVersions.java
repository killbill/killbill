package org.killbill.billing.util.nodes;

public final class KillbillVersions {

    private static final String KB_VERSION = "${killbill.version}";
    private static final String API_VERSION = "${killbill-api.version}";
    private static final String PLUGIN_API_VERSION = "${killbill-plugin-api.version}";
    private static final String COMMON_VERSION = "${killbill-commons.version}";
    private static final String PLATFORM_VERSION = "${killbill-platform.version}";

    public static String getKillbillVersion() {
        return KB_VERSION;
    }

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

