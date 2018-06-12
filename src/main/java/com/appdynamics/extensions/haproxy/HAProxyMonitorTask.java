/*
 *
 *   Copyright 2018. AppDynamics LLC and its affiliates.
 *   All Rights Reserved.
 *   This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 *   The copyright notice above does not evidence any actual or intended publication of such source code.
 *
 */

package com.appdynamics.extensions.haproxy;

import com.appdynamics.extensions.AMonitorTaskRunnable;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.crypto.CryptoUtil;
import com.appdynamics.extensions.haproxy.config.MetricConfig;
import com.appdynamics.extensions.haproxy.metrics.Stat;
import com.appdynamics.extensions.http.HttpClientUtils;
import com.appdynamics.extensions.http.UrlBuilder;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.util.StringUtils;
import com.google.common.collect.Lists;
import com.opencsv.CSVReader;
import jxl.Sheet;
import jxl.Workbook;
import jxl.write.Label;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.log4j.Logger;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * The {@code HAProxyMonitorTask} class handles the {@code AMonitorTaskRunnable} Task submitted by
 * the {@code HAProxyMonitor}. It runs as a new thread which gets a response from the server, builds
 * the metric list and prints it.
 *
 * @author Balakrishna V, Prashant M
 * @since appd-exts-commons:2.1.0
 */
public class HAProxyMonitorTask implements AMonitorTaskRunnable {

    private static final Logger logger = Logger.getLogger(HAProxyMonitorTask.class);

    private MonitorContextConfiguration configuration;

    private WritableWorkbook workbook;

    private Map haServerArgs;

    private MetricWriteHelper metricWriter;

    private String metricPrefix;

    private List<Metric> metrics = new ArrayList<Metric>();

    public HAProxyMonitorTask(MonitorContextConfiguration configuration, MetricWriteHelper metricWriteHelper, Map haServerArgs) {
        this.configuration = configuration;
        this.haServerArgs = haServerArgs;
        this.metricPrefix = configuration.getMetricPrefix() + Constant.METRIC_SEPARATOR + haServerArgs.get(Constant.DISPLAY_NAME);
        this.metricWriter = metricWriteHelper;
    }

    @Override
    public void onTaskComplete() {
        logger.info("Completed the HAProxy Monitoring Task");
    }

    public void run() {
        logger.info("Starting the HAProxy Monitoring Task for : " + haServerArgs.get(Constant.DISPLAY_NAME));
        try {
            String password = CryptoUtil.getPassword(haServerArgs);
            if (!StringUtils.validateStrings(password)) {
                haServerArgs.put(Constant.PASSWORD, password);
            }

            Map<String, String> requestMap = buildRequestMap(haServerArgs);
            String csvPath = (String) haServerArgs.get(Constant.CSV_EXPORT_URI);
            CloseableHttpClient httpClient = configuration.getContext().getHttpClient();
            String url = UrlBuilder.builder(requestMap).path(csvPath).build();
            String responseString = HttpClientUtils.getResponseAsStr(httpClient, url);
            //AssertUtils.assertNotNull(responseString, "response of the request is empty");


            // In case you want to read the response from local file
            if (responseString == null) {
                responseString = "";
                String csvFile = "/Users/prashant.mehta/Downloads/demo.csv";

                CSVReader reader = null;
                try {
                    reader = new CSVReader(new FileReader(csvFile));
                    String[] line;
                    while ((line = reader.readNext()) != null) {
                        String str = "";
                        for (int i = 0; i < line.length; i++) {
                            str += (line[i] + ',');
                        }
                        str += '\n';
                        responseString += str;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }


            // reads the csv output and writes the response to a spreadsheet which is used to get the metrics
            writeResponseToWorkbook(responseString);
            Stat.Stats stats = (Stat.Stats) configuration.getMetricsXml();
            //gets all proxies from the workbook which is in columns 0
            Map<Integer, String> allProxies = getAllProxyAndTypes(Constant.PROXY_INDEX);
            //get all proxy types from the workbook which is in column 1
            Map<Integer, String> proxyTypes = getAllProxyAndTypes(Constant.PROXY_TYPE_INDEX);

            Map<Integer, String> proxiesToBeMonitored = filterProxies(haServerArgs, allProxies);
            CollectAllMetrics(haServerArgs, proxiesToBeMonitored, proxyTypes);

            logger.info("HAProxy Monitoring Task completed successfully for : " + Constant.DISPLAY_NAME);
        } catch (Exception e) {
            logger.error("HAProxy Metrics collection failed for : " + Constant.DISPLAY_NAME, e);
        }
    }

    /**
     * Writes the csv response to a spreadsheet as a matrix of proxies Vs stats
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
                    Label label = new Label(i, j, result[i]);
                    sheet.addCell(label);
                }
                j++;
            }
            workbook.write();
            workbook.close();
            outputStream.close();
            logger.debug("response string written to the workbook");
        } catch (Exception e) {
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
     * Returns the zeroth sheet of the workbook
     *
     * @return
     */
    private Sheet getSheet() {
        return workbook.getSheet(0);
    }

    /*
     * Builds a hashMap of the HAServer from the read config file.
     *
     * @param haServer
     * @return
     */
    private Map<String, String> buildRequestMap(Map haServer) {
        Map<String, String> requestMap = new HashMap<String, String>();
        requestMap.put(Constant.HOST, (String) haServer.get(Constant.HOST));
        requestMap.put(Constant.PORT, String.valueOf(haServer.get(Constant.PORT)));
        requestMap.put(Constant.USE_SSL, String.valueOf(haServer.get(Constant.USE_SSL)));
        requestMap.put(Constant.USER_NAME, (String) haServer.get(Constant.USER_NAME));
        requestMap.put(Constant.PASSWORD, (String) haServer.get(Constant.PASSWORD));
        return requestMap;
    }

    /*
     *  Filter proxies from the HA-server proxy arguments and remove it from the all-proxies
     *  @param haServerArgs
     *  @param allProxies
     *  @return
     */

    private Map<Integer, String> filterProxies(Map<String, String> haServerArgs, Map<Integer, String> allProxies) {
        List<String> excludeProxies = Lists.newArrayList();
        if (haServerArgs.containsKey(Constant.PROXY_NAMES) && StringUtils.validateStrings(haServerArgs.get(Constant.PROXY_NAMES))) {
            excludeProxies = Arrays.asList(haServerArgs.get(Constant.PROXY_NAMES).split(","));
        }
        if (excludeProxies.size() != 0) {
            for (Iterator<Map.Entry<Integer, String>> it = allProxies.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Integer, String> entry = it.next();
                if (!excludeProxies.contains(entry.getValue())) {
                    it.remove();
                }
            }
        }
        logger.debug("filtered all the exclude proxies");
        return allProxies;
    }


    private void CollectAllMetrics(Map<String, String> haServerArgs, Map<Integer, String> proxiesToBeMonitored, Map<Integer, String> proxyTypes) {
        logger.debug("Starting the collect all metrics from the generated response");
        // Prints metrics to Controller Metric Browser
        final int statusColIndex = getColumnFromMetricKey("status");
        final int check_statusColIndex = getColumnFromMetricKey("check_status");
        MetricConfig[] metricConfigs = ((Stat.Stats) configuration.getMetricsXml()).getStat().getMetricConfig();
        for (Map.Entry<Integer, String> proxy : proxiesToBeMonitored.entrySet()) {
            String healthCheckStatus = getHealthCheckStatus(proxy.getKey(), check_statusColIndex);
            for (MetricConfig config : metricConfigs) {
                if (config.getAttr().equals("status")) {
                    Metric metric = new Metric("status", getStatus(proxy.getKey(), statusColIndex), metricPrefix + Constant.METRIC_SEPARATOR + proxy.getValue() + Constant.METRIC_SEPARATOR + proxyTypes.get(proxy.getKey()) + Constant.METRIC_SEPARATOR + "status");
                    metrics.add(metric);
                } else if (config.getAttr().equals("check_status") && !"".equals(healthCheckStatus)) {
                    Metric metric = new Metric("check_status", healthCheckStatus, metricPrefix + Constant.METRIC_SEPARATOR + proxy.getValue() + Constant.METRIC_SEPARATOR + proxyTypes.get(proxy.getKey()) + Constant.METRIC_SEPARATOR + "check_status");
                    metrics.add(metric);
                } else
                    printMetric(proxy, proxyTypes, config);
            }
        }
        if (metrics != null && metrics.size() > 0) {
            logger.debug("metrics collected and starting print metrics");
            metricWriter.transformAndPrintMetrics(metrics);
        }
    }

    /**
     * @param proxy
     * @param proxyTypes
     * @param config
     */
    private void printMetric(Map.Entry<Integer, String> proxy, Map<Integer, String> proxyTypes, MetricConfig config) {
        int column = config.getColumn();
        if (column == -1) return;
        String cellContent = getCellContents(column, proxy.getKey());
        if (!cellContent.equals("")) {
            Metric metric = new Metric(config.getAlias(), cellContent, metricPrefix + Constant.METRIC_SEPARATOR + proxy.getValue()
                    + Constant.METRIC_SEPARATOR + proxyTypes.get(proxy.getKey()) + Constant.METRIC_SEPARATOR + config.getAlias());
            metrics.add(metric);
        }
    }


    /**
     * Returns the column name for the metricKey as in the worksheet
     *
     * @param metricKey
     * @return
     */
    private int getColumnFromMetricKey(String metricKey) {
        MetricConfig[] metricConfigs = ((Stat.Stats) configuration.getMetricsXml()).getStat().getMetricConfig();
        int column = 0;
        for (MetricConfig config : metricConfigs) {
            if (config.getAttr().equals(metricKey)) {
                column = config.getColumn();
                break;
            }
        }
        return column;
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


    /**
     * Last health check status. For more info please look at check_status in http://cbonte.github.io/haproxy-dconv/configuration-1.5.html#9
     *
     * @param proxyRowIndex
     * @return
     */
    private String getHealthCheckStatus(int proxyRowIndex, int check_statusColIndex) {
        String check_status = getCellContents(check_statusColIndex, proxyRowIndex);
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
     * Gets the status of the proxy/server. If it is Up | Open, status is set to
     * 1; if not to 0
     *
     * @param proxyRowIndex
     * @return
     */
    private String getStatus(int proxyRowIndex, int statusColIndex) {
        String status = "0";
        if (getCellContents(statusColIndex, proxyRowIndex).equals("UP")
                || getCellContents(statusColIndex, proxyRowIndex).equals("OPEN")) {
            status = "1";
        }
        return status;
    }

    /**
     * Returns the metrics collected
     *
     * @return
     */
    protected List<Metric> getMetrics() {
        return metrics;
    }

}
