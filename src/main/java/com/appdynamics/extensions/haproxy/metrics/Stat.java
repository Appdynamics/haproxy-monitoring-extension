/*
 *
 *   Copyright 2018. AppDynamics LLC and its affiliates.
 *   All Rights Reserved.
 *   This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 *   The copyright notice above does not evidence any actual or intended publication of such source code.
 *
 */
package com.appdynamics.extensions.haproxy.metrics;

import com.appdynamics.extensions.haproxy.config.MetricConfig;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class Stat {
    @XmlAttribute
    private String url;
    @XmlAttribute
    private String name;
    @XmlAttribute
    public String children;
    @XmlElement(name = "metric")
    private MetricConfig[] metricConfig;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

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
        return metricConfig;
    }

    public void setMetricConfig(MetricConfig[] metricConfig) {
        this.metricConfig = metricConfig;
    }

    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Stats {
        @XmlElement(name = "stat")
        private Stat stat;

        public Stat getStat() {
            return stat;
        }

        public void setStat(Stat stats) {
            this.stat = stats;
        }
    }
}
