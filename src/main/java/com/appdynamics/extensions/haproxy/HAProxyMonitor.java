/**
 * Copyright 2013 AppDynamics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.appdynamics.extensions.haproxy;

import com.appdynamics.TaskInputArgs;
import com.appdynamics.extensions.ArgumentsValidator;
import com.appdynamics.extensions.crypto.CryptoUtil;
import com.appdynamics.extensions.http.SimpleHttpClient;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import com.singularity.ee.agent.systemagent.api.MetricWriter;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.TaskOutput;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import jxl.Sheet;
import jxl.Workbook;
import jxl.write.Label;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

public class HAProxyMonitor extends AManagedMonitor {

    private static final String CSV_EXPORT_URI = "csv-export-uri";
    private static final String METRIC_SEPARATOR = "|";
    private static Logger logger = Logger.getLogger(HAProxyMonitor.class);
    private static String metricPrefix;
    private WritableWorkbook workbook;
    private Map<String, Integer> dictionary;
    private Map<String, String> colNameWithDesc;

    private static final Map<String, String> DEFAULT_ARGS = new HashMap<String, String>() {
        {
            put(TaskInputArgs.METRIC_PREFIX, "Custom Metrics|HAProxy");
        }
    };

    public HAProxyMonitor() {
        String msg = "Using Monitor Version [" + getImplementationVersion() + "]";
        logger.info(msg);
        System.out.println(msg);

        populateDictionary();
        populateColNames();

    }

    private void populateColNames() {
        colNameWithDesc = new HashMap<String, String>();

        colNameWithDesc.put("qcur", "queued_requests");
        colNameWithDesc.put("qmax", "max_queued_requests");
        colNameWithDesc.put("scur", "current sessions");
        colNameWithDesc.put("smax", "max sessions");
        colNameWithDesc.put("slim", "session limit");
        colNameWithDesc.put("stot", "total sessions");
        colNameWithDesc.put("bin", "bytes in");
        colNameWithDesc.put("bout", "bytes out");
        colNameWithDesc.put("dreq", "denied requests");
        colNameWithDesc.put("dresp", "denied responses");
        colNameWithDesc.put("ereq", "error requests");
        colNameWithDesc.put("econ", "connection errors");
        colNameWithDesc.put("eresp", "response errors");
        colNameWithDesc.put("wretr", "connection retries");
        colNameWithDesc.put("wredis", "request redispatches");
        colNameWithDesc.put("weight", "server weight");
        colNameWithDesc.put("act", "active servers");
        colNameWithDesc.put("bck", "backup servers");
        colNameWithDesc.put("chkfail", "checks failed");
        colNameWithDesc.put("chkdown", "number of transitions");
        colNameWithDesc.put("lastchg", "last transition");
        colNameWithDesc.put("downtime", "total downtime");
        colNameWithDesc.put("qlimit", "maxqueue");
        colNameWithDesc.put("pid", "pid");
        colNameWithDesc.put("iid", "unique proxy id");
        colNameWithDesc.put("sid", "server id");
        colNameWithDesc.put("throttle", "throttle percentage");
        colNameWithDesc.put("lbtot", "lbtot");
        colNameWithDesc.put("tracked", "tracked");
        colNameWithDesc.put("type", "type");
        colNameWithDesc.put("rate", "rate");
        colNameWithDesc.put("rate_lim", "rate limit");
        colNameWithDesc.put("rate_max", "rate max");
        colNameWithDesc.put("check_code", "check_code");
        colNameWithDesc.put("check_duration", "check_duration");
        colNameWithDesc.put("hrsp_1xx", "hrsp_1xx");
        colNameWithDesc.put("hrsp_2xx", "hrsp_2xx");
        colNameWithDesc.put("hrsp_3xx", "hrsp_3xx");
        colNameWithDesc.put("hrsp_4xx", "hrsp_4xx");
        colNameWithDesc.put("hrsp_5xx", "hrsp_5xx");
        colNameWithDesc.put("hrsp_other", "hrsp_other");
        colNameWithDesc.put("hanafail", "failed health check");
        colNameWithDesc.put("req_rate", "req_rate");
        colNameWithDesc.put("req_rate_max", "req_rate_max");
        colNameWithDesc.put("req_tot", "req_tot");
        colNameWithDesc.put("cli_abrt", "client aborts");
        colNameWithDesc.put("srv_abrt", "server abortes");
        colNameWithDesc.put("comp_in", "comp_in");
        colNameWithDesc.put("comp_out", "comp_out");
        colNameWithDesc.put("comp_byp", "comp_byp");
        colNameWithDesc.put("comp_rsp", "comp_rsp");
        colNameWithDesc.put("lastsess", "lastsess");
        colNameWithDesc.put("qtime", "qtime");
        colNameWithDesc.put("ctime", "ctime");
        colNameWithDesc.put("rtime", "rtime");
        colNameWithDesc.put("ttime", "ttime");

    }

    private void populateDictionary() {
        dictionary = new HashMap<String, Integer>();
        dictionary.put("# pxname", 0);
        dictionary.put("svname", 1);
        dictionary.put("qcur", 2);
        dictionary.put("qmax", 3);
        dictionary.put("scur", 4);
        dictionary.put("smax", 5);
        dictionary.put("slim", 6);
        dictionary.put("stot", 7);
        dictionary.put("bin", 8);
        dictionary.put("bout", 9);
        dictionary.put("dreq", 10);
        dictionary.put("dresp", 11);
        dictionary.put("ereq", 12);
        dictionary.put("econ", 13);
        dictionary.put("eresp", 14);
        dictionary.put("wretr", 15);
        dictionary.put("wredis", 16);
        dictionary.put("status", 17);
        dictionary.put("weight", 18);
        dictionary.put("act", 19);
        dictionary.put("bck", 20);
        dictionary.put("chkfail", 21);
        dictionary.put("chkdown", 22);
        dictionary.put("lastchg", 23);
        dictionary.put("downtime", 24);
        dictionary.put("qlimit", 25);
        dictionary.put("pid", 26);
        dictionary.put("iid", 27);
        dictionary.put("sid", 28);
        dictionary.put("throttle", 29);
        dictionary.put("lbtot", 30);
        dictionary.put("tracked", 31);
        dictionary.put("type", 32);
        dictionary.put("rate", 33);
        dictionary.put("rate_lim", 34);
        dictionary.put("rate_max", 35);
        dictionary.put("check_status", 36);
        dictionary.put("check_code", 37);
        dictionary.put("check_duration", 38);
        dictionary.put("hrsp_1xx", 39);
        dictionary.put("hrsp_2xx", 40);
        dictionary.put("hrsp_3xx", 41);
        dictionary.put("hrsp_4xx", 42);
        dictionary.put("hrsp_5xx", 43);
        dictionary.put("hrsp_other", 44);
        dictionary.put("hanafail", 45);
        dictionary.put("req_rate", 46);
        dictionary.put("req_rate_max", 47);
        dictionary.put("req_tot", 48);
        dictionary.put("cli_abrt", 49);
        dictionary.put("srv_abrt", 50);
        dictionary.put("comp_in", 51);
        dictionary.put("comp_out", 52);
        dictionary.put("comp_byp", 53);
        dictionary.put("comp_rsp", 54);
        dictionary.put("lastsess", 55);
        dictionary.put("qtime", 58);
        dictionary.put("ctime", 59);
        dictionary.put("rtime", 60);
        dictionary.put("ttime", 61);
    }

    /*
     * Main execution method that uploads the metrics to AppDynamics Controller
     *
     * @see
     * com.singularity.ee.agent.systemagent.api.ITask#execute(java.util.Map,
     * com.singularity.ee.agent.systemagent.api.TaskExecutionContext)
     */
    public TaskOutput execute(Map<String, String> taskArguments, TaskExecutionContext arg1) throws TaskExecutionException {
        if (taskArguments != null) {
            logger.info("Starting the HAProxy Monitoring Task");
            try {
                taskArguments = ArgumentsValidator.validateArguments(taskArguments, DEFAULT_ARGS);
                String password = CryptoUtil.getPassword(taskArguments);
                if (Strings.isNullOrEmpty(password)) {
                    taskArguments.put("password", password);
                }
                SimpleHttpClient httpClient = SimpleHttpClient.builder(taskArguments).build();

                String csvPath = taskArguments.get(CSV_EXPORT_URI);
                String responseString = httpClient.target().path(csvPath).get().string();

                // reads the csv output and writes the response to a spreadsheet
                // which is used inturn to get the metrics
                writeResponseToWorkbook(responseString);

                Map<Integer, String> allProxies = getAllProxyAndTypes(dictionary.get("# pxname"));
                Map<Integer, String> proxyTypes = getAllProxyAndTypes(dictionary.get("svname"));

                Map<Integer, String> proxiesToBeMonitored = filterProxies(taskArguments, allProxies);
                printStats(taskArguments, proxiesToBeMonitored, proxyTypes);

                logger.info("HAProxy Monitoring Task completed successfully");
                return new TaskOutput("HAProxy Monitoring Task completed successfully");
            } catch (Exception e) {
                logger.error("HAProxy Metrics collection failed", e);
            }
        }
        throw new TaskExecutionException("HAProxy Monitor task completed with failures");
    }

    private Map<Integer, String> filterProxies(Map<String, String> taskArguments, Map<Integer, String> allProxies) {
        List<String> proxiesListedInConfigFile = Lists.newArrayList();
        if (taskArguments.containsKey("proxynames") && !Strings.isNullOrEmpty(taskArguments.get("proxynames"))) {
            proxiesListedInConfigFile = Arrays.asList(taskArguments.get("proxynames").split(","));
        }
        if (proxiesListedInConfigFile.size() != 0) {
            for (Iterator<Map.Entry<Integer, String>> it = allProxies.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Integer, String> entry = it.next();
                if (!proxiesListedInConfigFile.contains(entry.getValue())) {
                    it.remove();
                }
            }
        }
        return allProxies;
    }

    private void printStats(Map<String, String> taskArguments, Map<Integer, String> proxiesToBeMonitored, Map<Integer, String> proxyTypes) {

        List<String> excludedStats = new ArrayList<String>();
        if (taskArguments.containsKey("excludeStats") && !Strings.isNullOrEmpty(taskArguments.get("excludeStats"))) {
            excludedStats = Arrays.asList(taskArguments.get("excludeStats").split(","));
        }

        // Prints metrics to Controller Metric Browser
        metricPrefix = taskArguments.get(TaskInputArgs.METRIC_PREFIX) + METRIC_SEPARATOR;
        for (Map.Entry<Integer, String> proxy : proxiesToBeMonitored.entrySet()) {
            if (!excludedStats.contains("status")) {
                printMetric(proxy.getValue() + METRIC_SEPARATOR + proxyTypes.get(proxy.getKey()) + METRIC_SEPARATOR, "status", getStatus(proxy.getKey()));
            }

            String healthCheckStatus = getHealthCheckStatus(proxy.getKey());
            if (!excludedStats.contains("check_status") && !"".equals(healthCheckStatus)) {
                printMetric(proxy.getValue() + METRIC_SEPARATOR + proxyTypes.get(proxy.getKey()) + METRIC_SEPARATOR, "check_status", healthCheckStatus);
            }

            for (Entry<String, String> cols : colNameWithDesc.entrySet()) {
                if (!excludedStats.contains(cols.getKey())) {
                    printMetric(proxy, proxyTypes, cols.getKey(), cols.getValue());
                }
            }
        }
    }

    /**
     * Writes the csv respone to a spreadsheet as a matrix of proxies Vs stats
     *
     * @param responseString
     * @throws Exception
     */
    private void writeResponseToWorkbook(String responseString) throws Exception {
        try {
            OutputStream outputStream = new ByteArrayOutputStream();
            outputStream.write(responseString.getBytes());
            workbook = Workbook.createWorkbook(outputStream);

            WritableSheet sheet = workbook.createSheet("First Sheet", 0);

            BufferedReader reader = new BufferedReader(new StringReader(responseString));
            String line;
            int j = 0;
            while ((line = reader.readLine()) != null) {
                Pattern p = Pattern.compile(",");
                String[] result = p.split(line);
                for (int i = 0; i < result.length; i++) {
                    Label label = new jxl.write.Label(i, j, result[i]);
                    sheet.addCell(label);
                }
                j++;
            }
            workbook.write();
            workbook.close();
            outputStream.close();
        } catch (Exception e) {
            logger.error("Error while writing response to workbook stream ", e);
            throw new RuntimeException("Error while writing response to workbook stream");
        }
    }

    /**
     * Returns all proxies and proxy types as a Map of row index and Proxy Name.
     * Column Index is 0 for Px Name and 1 for Server Name
     *
     * @param columnIndex
     * @return
     */
    private Map<Integer, String> getAllProxyAndTypes(int columnIndex) {
        Map<Integer, String> proxiesMap = new HashMap<Integer, String>();
        int rows = getSheet().getRows();
        for (int i = 1; i < rows; i++) {
            proxiesMap.put(i, getSheet().getCell(columnIndex, i).getContents());
        }
        return proxiesMap;
    }

    /**
     * Prints the metric to the AppDynamics Controller Metric Browser
     *
     * @param proxy
     * @param proxyTypes
     * @param metricKey
     * @param metricName
     */
    private void printMetric(Entry<Integer, String> proxy, Map<Integer, String> proxyTypes, String metricKey, String metricName) {
        if (!getCellContents(dictionary.get(metricKey), proxy.getKey()).equals("")) {
            printMetric(proxy.getValue() + METRIC_SEPARATOR + proxyTypes.get(proxy.getKey()) + METRIC_SEPARATOR, metricName,
                    getCellContents(dictionary.get(metricKey), proxy.getKey()));
        }
    }

    private void printMetric(String metricPath, String metricName, Object metricValue) {
        if (metricValue != null) {
            MetricWriter metricWriter = super.getMetricWriter(getMetricPrefix() + metricPath + metricName,
                    MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE, MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE,
                    MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
            metricWriter.printMetric(String.valueOf(metricValue));
        }
    }

    private String getMetricPrefix() {
        return metricPrefix;
    }

    /**
     * Gets the status of the proxy/server. If it is Up | Open, status is set to
     * 1; if not to 0
     *
     * @param proxyRowIndex
     * @return
     */
    private String getStatus(int proxyRowIndex) {
        String status = "0";
        if (getCellContents(dictionary.get("status"), proxyRowIndex).equals("UP")
                || getCellContents(dictionary.get("status"), proxyRowIndex).equals("OPEN")) {
            status = "1";
        }
        return status;
    }

    /**
     * Last health check status. For more info please look at check_status in http://cbonte.github.io/haproxy-dconv/configuration-1.5.html#9
     *
     * @param proxyRowIndex
     * @return
     */
    private String getHealthCheckStatus(int proxyRowIndex) {
        String check_status = getCellContents(dictionary.get("check_status"), proxyRowIndex);
        if ("UNK".equals(check_status)) {
            return "0";
        } else if ("INI".equals(check_status)) {
            return "1";
        } else if ("SOCKERR".equals(check_status)) {
            return "2";
        } else if ("L4OK".equals(check_status)) {
            return "3";
        } else if ("L4TOUT".equals(check_status)) {
            return "4";
        } else if ("L4CON".equals(check_status)) {
            return "5";
        } else if ("L6OK".equals(check_status)) {
            return "6";
        } else if ("L6TOUT".equals(check_status)) {
            return "7";
        } else if ("L6RSP".equals(check_status)) {
            return "8";
        } else if ("L7OK".equals(check_status)) {
            return "9";
        } else if ("L7OKC".equals(check_status)) {
            return "10";
        } else if ("L7TOUT".equals(check_status)) {
            return "11";
        } else if ("L7RSP".equals(check_status)) {
            return "12";
        } else if ("L7STS".equals(check_status)) {
            return "13";
        } else {
            return "";
        }

    }

    /**
     * Returns the zeroth sheet of the workbook
     *
     * @return
     */
    private Sheet getSheet() {
        return workbook.getSheet(0);
    }

    /**
     * Gets the contents of the cell in the workbook given column and row
     *
     * @param column
     * @param row
     * @return String
     */
    private String getCellContents(int column, int row) {
        String contents = getSheet().getCell(column, row).getContents();
        return contents;
    }

    private static String getImplementationVersion() {
        return HAProxyMonitor.class.getPackage().getImplementationTitle();
    }
}