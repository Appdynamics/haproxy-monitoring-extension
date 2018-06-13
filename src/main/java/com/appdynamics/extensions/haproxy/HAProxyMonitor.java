/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 *  * All Rights Reserved.
 *  * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 *  * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.haproxy;

import com.appdynamics.extensions.ABaseMonitor;
import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.haproxy.config.ProxyStats;
import com.appdynamics.extensions.util.AssertUtils;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


/**
 * The {@code HAProxyMonitor} class extends {@code ABaseMonitor} Abstract class.
 * It reads the {@code config.yml} into configYml and submits Task to
 * {@code HAProxyMonitorTask} for each server monitoring.
 *
 * @author Balakrishna V, Prashant M
 * @since appd-exts-commons:2.1.0
 */
public class HAProxyMonitor extends ABaseMonitor {

    private static Logger logger = Logger.getLogger(HAProxyMonitor.class);
    private Map<String, ?> configYml;

    @Override
    protected String getDefaultMetricPrefix() {
        return Constant.METRIC_PREFIX;
    }

    @Override
    public String getMonitorName() {
        return "HaProxy Monitor";
    }

    /**
     * Submits tasks to {@code TasksExecutionServiceProvider}
     *
     * @param tasksExecutionServiceProvider
     */
    @Override
    protected void doRun(TasksExecutionServiceProvider tasksExecutionServiceProvider) {
        try {
            configYml = this.getContextConfiguration().getConfigYml();

            AssertUtils.assertNotNull(configYml, "The config.yml is not available");
            AssertUtils.assertNotNull(this.getContextConfiguration().getMetricsXml(), "Metrics xml not available");

            int serversCount = ((List) configYml.get("servers")).size();
            if (serversCount == 0) {
                logger.debug("The server section in test-config.yml is not initialised");
            }
            logger.info("Starting the HAProxy Monitoring Task");

            for (int i = 0; i < serversCount; i++) {
                Map<String, String> serverArgs = new HashMap<>();
                Map<String, ?> server = (Map<String, ?>) ((List) configYml.get("servers")).get(i);
                for (Entry<String, ?> subServerEntry : server.entrySet()) {
                    if (subServerEntry.getValue() instanceof List) {
                        String str = "";
                        Iterator itr = ((List) subServerEntry.getValue()).iterator();
                        while (itr.hasNext()) {
                            str += (itr.next().toString() + ',');
                        }
                        serverArgs.put(subServerEntry.getKey(), str);
                    } else {
                        serverArgs.put(subServerEntry.getKey(), (subServerEntry.getValue()).toString());
                    }
                }
                AssertUtils.assertNotNull(serverArgs, "the server arguements are empty");
                HAProxyMonitorTask haProxyMonitorTask = new HAProxyMonitorTask(this.getContextConfiguration(), tasksExecutionServiceProvider.getMetricWriteHelper(), serverArgs);
                tasksExecutionServiceProvider.submit(serverArgs.get("displayName"), haProxyMonitorTask);
            }
        } catch (Exception e) {
            logger.error("HAProxy Metrics collection failed", e);
        }

    }

    /**
     * An Overridden method which gets called to set metrics-xml into the {@code MonitorContextConfiguration}
     *
     * @param args
     */
    @Override
    protected void initializeMoreStuff(Map<String, String> args) {
        logger.info("initializing metric.xml file");
        this.getContextConfiguration().setMetricXml(args.get("metric-file"), ProxyStats.class);
    }

    @Override
    protected int getTaskCount() {
        List<Map<String, String>> servers = (List<Map<String, String>>) getContextConfiguration().getConfigYml().get("servers");
        AssertUtils.assertNotNull(servers, "The 'servers' section in test-config.yml is not initialised");
        return servers.size();
    }

    public static void main(String[] args) throws TaskExecutionException {

        ConsoleAppender ca = new ConsoleAppender();
        ca.setWriter(new OutputStreamWriter(System.out));
        ca.setLayout(new PatternLayout("%-5p [%t]: %m%n"));
        ca.setThreshold(Level.DEBUG);

        logger.getRootLogger().addAppender(ca);

        HAProxyMonitor monitor = new HAProxyMonitor();

        final Map<String, String> taskArgs = new HashMap<String, String>();
        taskArgs.put("config-file", "//Users/prashant.mehta/dev/haproxy-monitoring-extension/src/main/resources/conf/config.yml");
        taskArgs.put("metric-file", "/Users/prashant.mehta/dev/haproxy-monitoring-extension/src/main/resources/conf/metrics.xml");
        monitor.execute(taskArgs, null);
    }
}