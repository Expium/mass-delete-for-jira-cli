package com.expium.massdelete.ui;

import com.atlassian.jira.rest.client.api.RestClientException;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.expium.massdelete.BuildProperties;
import com.expium.massdelete.Options;
import com.expium.massdelete.StopReason;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;

/**
 * Copyright 2015-2016 Expium LLC
 * http://expium.com/
 */
public class UI {
    private final Logger logger = LoggerFactory.getLogger(UI.class);
    private final Logger fileLogger = LoggerFactory.getLogger("FILE_ONLY");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("M/d/yy hh:mm aaa");

    private boolean connectivityVerified = false;
    private boolean verbose;
    private int batchCounter;

    public void showWelcome(BuildProperties buildProperties) {
        String version = buildProperties.getVersion();
        if (version == null) {
            version = "";
        } else {
            version = " " + version;
        }

        System.out.println("");
        System.out.println("Welcome to Expium Mass Delete" + version + " for JIRA.");
        System.out.println("");
        System.out.println("Copyright 2015-2016 Expium LLC");
        System.out.println("http://expium.com/");
        System.out.println("");
    }

    public Options getOptions(String[] args) {
        Options options = new Options();
        CmdLineParser parser = new CmdLineParser(options);

        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            printUsage();
            System.exit(1);
        }
        if (options.help) {
            printUsage();
            System.exit(0);
        }

        this.verbose = options.verbose;

        System.out.println("This program may be aborted at any time by pressing Ctrl+C, closing the");
        System.out.println("terminal or terminating the process from task manager.");
        System.out.println();
        System.out.println("It is necessary to leave this window open as long as the removal is in");
        System.out.println("progress. Closing it will terminate the process.");
        System.out.println();
        System.out.println("If an error occurs or the job is terminated in some other way, you can");
        System.out.println("just run it again to retry/resume.");
        System.out.println();

        Console console = System.console();
        if (console == null) {
            // Running in IDE
            try {
                System.out.print("JIRA password: ");
                options.password = new BufferedReader(new InputStreamReader(System.in)).readLine();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            options.password = new String(System.console().readPassword("JIRA password: "));
        }
        System.out.println();
        return options;
    }

    private static void printUsage() {
        // Use a new instance, otherwise it messes up defaults
        new CmdLineParser(new Options()).printUsage(System.err);
        System.err.println();
    }

    public void interrupted() {
        logger.error("Interrupted, aborting");
        System.exit(-1);
    }

    public void jiraRequestFailed(String operation, Throwable e) {
        if (!verbose) {
            // Flush the line, so the log entry appears on a line of its own, not mixed up with the dots.
            System.out.println();
        }

        String msg = "Unable to " + operation;

        if (e instanceof RestClientException) {
            RestClientException re = (RestClientException) e;
            int status = re.getStatusCode().or(-1);
            if (status == 401) {
                msg += " - unauthorized. ";
                if (!connectivityVerified) {
                    msg += " Please verify the password.";
                }
                logger.error(msg);
                return;
            } else if (status == 403) {
                msg += " - forbidden.";
                if (!connectivityVerified) {
                    msg += " Please verify the password. If this error persists, it may be necessary to open JIRA in browser, log out and log back in.";
                }
                logger.error(msg);
                return;
            } else if (status == 404) {
                msg += " - not found.";
                if (!connectivityVerified) {
                    msg += " Please check the URL.";
                }
                logger.error(msg);
                return;
            } else if (status >= 500) {
                logger.error(msg + " - JIRA server error, see log for detail");
                fileLogger.error("Error detail", e);
                return;
            }
        } else if (isTimeout(e)) {
            logger.error(msg + " - request timed out");
            return;
        }

        logger.error(msg + ", see log for detail.");
        fileLogger.error("Error detail", e);
    }

    public boolean confirmRemoval(int total) {
        boolean confirmed;
        do {
            System.out.println();
            System.out.println("The program is going to permanently delete " + total + " issues from JIRA.");
            System.out.println("* Type " + total + " and press \"Enter\" to begin the removal process.");
            System.out.println("* Type \"exit\" and press \"Enter\" to abort.");
            System.out.println();
            String ln = new Scanner(System.in).nextLine();
            if (ln.trim().equals("exit")) {
                return false;
            }
            confirmed = ln.trim().equals(String.valueOf(total));
        } while (!confirmed);
        return true;
    }

    public void estimateAfterBatchCompletion(long batchTime, int queryBatchSize, Date estimatedCompletion) {
        logger.info("Took {} ms to process {} issues. Estimated finish time at the current rate: {}.",
                batchTime, queryBatchSize, dateFormat.format(estimatedCompletion));
    }

    public void batchStarting() {
        batchCounter = 0;
    }

    public void removing(Issue issue) {
        batchCounter++;
        if (verbose) {
            logger.debug("Removing " + issue.getKey() + " -- " + issue.getSummary());
        } else {
            // Provide some feedback
            System.out.print(".");
            if ((batchCounter % 78) == 0) {
                System.out.println();
            }
        }
    }

    public void removalFailed() {
        batchCounter = 0; // Start from full line
    }

    public void batchCompleted() {
        if (!verbose) {
            System.out.println();
        }
    }

    public void progress(int removed, int skipped, int remaining) {
        logger.info("{} issues removed, {} skipped, {} remaining", removed, skipped, remaining);
    }

    public void sessionStopped(int removed, int skipped, int remaining, long sessionDuration) {
        logger.info("{} removed, {} skipped, {} unprocessed in {}", removed, skipped, remaining,
                String.format("%d:%02d:%02d", sessionDuration / 3600, (sessionDuration % 3600) / 60,
                        (sessionDuration % 60)));
    }

    public void info(String msg, Object... args) {
        logger.info(msg, args);
    }

    public void warn(String msg, Object... args) {
        logger.warn(msg, args);
    }

    public void error(String msg) {
        logger.error(msg);
    }

    public void error(String msg, Exception e) {
        logger.error(msg);
        fileLogger.error("Error detail", e);
    }

    public static boolean isTimeout(Throwable e) {
        return Iterables
                .any(Throwables.getCausalChain(e), Predicates.instanceOf(SocketTimeoutException.class));
    }

    public void connectivityVerified() {
        this.connectivityVerified = true;
    }

    public void stopped(StopReason reason) {
        switch (reason) {
        case ERROR:
            System.exit(-1);
        case NO_CONNECTION_TO_JIRA:
            System.exit(1);
        case FILTER_NOT_FOUND:
            System.exit(2);
        case ISSUE_SEARCH_FAILED:
            System.exit(3);
        case USER_DID_NOT_CONFIRM_REMOVAL:
        case NO_MATCHING_ISSUES:
        case COMPLETED:
        case NOTHING_REMOVED_IN_BATCH:
            System.exit(0);
        }
    }

}
