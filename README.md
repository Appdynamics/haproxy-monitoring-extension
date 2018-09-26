# AppDynamics HAProxy Monitoring Extension


## Use Case

HAProxy is an open source, high performance TCP/HTTP Load Balancer commonly used to improve the performance of web sites and services by spreading requests across multiple servers. The HAProxy Monitoring extension collects key metrics from HAProxy Load balancer of the underlying proxies/servers and presents them to the AppDynamics Metric Browser. 


## Prerequisites

HAProxy Monitoring Extension works with HAProxy v1.3 and above.

In order to use this extension, you do need a [Standalone JAVA Machine Agent](https://docs.appdynamics.com/display/PRO44/Standalone+Machine+Agents) or [SIM Agent](https://docs.appdynamics.com/display/PRO44/Server+Visibility).  For more details on downloading these products, please  visit [here](https://download.appdynamics.com/).
The extension needs to be able to connect to the HAProxy in order to collect and send metrics. To do this, you will have to either establish a remote connection in between the extension and the product, or have an agent on the same machine running the product in order for the extension to collect and send the metrics.
  
## Installation
1. Download and unzip the HAProxyMonitor-2.0.0.zip to the "<MachineAgent_Dir>/monitors" directory.
2. Edit the file config.yml as described below in Configuration Section, located in <MachineAgent_Dir>/monitors/HAProxyMonitor and update the server(s) details.
3. All metrics to be reported are configured in metrics.xml. Users can remove entries from metrics.xml to stop the metric from reporting, or add new entries as well.
4. Restart the Machine Agent.

Please place the extension in the **"monitors"** directory of your **Machine Agent** installation directory. Do not place the extension in the **"extensions"** directory of your **Machine Agent** installation directory.
In the AppDynamics Metric Browser, look for **Application Infrastructure Performance|\<Tier\>|Custom Metrics|HAProxy** and you should be able to see all the metrics.

## Configuration
### Config.yml

Configure the extension by editing the config.yml file in `<MACHINE_AGENT_HOME>/monitors/HAProxyMonitor/`.
  1. Configure the "COMPONENT_ID" under which the metrics need to be reported. This can be done by changing the value of `<COMPONENT_ID>` in   **metricPrefix: Server|Component:<COMPONENT_ID>|Custom Metrics|HAProxy|**.
       For example,
       ```
       metricPrefix: "Server|Component:100|Custom Metrics|HAProxy|"
       ```

  2. The extension supports reporting metrics from multiple HAProxy instances. The monitor provides an option to add HAProxy server/s for monitoring the metrics provided by the particular end-point. Have a look at config.yml for more details.
      For example:
      ```
      metricPrefix: "Server|Component:<COMPONENT_ID>|Custom Metrics|HAProxy|"
      servers:
        - displayName: "Local HAProxy"
          host: "demo.haproxy.org"
          port: 80
          csvExportUri: ";csv"
          username: ""
          password: ""
          encryptedPassword: ""
          useSsl: false
          #proxyServers can be configured as a list of (pxname-svname) values and are regex supported but do not put ".*" or blank inputs
          proxyServers:
              - pxname: "http.*"      #Put the indivudual pxname. Array of pxname not supported
                svname: ["FRONTEND", ".*-direct", "IPv4-.*"]
              - pxname: "www"
                svname: ["b.*"]
      connection:
        connectTimeout: 10000
        socketTimeout: 10000
      ```

  3. Configure the numberOfThreads.
     For example,
     If number of servers that need to be monitored is 5, then number of threads required is 5 * 1 = 5
     ```
     numberOfThreads: 5
     ```


### Metrics.xml

You can add/remove metrics of your choice by modifying the provided metrics.xml file. This file consists of all the metrics that will be monitored and sent to the controller. Please look how the metrics have been defined and follow the same convention, when adding new metrics. You do have the ability to chosoe your Rollup types as well as set an alias that you would like to be displayed on the metric browser.

   1. Proxy Servers Configuration
    Add the `pxname` Proxy Name and `svname` Service Name as under the servers tag as shown below.
```
            <proxy-servers name="proxy-servers">
                    <servers pxname="http-in" svname="FRONTEND" />
            </proxy-servers>
 ```

   2. Metric Stat Configuration
    Add the `metric` to be monitored under the metric tag as shown below.
```
         <stat name="metrics">
                <metric attr="qcur" alias="queued_requests" column="2" aggregationType = "OBSERVATION" timeRollUpType = "CURRENT" clusterRollUpType = "COLLECTIVE" />
          </stat>
 ```
For configuring the metrics, the following properties can be used:

 |     Property      |   Default value |         Possible values         |                                               Description                                                      |
 | ----------------- | --------------- | ------------------------------- | -------------------------------------------------------------------------------------------------------------- |
 | alias             | metric name     | Any string                      | The substitute name to be used in the metric browser instead of metric name.                                   |
 | aggregationType   | "AVERAGE"       | "AVERAGE", "SUM", "OBSERVATION" | [Aggregation qualifier](https://docs.appdynamics.com/display/PRO44/Build+a+Monitoring+Extension+Using+Java)    |
 | timeRollUpType    | "AVERAGE"       | "AVERAGE", "SUM", "CURRENT"     | [Time roll-up qualifier](https://docs.appdynamics.com/display/PRO44/Build+a+Monitoring+Extension+Using+Java)   |
 | clusterRollUpType | "INDIVIDUAL"    | "INDIVIDUAL", "COLLECTIVE"      | [Cluster roll-up qualifier](https://docs.appdynamics.com/display/PRO44/Build+a+Monitoring+Extension+Using+Java)|
 | multiplier        | 1               | Any number                      | Value with which the metric needs to be multiplied.                                                            |
 | convert           | null            | Any key value map               | Set of key value pairs that indicates the value to which the metrics need to be transformed. eg: UP:1, OPEN:1  |
 | delta             | false           | true, false                     | If enabled, gives the delta values of metrics instead of actual values.                                        |


 **All these metric properties are optional, and the default value shown in the table is applied to the metric (if a property has not been specified) by default.**



## Metrics
HA-proxy metrics and its description is shown [here](https://github.com/Appdynamics/haproxy-monitoring-extension/blob/master/Metrics-Details.md). For mored details, please visit [HAProxy Management Guide](https://www.haproxy.org/download/1.8/doc/management.txt).


## Credentials Encryption

Please visit [this page](https://community.appdynamics.com/t5/Knowledge-Base/How-to-use-Password-Encryption-with-Extensions/ta-p/29397) to get detailed instructions on password encryption. The steps in this document will guide you through the whole process.

## Extensions Workbench
Workbench is an inbuilt feature provided with each extension in order to assist you to fine tune the extension setup before you actually deploy it on the controller. Please review the following document on [How to use the Extensions WorkBench](https://community.appdynamics.com/t5/Knowledge-Base/How-to-use-the-Extensions-WorkBench/ta-p/30130).

## Troubleshooting
Please follow the steps listed in this [troubleshooting-document](https://community.appdynamics.com/t5/Knowledge-Base/How-to-troubleshoot-missing-custom-metrics-or-extensions-metrics/ta-p/28695) in order to troubleshoot your issue. These are a set of common issues that customers might have faced during the installation of the extension. If these don't solve your issue, please follow the last step on the [troubleshooting-document](https://community.appdynamics.com/t5/Knowledge-Base/How-to-troubleshoot-missing-custom-metrics-or-extensions-metrics/ta-p/28695) to contact the support team.


## Support Tickets
If after going through the [Troubleshooting Document](https://community.appdynamics.com/t5/Knowledge-Base/How-to-troubleshoot-missing-custom-metrics-or-extensions-metrics/ta-p/28695) you have not been able to get your extension working, please file a ticket and add the following information.

Please provide the following in order for us to assist you better.

    1. Stop the running machine agent.
    2. Delete all existing logs under <MachineAgent>/logs.
    3. Please enable debug logging by editing the file <MachineAgent>/conf/logging/log4j.xml. Change the level value of the following <logger> elements to debug.
        <logger name="com.singularity">
        <logger name="com.appdynamics">
    4. Start the machine agent and please let it run for 10 mins. Then zip and upload all the logs in the directory <MachineAgent>/logs/*.
    5. Attach the zipped <MachineAgent>/conf/* directory here.
    6. Attach the zipped <MachineAgent>/monitors/ExtensionFolderYouAreHavingIssuesWith directory here.

For any support related questions, you can also contact help@appdynamics.com.

## Contributing
Always feel free to fork and contribute any changes directly here on [GitHub](https://github.com/Appdynamics/haproxy-monitoring-extension).

## Version
|          Name            |  Version   |
|--------------------------|------------|
|Extension Version         |2.1.0       |
|Controller Compatibility  |3.7 or Later|
|Product Tested On         |1.7.5       |
|Last Update               |04/09/2018  |
|Changes list              |[ChangeLog](https://github.com/Appdynamics/haproxy-monitoring-extension/blob/master/CHANGELOG.md)|
