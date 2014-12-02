# AppDynamics HAProxy Monitoring Extension

This extension works only with the standalone machine agent.

##Use Case

HAProxy is an open source, high performance TCP/HTTP Load Balancer commonly used to improve the performance of web sites and services by spreading requests across multiple servers. 
The HAProxy Monitoring extension collects key metrics from HAProxy Load balancer of the underlying proxies/servers and presents them to the AppDynamics Metric Browser. 

Notes: Works with HAProxy v 1.3 and above.

##Installation

1. Run `mvn clean install` from the haproxy-monitoring-extension directory and find the HAProxyMonitor.zip in the "target" folder.
2. Unzip as "HAProxyMonitor" and copy the "HAProxyMonitor" directory to `<MACHINE_AGENT_HOME>/monitors`
4. In `<MACHINE_AGENT_HOME>/monitors/HAProxyMonitor/`, open monitor.xml and configure the HAProxy parameters.
     <pre>
	 &lt;argument name="host" is-required="true" default-value="demo.1wt.eu" /&gt;
     &lt;argument name="port" is-required="true" default-value="80" /&gt;
                       
     URI of the haproxy CSV stats. See the 'CSV Export' link on your haproxy stats page
     &lt;argument name="csv-export-uri" is-required="true" default-value=";csv" /&gt;
     &lt;argument name="username" is-required="false" default-value="" /&gt;
     &lt;argument name="password" is-required="false" default-value="" /&gt;
     proxy names you wish to monitor as a comma separated values. If empty, all the proxies are monitored.
     &lt;argument name="proxynames" is-required="false" default-value="" /&gt;
     &lt;argument name="metric-prefix" is-required="false" default-value="Custom Metrics|HAProxy|" /&gt;
     </pre>
5. Restart the Machine Agent. 
 
In the AppDynamics Metric Browser, look for: Application Infrastructure Performance  | \<Tier\> | Custom Metrics | HAProxy

##Password Encryption Support

To avoid setting the clear text password in the monitor.xml. Please follow the process to encrypt the password and set the encrypted password and the key in the monitor.xml

1. Download the util jar to encrypt the password from https://github.com/Appdynamics/maven-repo/raw/master/releases/com/appdynamics/appd-exts-commons/1.1.2/appd-exts-commons-1.1.2.jar 
2. Encrypt password from the commandline 
java -cp "appd-exts-commons-1.1.2.jar" com.appdynamics.extensions.crypto.Encryptor myKey myPassword 
3. Add the following properties in the monitor.xml substituting the default password argument.
<pre>
&lt;argument name="password-encrypted" is-required="true" default-value="<ENCRYPTED_PASSWORD>"/&gt;
&lt;argument name="encryption-key" is-required="false" default-value="myKey"/&gt;
</pre>

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

Find out more in the [AppSphere](http://community.appdynamics.com/t5/eXchange-Community-AppDynamics/HA-Proxy-Monitoring-Extension/idi-p/6143) community.

##Support

For any questions or feature request, please contact [AppDynamics Support](mailto:help@appdynamics.com).


