# AppDynamics HAProxy Monitoring Extension

This extension works only with the standalone machine agent.

##Use Case

HAProxy is an open source, high performance TCP/HTTP Load Balancer commonly used to improve the performance of web sites and services by spreading requests across multiple servers. 
The HAProxy Monitoring extension collects key metrics from HAProxy Load balancer of the underlying proxies/servers and presents them to the AppDynamics Metric Browser. 

Notes: Works with HAProxy v 1.3 and above.

##Installation

1. Run 'ant package' from the haproxy-monitoring-extension directory
2. Download the file HAProxyMonitor.zip located in the 'dist' directory into \<machineagent install dir\>/monitors/
3. Unzip the downloaded file
4. In \<machineagent install dir\>/monitors/HAProxyMonitor/, open monitor.xml and configure the HAProxy parameters.
     <pre>
     URI of the haproxy CSV stats. See the 'CSV Export' link on your haproxy stats page
     &lt;argument name="url" is-required="true" default-value="http://demo.1wt.eu/;csv" /&gt;
     &lt;argument name="username" is-required="false" default-value="" /&gt;
     &lt;argument name="password" is-required="false" default-value="" /&gt;
     proxy names you wish to monitor as a comma separated values. If empty all the proxies are monitored.
     &lt;argument name="proxynames" is-required="false" default-value="" /&gt;
     </pre>
5. Restart the Machine Agent. 
 
In the AppDynamics Metric Browser, look for: Application Infrastructure Performance  | \<Tier\> | Custom Metrics | HAProxy

##Directory Structure

<table><tbody>
<tr>
<th align="left"> File/Folder </th>
<th align="left"> Description </th>
</tr>
<tr>
<td class='confluenceTd'> conf </td>
<td class='confluenceTd'> Contains the monitor.xml </td>
</tr>
<tr>
<td class='confluenceTd'> lib </td>
<td class='confluenceTd'> Contains third-party project references </td>
</tr>
<tr>
<td class='confluenceTd'> src </td>
<td class='confluenceTd'> Contains source code to the HAProxy Monitoring Extension </td>
</tr>
<tr>
<td class='confluenceTd'> dist </td>
<td class='confluenceTd'> Only obtained when using ant. Run 'ant build' to get binaries. Run 'ant package' to get the distributable .zip file </td>
</tr>
<tr>
<td class='confluenceTd'> build.xml </td>
<td class='confluenceTd'> Ant build script to package the project (required only if changing Java code) </td>
</tr>
</tbody>
</table>

## Metrics

| Metric Name | Description |
|----------------|-------------|
|status				| 1 (UP/OPEN) 0 (DOWN)|
|qcur				| current queued requests|
|scur				| current sessions|
|stot				| total sessions|
|bin			         	| Bytes In	|
|bout				| Bytes Out|
|ereq				| error requests|
|eresp				| response errors|
|econ				| connection errors|
|act			     	| server is active (server), number of active servers (backend)|
|bck			     	| server is backup (server), number of backup servers (backend)|

## Custom Dashboard
![](https://github.com/Appdynamics/haproxy-monitoring-extension/raw/master/HAProxyCustomDashboard.png)

##Contributing

Always feel free to fork and contribute any changes directly here on GitHub.

##Community

Find out more in the [AppSphere]() community.

##Support

For any questions or feature request, please contact [AppDynamics Center of Excellence](mailto:ace-request@appdynamics.com).


