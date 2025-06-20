package com.udp.server.model;

import java.util.Map;

public class UdpHttpRequest {
    String method;
    String url;
    Map<String, String> headers;
    String body;
}
