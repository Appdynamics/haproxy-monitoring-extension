package com.appdynamics.extensions.haproxy;

import com.appdynamics.extensions.AMonitorTaskRunnable;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.TaskInputArgs;
import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.crypto.CryptoUtil;
import com.appdynamics.extensions.haproxy.config.MetricConfig;
import com.appdynamics.extensions.haproxy.metrics.Stat;
import com.appdynamics.extensions.http.HttpClientUtils;
import com.appdynamics.extensions.http.UrlBuilder;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.util.ArgumentsValidator;
import com.appdynamics.extensions.util.AssertUtils;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
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

public class HAProxyMonitorTask implements AMonitorTaskRunnable {

    private static final Logger logger = Logger.getLogger(HAProxyMonitorTask.class);

    private static final String CSV_EXPORT_URI = "csvExportUri";

    private static final String METRIC_SEPARATOR = "|";

    private static final Map<String, String> DEFAULT_ARGS = new HashMap<String, String>() {
        {
            put(TaskInputArgs.METRIC_PREFIX, "Custom Metrics|HAProxy");
        }
    };

    private MonitorContextConfiguration configuration;

    private WritableWorkbook workbook;

    private Map haServerArgs;

    private MetricWriteHelper metricWriter;

    private String metricPrefix;

    private List<Metric> metrics = new ArrayList<Metric>();

    public HAProxyMonitorTask(TasksExecutionServiceProvider serviceProvider, MonitorContextConfiguration configuration, Map haServerArgs) {
        this.configuration = configuration;
        this.haServerArgs = haServerArgs;
        this.metricPrefix = configuration.getMetricPrefix() + "|" + haServerArgs.get("displayName");
        this.metricWriter = serviceProvider.getMetricWriteHelper();
    }

    public void onTaskComplete() {

    }

    public void run() {

        logger.info("Starting the HAProxy Monitoring Task");
        try {
            haServerArgs = ArgumentsValidator.validateArguments(haServerArgs, DEFAULT_ARGS);
            String password = CryptoUtil.getPassword(haServerArgs);
            if (Strings.isNullOrEmpty(password)) {
                haServerArgs.put("password", password);
            }

            Map<String, String> requestMap = buildRequestMap(haServerArgs);
            String csvPath = (String) haServerArgs.get(CSV_EXPORT_URI);
            CloseableHttpClient httpClient = configuration.getContext().getHttpClient();
            String url = UrlBuilder.builder(requestMap).path(csvPath).build();
            String responseString = HttpClientUtils.getResponseAsStr(httpClient, url);
            AssertUtils.assertNotNull(responseString, "response of the request is empty");

/*          when need to read the response from local file
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
            */

            // reads the csv output and writes the response to a spreadsheet
            // which is used in-turn to get the metrics
            writeResponseToWorkbook(responseString);
            Stat.Stats stats = (Stat.Stats) configuration.getMetricsXml();
            Map<Integer, String> allProxies = getAllProxyAndTypes(stats.getStats().getMetricConfig()[0].getColumn());
            Map<Integer, String> proxyTypes = getAllProxyAndTypes(stats.getStats().getMetricConfig()[1].getColumn());

            Map<Integer, String> proxiesToBeMonitored = filterProxies(haServerArgs, allProxies);
            printStats(haServerArgs, proxiesToBeMonitored, proxyTypes);

            logger.info("HAProxy Monitoring Task completed successfully");
        } catch (Exception e) {
            logger.error("HAProxy Metrics collection failed", e);
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
                    Label label = new Label(i, j, result[i]);
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
     * Returns the zeroth sheet of the workbook
     *
     * @return
     */
    private Sheet getSheet() {
        return workbook.getSheet(0);
    }


    private Map<String, String> buildRequestMap(Map haServer) {
        Map<String, String> requestMap = new HashMap<String, String>();
        requestMap.put("host", (String) haServer.get("host"));
        requestMap.put("port", String.valueOf(haServer.get("port")));
        requestMap.put("useSsl", String.valueOf(haServer.get("useSsl")));
        requestMap.put("username", (String) haServer.get("username"));
        requestMap.put("password", (String) haServer.get("password"));
        return requestMap;
    }


    private Map<Integer, String> filterProxies(Map<String, String> haServerArgs, Map<Integer, String> allProxies) {
        List<String> proxiesListedInConfigFile = Lists.newArrayList();
        if (haServerArgs.containsKey("proxynames") && !Strings.isNullOrEmpty(haServerArgs.get("proxynames"))) {
            proxiesListedInConfigFile = Arrays.asList(haServerArgs.get("proxynames").split(","));
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


    private void printStats(Map<String, String> haServerArgs, Map<Integer, String> proxiesToBeMonitored, Map<Integer, String> proxyTypes) {

        List<String> excludedStats = new ArrayList<String>();
        if (haServerArgs.containsKey("excludeStats") && !Strings.isNullOrEmpty(haServerArgs.get("excludeStats"))) {
            excludedStats = Arrays.asList(haServerArgs.get("excludeStats").split(","));
        }

        // Prints metrics to Controller Metric Browser
        if (metricPrefix == null) {
            metricPrefix = haServerArgs.get(TaskInputArgs.METRIC_PREFIX) + METRIC_SEPARATOR;
        }
        final int statusColIndex = getColumnFromMetricKey("status");
        final int check_statusColIndex = getColumnFromMetricKey("check_status");
        MetricConfig[] metricConfigs = ((Stat.Stats) configuration.getMetricsXml()).getStats().getMetricConfig();
        for (Map.Entry<Integer, String> proxy : proxiesToBeMonitored.entrySet()) {
            String healthCheckStatus = getHealthCheckStatus(proxy.getKey(), check_statusColIndex);
            for (MetricConfig config : metricConfigs) {
                if (!excludedStats.contains(config.getAttr())) {
                    if (config.getAttr().equals("status")) {
                        Metric metric = new Metric("status", getStatus(proxy.getKey(), statusColIndex), metricPrefix + METRIC_SEPARATOR + proxy.getValue() + METRIC_SEPARATOR + proxyTypes.get(proxy.getKey()) + METRIC_SEPARATOR + "status");
                        metrics.add(metric);
                    } else if (config.getAttr().equals("check_status") && !"".equals(healthCheckStatus)) {
                        Metric metric = new Metric("check_status", healthCheckStatus, metricPrefix + METRIC_SEPARATOR + proxy.getValue() + METRIC_SEPARATOR + proxyTypes.get(proxy.getKey()) + METRIC_SEPARATOR + "check_status");
                        metrics.add(metric);
                    }
                    else
                    printMetric(proxy, proxyTypes, config);
                }

            }
        }
        if (metrics != null && metrics.size() > 0) {
            metricWriter.transformAndPrintMetrics(metrics);
        }
    }

    private void printMetric(Map.Entry<Integer, String> proxy, Map<Integer, String> proxyTypes, MetricConfig config) {
        int column = config.getColumn();
        if (column == -1) return;
        String cellContent = getCellContents(column, proxy.getKey());
        if (!cellContent.equals("")) {
            Metric metric = new Metric(config.getAlias(), cellContent, metricPrefix + METRIC_SEPARATOR + proxy.getValue()
                    + METRIC_SEPARATOR + proxyTypes.get(proxy.getKey()) + METRIC_SEPARATOR + config.getAlias());
            metrics.add(metric);
        }
    }

    private int getColumnFromMetricKey(String metricKey) {
        MetricConfig[] metricConfigs = ((Stat.Stats) configuration.getMetricsXml()).getStats().getMetricConfig();
        int column = 0;
        for (MetricConfig config : metricConfigs) {
            if (config.getAttr().equals(metricKey))
                column = config.getColumn();
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

    protected List<Metric> getMetrics() {
        return metrics;
    }

}
