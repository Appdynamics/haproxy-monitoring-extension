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

    public Stat getStat() {
        return stat;
    }

    public void setStat() {
        this.stat = stat;
    }

}
