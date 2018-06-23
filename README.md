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
  ```
  	<argument name="host" is-required="true" default-value="demo.1wt.eu"/>
        <argument name="port" is-required="true" default-value="80"/>

        <!--URI of the haproxy CSV stats url. See the 'CSV Export' link on your haproxy stats page -->
        <argument name="csv-export-uri" is-required="true" default-value=";csv"/>

        <argument name="username" is-required="false" default-value=""/>
        <argument name="password" is-required="false" default-value=""/>
        <!--If the haproxy stats URI is SSL enabled, ie; HTTPS, use the below option -->
        <argument name="use-ssl" is-required="false" default-value="true"/>

        <!--proxy names you wish to monitor as a comma separated values. If empty all the proxies are monitored -->
        <argument name="proxynames" is-required="false" default-value=""/>
        <!--HA Proxy stats as a comma separated values to be excluded from monitoring -->
        <argument name="excludeStats" is-required="false" default-value="pid,iid,sid"/>
        <!--Please ensure the Component ID is set
        <argument name="metric-prefix" is-required="false" default-value="Server|Component:<<component_id>>|Custom Metrics|HAProxy|"/>
  ```
     

5. Restart the Machine Agent. 
 
In the AppDynamics Metric Browser, look for: Application Infrastructure Performance  | \<Tier\> | Custom Metrics | HAProxy

##Password Encryption Support

To avoid setting the clear text password in the monitor.xml. Please follow the process to encrypt the password and set the encrypted password and the key in the monitor.xml

1. Download the util jar to encrypt the password from https://github.com/Appdynamics/maven-repo/raw/master/releases/com/appdynamics/appd-exts-commons/1.1.2/appd-exts-commons-1.1.2.jar 
2. Encrypt password from the commandline 
java -cp "appd-exts-commons-1.1.2.jar" com.appdynamics.extensions.crypto.Encryptor myKey myPassword 
3. Add the following properties in the monitor.xml substituting the default password argument.
<pre>
&lt;argument name="password-encrypted" is-required="true" default-value="&lt;ENCRYPTED_PASSWORD&gt;"/&gt;
&lt;argument name="encryption-key" is-required="true" default-value="myKey"/&gt;
</pre>

## Metrics

All the HA proxy metrics are shown. For the complete list of metrics please visit http://cbonte.github.io/haproxy-dconv/configuration-1.5.html#9

## Custom Dashboard
![](https://github.com/Appdynamics/haproxy-monitoring-extension/raw/master/HAProxyCustomDashboard.png)

##Contributing

Always feel free to fork and contribute any changes directly here on GitHub.

##Community

Find out more in the [AppSphere](http://community.appdynamics.com/t5/eXchange-Community-AppDynamics/HA-Proxy-Monitoring-Extension/idi-p/6143) community.

##Support

For any questions or feature request, please contact [AppDynamics Support](mailto:help@appdynamics.com).


