package com.appdynamics.extensions.haproxy;

import com.google.common.collect.Maps;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.junit.Test;

import java.util.Map;

public class HAProxyMonitorTest {

    @Test
    public void main() throws TaskExecutionException {

        HAProxyMonitor monitor = new HAProxyMonitor();
        Map<String, String> taskArgs = Maps.newHashMap();
        taskArgs.put("config-file", "src/test/resources/conf/test-config.yml");
        taskArgs.put("metric-file", "src/test/resources/conf/test-metrics.xml");
        try {
            monitor.execute(taskArgs, null);
        } catch (TaskExecutionException e) {
            e.printStackTrace();
        }
    }
}