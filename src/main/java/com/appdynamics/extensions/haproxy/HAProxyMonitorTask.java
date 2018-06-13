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
import com.appdynamics.extensions.haproxy.config.ProxyStats;
import com.appdynamics.extensions.haproxy.config.ServerConfig;
import com.appdynamics.extensions.http.HttpClientUtils;
import com.appdynamics.extensions.http.UrlBuilder;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.util.AssertUtils;
import com.appdynamics.extensions.util.StringUtils;
import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.write.Label;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.StringReader;
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
            AssertUtils.assertNotNull(responseString, "response of the request is empty");

            //reads the csv output and writes the response to a spreadsheet which is used to get the metrics
            writeResponseToWorkbook(responseString);
            ProxyStats proxyStats = (ProxyStats) configuration.getMetricsXml();
            Map<String, List<String>> proxyServers = mapProxyServers(proxyStats);
            CollectAllMetrics(proxyServers);

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

    Map<String, List<String>> mapProxyServers(ProxyStats proxyStats) {
        Map<String, List<String>> proxyServersMap = new HashMap<>();
        List<String> serverList = null;
        for (ServerConfig scfg : proxyStats.getProxyServerConfig().getServerConfigs()) {
            if (proxyServersMap.get(scfg.getPxname()) == null) {
                serverList = new LinkedList<>();
                serverList.add(scfg.getSvname());
                proxyServersMap.put(scfg.getPxname(), serverList);
            } else {
                proxyServersMap.get(scfg.getPxname()).add(scfg.getSvname());
            }
        }
        return proxyServersMap;
    }

    /**
     * @param proxyServers
     */
    private void CollectAllMetrics(Map<String, List<String>> proxyServers) {
        logger.debug("Starting the collect all metrics from the generated response");
        // Prints metrics to Controller Metric Browser
        final int statusColIndex = getColumnFromMetricKey("status");
        final int check_statusColIndex = getColumnFromMetricKey("check_status");
        MetricConfig[] metricConfigs = ((ProxyStats) configuration.getMetricsXml()).getStat().getMetricConfig();
        for (int index = 1; index < getSheet().getRows(); index++) {
            Cell[] worksheetRow = getSheet().getRow(index);
            if (proxyServers.get(worksheetRow[Constant.PROXY_INDEX].getContents()) != null && proxyServers.get(worksheetRow[Constant.PROXY_INDEX].getContents()).contains(worksheetRow[Constant.PROXY_TYPE_INDEX].getContents())) {
                String healthCheckStatus = getHealthCheckStatus(index, check_statusColIndex);
                for (MetricConfig config : metricConfigs) {
                    if (config.getAttr().equals("status")) {
                        Metric metric = new Metric("status", getStatus(index, statusColIndex), metricPrefix + Constant.METRIC_SEPARATOR + worksheetRow[Constant.PROXY_INDEX].getContents() + Constant.METRIC_SEPARATOR + worksheetRow[Constant.PROXY_TYPE_INDEX].getContents() + Constant.METRIC_SEPARATOR + "status");
                        metrics.add(metric);
                    } else if (config.getAttr().equals("check_status") && !"".equals(healthCheckStatus)) {
                        Metric metric = new Metric("check_status", healthCheckStatus, metricPrefix + Constant.METRIC_SEPARATOR + worksheetRow[Constant.PROXY_INDEX].getContents() + Constant.METRIC_SEPARATOR + worksheetRow[Constant.PROXY_TYPE_INDEX].getContents() + Constant.METRIC_SEPARATOR + "check_status");
                        metrics.add(metric);
                    } else
                        printMetric(config, worksheetRow);
                }
            }
        }
        if (metrics != null && metrics.size() > 0) {
            logger.debug("metrics collected and starting print metrics");
            metricWriter.transformAndPrintMetrics(metrics);
        }
    }

    /**
     * @param config
     * @param worksheetRow
     */
    private void printMetric(MetricConfig config, Cell[] worksheetRow) {
        int column = config.getColumn();
        if (column == -1) return;
        String cellContent = worksheetRow[column].getContents();
        if (!cellContent.equals("")) {
            Metric metric = new Metric(config.getAlias(), cellContent, metricPrefix + Constant.METRIC_SEPARATOR + worksheetRow[Constant.PROXY_INDEX].getContents()
                    + Constant.METRIC_SEPARATOR + worksheetRow[Constant.PROXY_TYPE_INDEX].getContents() + Constant.METRIC_SEPARATOR + config.getAlias());
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
        MetricConfig[] metricConfigs = ((ProxyStats) configuration.getMetricsXml()).getStat().getMetricConfig();
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
