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
import com.appdynamics.extensions.haproxy.config.MetricConfig;
import com.appdynamics.extensions.haproxy.config.MetricConverter;
import com.appdynamics.extensions.haproxy.config.ProxyStats;
import com.appdynamics.extensions.haproxy.config.ServerConfig;
import com.appdynamics.extensions.http.HttpClientUtils;
import com.appdynamics.extensions.http.UrlBuilder;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.util.AssertUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code HAProxyMonitorTask} class handles the {@code AMonitorTaskRunnable} Task submitted by
 * the {@code HAProxyMonitor}. It runs as a new thread which gets a response from the server, builds
 * the metric list and prints it.
 *
 * @author Prashant M
 * @since appd-exts-commons:2.1.0
 */
public class HAProxyMonitorTask implements AMonitorTaskRunnable {

    private static final Logger logger = Logger.getLogger(HAProxyMonitorTask.class);

    private MonitorContextConfiguration configuration;

    private Map haServerArgs;

    private MetricWriteHelper metricWriter;

    private String metricPrefix;

    private int heartBeatValue = 0;

    public HAProxyMonitorTask(MonitorContextConfiguration configuration, MetricWriteHelper metricWriteHelper, Map haServerArgs) {
        this.configuration = configuration;
        this.haServerArgs = haServerArgs;
        this.metricPrefix = configuration.getMetricPrefix() + Constant.METRIC_SEPARATOR + haServerArgs.get(Constant.DISPLAY_NAME);
        this.metricWriter = metricWriteHelper;
    }

    @Override
    public void onTaskComplete() {
        metricWriter.printMetric(metricPrefix + "|HeartBeat", BigDecimal.valueOf(heartBeatValue), "AVG.AVG.IND");
        logger.info("Completed the HAProxy Monitoring Task");
    }

    public void run() {
        logger.info("Starting the HAProxy Monitoring Task for : " + haServerArgs.get(Constant.DISPLAY_NAME));
        try {
            Map<String, String> requestMap = buildRequestMap(haServerArgs);
            String csvPath = (String) haServerArgs.get(Constant.CSV_EXPORT_URI);
            CloseableHttpClient httpClient = configuration.getContext().getHttpClient();
            String url = UrlBuilder.builder(requestMap).path(csvPath).build();
            String responseString = HttpClientUtils.getResponseAsStr(httpClient, url);
            AssertUtils.assertNotNull(responseString, "response of the request is empty");
            heartBeatValue = 1;

            //reads the csv output and writes the response to a spreadsheet which is used to get the metrics
            List<List<String>> workbook = writeResponseToWorkbook(responseString);
            Map<String, List<String>> proxyServersMap = mapProxyServers();
            collectAllMetrics(proxyServersMap, workbook);
            logger.info("HAProxy Monitoring Task completed successfully for : " + haServerArgs.get(Constant.DISPLAY_NAME));
        } catch (Exception e) {
            logger.error("HAProxy Metrics collection failed for : " + haServerArgs.get(Constant.DISPLAY_NAME), e);
        }
    }

    /**
     * Writes the csv response to a spreadsheet as a matrix of proxies Vs stats
     *
     * @param responseString
     * @throws Exception
     */
    private List<List<String>> writeResponseToWorkbook(String responseString) throws Exception {
        try {
            OutputStream outputStream = new ByteArrayOutputStream();
            outputStream.write(responseString.getBytes());
            List<List<String>> workbook = new LinkedList<>();
            BufferedReader reader = new BufferedReader(new StringReader(responseString));
            String line;
            while ((line = reader.readLine()) != null) {
                Pattern p = Pattern.compile(",");
                String[] currLine = p.split(line);
                List<String> row = new LinkedList<>();
                for (String columnVal : currLine) {
                    row.add(columnVal);
                }
                workbook.add(row);
            }
            outputStream.close();
            logger.debug("response string written to the workbook");
            return workbook;
        } catch (Exception e) {
            throw new RuntimeException("Error while writing response to workbook stream");
        }
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

    /**
     * Creates a Map of proxy vs List of servers from the response
     *
     * @return
     */
    Map<String, List<String>> mapProxyServers() {
        Map<String, List<String>> proxyServersMap = new HashMap<>();
        List<ServerConfig> serverConfigList = getMappedServerConfigs(haServerArgs);
        for (ServerConfig serverConfig : serverConfigList) {
            if (proxyServersMap.get(serverConfig.getPxname()) == null) {
                List<String> serverList = new LinkedList<>();
                serverList.add(serverConfig.getSvname());
                proxyServersMap.put(serverConfig.getPxname(), serverList);
            } else {
                proxyServersMap.get(serverConfig.getPxname()).add(serverConfig.getSvname());
            }
        }
        return proxyServersMap;
    }

    /**
     * Collects all the metrics corresponding to the entries in the worksheet
     *
     * @param proxyServersMap
     */
    private void collectAllMetrics(Map<String, List<String>> proxyServersMap, List<List<String>> workbook) {
        logger.debug("Starting the collect all metrics from the generated response");
        // Prints metrics to Controller Metric Browser
        MetricConfig[] metricConfigs = ((ProxyStats) configuration.getMetricsXml()).getStat().getMetricConfig();
        List<Metric> metrics = new ArrayList<Metric>();
        //Ignoring the first row as it will have the headers from the CSV.
        for (int rowNum = 1; rowNum < workbook.size(); rowNum++) {
            List<String> workbookRow = workbook.get(rowNum);
            String pxName = workbookRow.get(Constant.PROXY_INDEX);
            List<String> serverList = getProxyServersList(proxyServersMap, pxName);
            if (proxyServersMap.isEmpty() || (serverList != null && checkStringPatternMatch(serverList, workbookRow.get(Constant.PROXY_TYPE_INDEX))))
                metrics.addAll(populateServerMetrics(metricConfigs, workbookRow));
        }
        if (metrics != null && metrics.size() > 0) {
            logger.debug("metrics collected and starting print metrics");
            metricWriter.transformAndPrintMetrics(metrics);
        }
    }

    /**
     * collects metrics from the workSheet for respective metricConfigs
     *
     * @param config
     * @param workbookRow
     * @param commonMetricPath
     * @param propertiesMap
     */
    private Metric collectMetric(MetricConfig config, List<String> workbookRow, String commonMetricPath, Map<String, String> propertiesMap) {
        String cellContent = workbookRow.get(config.getColumn());
        if (!cellContent.equals(""))
            return new Metric(config.getAlias(), cellContent, commonMetricPath + config.getAlias(), propertiesMap);
        return null;
    }

    /**
     * Gets the status of the proxy/server. If it is Up | Open, status is set to
     * 1; if not to 0
     *
     * @param converters
     * @param status
     * @return
     */
    private String getConvertedStatus(MetricConverter[] converters, String status) {
        for (MetricConverter converter : converters) {
            if (converter.getLabel().equals(status))
                return converter.getValue();
        }
        return "";
    }

    /**
     *
     * @param server
     * @return
     */
    private List<ServerConfig> getMappedServerConfigs(Map<String, ?> server) {
        System.out.println(server);
        List<Map<String, ?>> proxyServers = (List<Map<String, ?>>) server.get("proxyServers");
        List<ServerConfig> serverConfigList = new LinkedList<>();
        if (proxyServers != null) {
            for (Map<String, ?> proxyServer : proxyServers) {
                String pxname = null;
                for (Map.Entry<String, ?> entry : proxyServer.entrySet()) {
                    if (entry.getKey().equals("pxname")) {
                        pxname = (String) entry.getValue();
                    }
                    if (entry.getKey().equals("svname")) {
                        List<String> svnames = (List<String>) entry.getValue();
                        for (String svname : svnames) {
                            ServerConfig serverConfig = new ServerConfig(pxname, svname);
                            serverConfigList.add(serverConfig);
                        }
                    }
                }
            }
        }
        return serverConfigList;
    }

    /**
     *
     * @param proxyServers
     * @param pxName
     * @return
     */
    private List<String> getProxyServersList(Map<String, List<String>> proxyServers, String pxName) {
        Set<String> configPatterns = proxyServers.keySet();
        for (String configPxName : configPatterns) {
            if (checkRegexMatch(pxName, configPxName))
                return proxyServers.get(configPxName);
        }
        logger.debug("No match found for the proxy name : " + pxName);
        return null;
    }

    /**
     *
     * @param configPatterns
     * @param svName
     * @return
     */
    private boolean checkStringPatternMatch(List<String> configPatterns, String svName) {
        for (String configPxName : configPatterns) {
            if (checkRegexMatch(svName, configPxName)) {
                logger.debug("Match found for SvName :" + svName);
                return true;
            }
        }
        return false;
    }

    /**
     *
     * @param text
     * @param pattern
     * @return
     */
    private boolean checkRegexMatch(String text, String pattern) {
        Pattern regexPattern = Pattern.compile(pattern);
        Matcher regexMatcher = regexPattern.matcher(text);
        return regexMatcher.matches();
    }

    /**
     *
     * @param metricConfigs
     * @param workbookRow
     * @return
     */
    private List<Metric> populateServerMetrics(MetricConfig[] metricConfigs, List<String> workbookRow) {
        List<Metric> metrics = new LinkedList<>();
        ObjectMapper objectMapper = new ObjectMapper();
        for (MetricConfig config : metricConfigs) {
            Map<String, String> propertiesMap = objectMapper.convertValue(config, Map.class);
            //For any Non-Integer metric, it will be evaluated from the MetricConverter as defined in the metric.xml
            String commonMetricPath = metricPrefix + Constant.METRIC_SEPARATOR + workbookRow.get(Constant.PROXY_INDEX) + Constant.METRIC_SEPARATOR + workbookRow.get(Constant.PROXY_TYPE_INDEX) + Constant.METRIC_SEPARATOR;
            if (config.getMetricConverter() != null) {
                String convertedMetricValue = getConvertedStatus(config.getMetricConverter(), workbookRow.get(config.getColumn()));
                if (!convertedMetricValue.equals("")) {
                    Metric metric = new Metric(config.getAlias(), convertedMetricValue, commonMetricPath + config.getAlias(), propertiesMap);
                    metrics.add(metric);
                }
            } else {
                Metric metric = collectMetric(config, workbookRow, commonMetricPath, propertiesMap);
                if (metric != null)
                    metrics.add(metric);
            }
            logger.debug("Collected metrics for : " + commonMetricPath + config.getAlias());
        }
        return metrics;
    }

}
