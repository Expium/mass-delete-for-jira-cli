package com.expium.massdelete;

import com.expium.massdelete.remover.Remover;
import com.expium.massdelete.ui.UI;

import java.util.Properties;

/**
 * Copyright 2015-2016 Expium LLC
 * http://expium.com/
 */
public class Main {
    public static void main(String[] args) {
        UI ui = new UI();
        ui.showWelcome(readProperties());
        Options options = ui.getOptions(args);
        LoggerConfiguration.configure(options.verbose);
        new Remover(ui, options).go();
    }

    private static BuildProperties readProperties() {
        Properties properties = new Properties();
        try {
            properties.load(Main.class.getResourceAsStream("/build.properties"));
        } catch (Exception e) {
        }
        return new BuildProperties(properties);
    }
}
