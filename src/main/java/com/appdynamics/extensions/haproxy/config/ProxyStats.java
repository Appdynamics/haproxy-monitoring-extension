package com.appdynamics.extensions.haproxy.config;


import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "proxy-stats")
@XmlAccessorType(XmlAccessType.FIELD)
public class ProxyStats {
    @XmlElement(name = "stat")
    private Stat stat;
    @XmlElement(name = "proxy-servers")
    private ProxyServerConfig proxyServerConfig;

    public Stat getStat() {
        return stat;
    }

    public void setStat() {
        this.stat = stat;
    }

    public ProxyServerConfig getProxyServerConfig() {
        return proxyServerConfig;
    }

    public void setProxyServerConfig(ProxyServerConfig proxyServerConfig) {
        this.proxyServerConfig = proxyServerConfig;
    }

}
