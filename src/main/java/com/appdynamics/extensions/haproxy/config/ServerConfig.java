package com.appdynamics.extensions.haproxy.config;

public class ServerConfig {
    public ServerConfig(String pxname, String svname){
        this.pxname = pxname;
        this.svname = svname;
    }
    private String pxname;
    private String svname;

    public String getPxname() {
        return pxname;
    }

    public String getSvname() {
        return svname;
    }
}
