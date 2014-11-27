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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import jxl.Sheet;
import jxl.Workbook;
import jxl.write.Label;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;

import org.apache.log4j.Logger;

import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import com.singularity.ee.agent.systemagent.api.MetricWriter;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.TaskOutput;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import com.singularity.ee.util.httpclient.HttpClientWrapper;
import com.singularity.ee.util.httpclient.HttpExecutionRequest;
import com.singularity.ee.util.httpclient.HttpExecutionResponse;
import com.singularity.ee.util.httpclient.HttpOperation;
import com.singularity.ee.util.httpclient.IHttpClientWrapper;
import com.singularity.ee.util.log4j.Log4JLogger;

public class HAProxyMonitor extends AManagedMonitor {

	private static Logger logger = Logger.getLogger(HAProxyMonitor.class);
	private static final String metricPathPrefix = "Custom Metrics|HAProxy|";

	private WritableWorkbook workbook;

	private Map<String, Integer> dictionary;

	private String responseString;

	private List<String> proxiesListedInConfigFile = new ArrayList<String>();
	
	public HAProxyMonitor() {
		
		String msg = "Using Monitor Version [" + getImplementationVersion() + "]";
		logger.info(msg);
		System.out.println(msg);
		
		dictionary = new HashMap<String, Integer>();
		dictionary.put("# pxname", 0);
		dictionary.put("svname", 1);
		dictionary.put("qcur", 2);
		dictionary.put("scur", 4);
		dictionary.put("stot", 7);
		dictionary.put("bin", 8);
		dictionary.put("bout", 9);
		dictionary.put("ereq", 12);
		dictionary.put("econ", 13);
		dictionary.put("eresp", 14);
		dictionary.put("status", 17);
		dictionary.put("act", 19);
		dictionary.put("bck", 20);
		dictionary.put("lbtot", 30);
	}

	/*
	 * Main execution method that uploads the metrics to AppDynamics Controller
	 * 
	 * @see
	 * com.singularity.ee.agent.systemagent.api.ITask#execute(java.util.Map,
	 * com.singularity.ee.agent.systemagent.api.TaskExecutionContext)
	 */
	public TaskOutput execute(Map<String, String> taskArguments, TaskExecutionContext arg1) throws TaskExecutionException {
		try {
			// connect to url with the arguments provided and get response string
			connect(taskArguments);
			// reads the csv output and writes the response to a spreadsheet
			// which is used inturn to get the metrics
			writeResponseToWorkbook(responseString);

			Map<Integer, String> proxiesToBeMonitored = getAllProxyAndTypes(dictionary.get("# pxname"));
			Map<Integer, String> proxyTypes = getAllProxyAndTypes(dictionary.get("svname"));

			if (proxiesListedInConfigFile.size() != 0) {
				for (Iterator<Map.Entry<Integer, String>> it = proxiesToBeMonitored.entrySet().iterator(); it.hasNext();) {
					Map.Entry<Integer, String> entry = it.next();
					if (!proxiesListedInConfigFile.contains(entry.getValue())) {
						it.remove();
					}
				}
			}
			// Prints metrics to Controller Metric Browser
			for (Map.Entry<Integer, String> proxy : proxiesToBeMonitored.entrySet()) {
				printMetric(getMetricPrefix() + proxy.getValue() + "|" + proxyTypes.get(proxy.getKey()) + "|", "status", getStatus(proxy.getKey()),
						MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE, MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE,
						MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);

				printMetric(proxy, proxyTypes, "qcur", "queued_requests");
				printMetric(proxy, proxyTypes, "scur", "current sessions");
				printMetric(proxy, proxyTypes, "stot", "total sessions");
				printMetric(proxy, proxyTypes, "bin", "bytes in");
				printMetric(proxy, proxyTypes, "bout", "bytes out");
				printMetric(proxy, proxyTypes, "ereq", "error requests");
				printMetric(proxy, proxyTypes, "econ", "connection errors");
				printMetric(proxy, proxyTypes, "eresp", "response errors");
				printMetric(proxy, proxyTypes, "act", "active servers");
				printMetric(proxy, proxyTypes, "bck", "backup servers");
				printMetric(proxy, proxyTypes, "lbtot", "lbtot");
			}

			return new TaskOutput("HAProxy Metric Upload Complete");
		} catch (Exception e) {
			logger.error("HAProxy Metric upload failed");
			return new TaskOutput("HAProxy Metric upload failed");
		}
	}

	/**
	 * Validates the task arguments passed and connects to HAProxy CSV page.
	 * 
	 * @param taskArguments
	 */
	private void connect(Map<String, String> taskArguments) {
		if (!taskArguments.containsKey("url") || !taskArguments.containsKey("username") || !taskArguments.containsKey("password")) {
			logger.error("Monitor.xml needs to contain all required task arguments");
			throw new RuntimeException("Monitor.xml needs to contain all required task arguments");
		}
		String url = taskArguments.get("url");
		URL aurl;
		try {
			aurl = new URL(url);
			String host = aurl.getHost();
			String userName = taskArguments.get("username");
			String password = taskArguments.get("password");

			if (url != null && url != "") {
				responseString = getResponseString(url, host, userName, password);
			}
			logger.info("Connected to " + url);
		} catch (MalformedURLException e) {
			logger.error("URL null or empty in monitor.xml");
			throw new RuntimeException("URL null or empty in monitor.xml");
		}

		if (taskArguments.containsKey("proxynames") && null != taskArguments.get("proxynames") && !taskArguments.get("proxynames").equals("")) {
			proxiesListedInConfigFile = Arrays.asList(taskArguments.get("proxynames").split(","));
		}
	}

	/**
	 * Returns http response as a csv string
	 * 
	 * @param url
	 * @param host
	 * @param username
	 * @param password
	 * @return
	 */
	private String getResponseString(String url, String host, String username, String password) {
		IHttpClientWrapper httpClient = HttpClientWrapper.getInstance();
		HttpExecutionRequest request = new HttpExecutionRequest(url, "", HttpOperation.GET);
		httpClient.authenticateHost(host, 80, "", username, password, true);
		HttpExecutionResponse response = httpClient.executeHttpOperation(request, new Log4JLogger(logger));

		responseString = response.getResponseBody();
		return responseString;
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
		} catch (Exception e) {
			logger.error("Error while writing response to workbook stream");
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
			printMetric(getMetricPrefix() + proxy.getValue() + "|" + proxyTypes.get(proxy.getKey()) + "|", metricName,
					getCellContents(dictionary.get(metricKey), proxy.getKey()), MetricWriter.METRIC_AGGREGATION_TYPE_AVERAGE,
					MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE, MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
		}
	}

	private void printMetric(String metricPath, String metricName, Object metricValue, String aggregation, String timeRollup, String cluster) {
		MetricWriter metricWriter = super.getMetricWriter(metricPath + metricName, aggregation, timeRollup, cluster);
		metricWriter.printMetric(String.valueOf(metricValue));
	}

	private String getMetricPrefix() {
		return metricPathPrefix;
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

	public static void main(String[] args) throws Exception {

		HAProxyMonitor monitor = new HAProxyMonitor();

		String responseString = monitor.getResponseString("http://demo.1wt.eu/;csv", "demo.1wt.eu", "", "");

		System.out.println(responseString);
	}
}