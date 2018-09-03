package com.appdynamics.extensions.haproxy.config;


public class ProxyServerConfig {
    private ServerConfig[] serverConfigs;

    public void setServerConfigs(ServerConfig[] serverConfigs) {
        this.serverConfigs = serverConfigs;
    }

    public ServerConfig[] getServerConfigs() {
        return serverConfigs;
    }
}