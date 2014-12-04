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

public class HAProxyMonitor extends AManagedMonitor {

	private static final String CSV_EXPORT_URI = "csv-export-uri";
	private static final String METRIC_SEPARATOR = "|";
	private static Logger logger = Logger.getLogger(HAProxyMonitor.class);
	private static String metricPrefix;
	private WritableWorkbook workbook;
	private Map<String, Integer> dictionary;

	private static final Map<String, String> DEFAULT_ARGS = new HashMap<String, String>() {
		{
			put(TaskInputArgs.METRIC_PREFIX, "Custom Metrics|HAProxy");
		}
	};

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
			for (Iterator<Map.Entry<Integer, String>> it = allProxies.entrySet().iterator(); it.hasNext();) {
				Map.Entry<Integer, String> entry = it.next();
				if (!proxiesListedInConfigFile.contains(entry.getValue())) {
					it.remove();
				}
			}
		}
		return allProxies;
	}

	private void printStats(Map<String, String> taskArguments, Map<Integer, String> proxiesToBeMonitored, Map<Integer, String> proxyTypes) {
		// Prints metrics to Controller Metric Browser
		metricPrefix = taskArguments.get(TaskInputArgs.METRIC_PREFIX) + METRIC_SEPARATOR;
		for (Map.Entry<Integer, String> proxy : proxiesToBeMonitored.entrySet()) {
			printMetric(proxy.getValue() + METRIC_SEPARATOR + proxyTypes.get(proxy.getKey()) + METRIC_SEPARATOR, "status", getStatus(proxy.getKey()));

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