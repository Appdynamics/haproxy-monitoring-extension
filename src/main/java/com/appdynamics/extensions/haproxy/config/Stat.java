package com.appdynamics.extensions.haproxy.config;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class Stat {
    @XmlAttribute
    private String name;
    @XmlAttribute
    public String children;
    @XmlElement(name = "metric")
    private MetricConfig[] metricConfigs;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getChildren() {
        return children;
    }

    public void setChildren(String children) {
        this.children = children;
    }

    public MetricConfig[] getMetricConfig() {
        return metricConfigs;
    }

    public void setMetricConfig(MetricConfig[] metricConfigs) {
        this.metricConfigs = metricConfigs;
    }
}
