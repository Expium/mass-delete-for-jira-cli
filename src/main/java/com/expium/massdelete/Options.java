package com.expium.massdelete;

import org.kohsuke.args4j.Option;

import java.net.URI;

/**
 * Copyright 2015-2016 Expium LLC
 * http://expium.com/
 */
public class Options {
    @Option(name = "-?", aliases = { "--help" }, usage = "Print this help", help = true)
    public boolean help;

    @Option(name = "-j", aliases = { "--url" }, usage = "Base JIRA URL", required = true)
    public URI url;

    @Option(name = "-u", aliases = { "--user" }, usage = "JIRA user name (login)", required = true)
    public String user;

    @Option(name = "-f", aliases = {
            "--filter" }, usage = "Name of filter in JIRA (must be in favorites)", required = true)
    public String filter;

    // Not a CLI option, we don't want password remembered in shell history or whatever
    public String password;

    @Option(name = "-v", aliases = { "--verbose" }, usage = "Enable verbose output")
    public boolean verbose;

    @Option(name = "-m", aliases = {
            "--max-per-second" }, usage = "Max issues to delete per second, may be fractional")
    public double maxIssuesPerSecond = 1_000;

    @Option(name = "-b", aliases = { "--batch-size" }, usage = "How many issues to query at a time")
    public int queryBatchSize = 100;

    @Option(name = "-s", aliases = {
            "--skip-errors" }, usage = "When set, the program will skip issues it was unable to delete and try to continue.")
    public boolean skipErrors;
}
