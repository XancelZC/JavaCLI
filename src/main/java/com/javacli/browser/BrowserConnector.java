package com.javacli.browser;

public interface BrowserConnector {
    String status();

    String connectDefault();

    String disconnect();
}
