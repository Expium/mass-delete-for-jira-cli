package com.expium.massdelete;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

/**
 * Copyright 2015-2016 Expium LLC
 * http://expium.com/
 */
public class LoggerConfiguration {
    public static void configure(boolean verbose) {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(verbose ? Level.DEBUG : Level.INFO);

        ((Logger) LoggerFactory.getLogger("com.atlassian.jira")).setLevel(Level.INFO);
    }
}
