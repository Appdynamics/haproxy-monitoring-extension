package com.appdynamics.extensions.haproxy.config;


import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class ProxyServerConfig {
    @XmlAttribute
    private String name;
    @XmlAttribute
    public String children;
    @XmlElement(name = "servers")
    private ServerConfig[] serverConfigs;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setChildren(String children) {
        this.children = children;
    }

    public String getChildren() {
        return children;
    }

    public void setServerConfigs(ServerConfig[] serverConfigs) {
        this.serverConfigs = serverConfigs;
    }

    public ServerConfig[] getServerConfigs() {
        return serverConfigs;
    }
}

