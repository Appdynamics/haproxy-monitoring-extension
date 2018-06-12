/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 *  * All Rights Reserved.
 *  * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 *  * The copyright notice above does not evidence any actual or intended publication of such source code.
 */


/*
 */

package com.appdynamics.extensions.haproxy;

public class Constant{
    public static String DISPLAY_NAME;
    public static String HOST;
    public static String PORT;
    public static String CSV_EXPORT_URI;
    public static String USER_NAME;
    public static String PASSWORD;
    public static String PROXY_NAMES;
    public static String METRIC_SEPARATOR;
    public static int PROXY_INDEX;
    public static int PROXY_TYPE_INDEX;
    public static String METRIC_PREFIX;
    public static String USE_SSL;
    static{
        DISPLAY_NAME = "displayName";
        HOST = "host";
        PORT = "port";
        CSV_EXPORT_URI = "csvExportUri";
        USER_NAME = "username";
        PASSWORD = "password";
        PROXY_NAMES = "proxynames";
        METRIC_SEPARATOR = "|";
        PROXY_INDEX = 0;
        PROXY_TYPE_INDEX = 1;
        METRIC_PREFIX = "Custom Metrics|HAProxy";
        USE_SSL = "useSsl";
    }
}