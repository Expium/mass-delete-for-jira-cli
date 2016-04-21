package com.expium.massdelete;

import java.util.Properties;

/**
 * Copyright 2015-2016 Expium LLC
 * http://expium.com/
 */
public class BuildProperties {
    private final Properties properties;

    public BuildProperties(Properties properties) {
        this.properties = properties;
    }

    public String getVersion() {
        return (String) properties.get("version");
    }
}
