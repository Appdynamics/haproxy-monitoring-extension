package com.appdynamics.extensions.haproxy.config;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class ServerConfig {
    @XmlAttribute
    private String pxname;
    @XmlAttribute
    private String svname;

    public void setPxname(String pxname) {
        this.pxname = pxname;
    }

    public String getPxname() {
        return pxname;
    }

    public void setSvname(String svname) {
        this.svname = svname;
    }

    public String getSvname() {
        return svname;
    }
}
