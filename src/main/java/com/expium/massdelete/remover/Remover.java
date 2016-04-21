package com.expium.massdelete.remover;

import com.atlassian.jira.rest.client.api.domain.Filter;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.expium.massdelete.Options;
import com.expium.massdelete.StopReason;
import com.expium.massdelete.ui.UI;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.RateLimiter;

import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Copyright 2015-2016 Expium LLC
 * http://expium.com/
 */
public class Remover {
    private final UI ui;
    private final Options options;
    private final RateLimiter rateLimiter;

    private final Thread LOG_SESSION_STATS = new Thread(new Runnable() {
        @Override
        public void run() {
            long sessionDuration = (System.currentTimeMillis() - sessionStart) / 1000;
            ui.sessionStopped(removed, skipped, remaining, sessionDuration);
        }
    });

    private Set<String> skippedKeys = new HashSet<>();

    private int skipped = 0;
    private int removed = 0;
    private int remaining;

    private long sessionStart;
    private long batchStart;

    public Remover(UI ui, Options options) {
        this.ui = ui;
        this.options = options;
        this.rateLimiter = RateLimiter.create(options.maxIssuesPerSecond);
    }

    public void go() {
        try (JiraClientAdapter client = new JiraClientAdapter(ui, options)) {
            if (!verifyConnectivity(client)) {
                ui.stopped(StopReason.NO_CONNECTION_TO_JIRA);
                return;
            }

            Filter filter = findFilter(client, options.filter);
            if (filter == null) {
                ui.stopped(StopReason.FILTER_NOT_FOUND);
                return;
            }

            SearchResult searchResults = search(client, filter);
            if (searchResults == null) {
                ui.stopped(StopReason.ISSUE_SEARCH_FAILED);
                return;
            }

            int total = searchResults.getTotal();
            if (total == 0) {
                ui.info("The filter matched no issues. Nothing to do - exiting.");
                ui.stopped(StopReason.NO_MATCHING_ISSUES);
                return;
            }

            ui.info("The filter matches {} issues.", total);
            if (!ui.confirmRemoval(total)) {
                ui.stopped(StopReason.USER_DID_NOT_CONFIRM_REMOVAL);
                return;
            }

            sessionStart = System.currentTimeMillis();
            Runtime.getRuntime().addShutdownHook(LOG_SESSION_STATS);
            while (searchResults.getTotal() > 0) {
                int skippedBefore = skipped;
                int removedBefore = removed;
                boolean success = removeBatch(client, searchResults);
                if (!success) {
                    ui.stopped(StopReason.ERROR);
                    break;
                } else if (remaining == 0) {
                    ui.info("Completed");
                    ui.stopped(StopReason.COMPLETED);
                    break;
                } else if (skipped == skippedBefore && removed == removedBefore) {
                    ui.warn("No issues removed or skipped in the batch. Make sure the filter defines a stable order. Exiting.");
                    ui.stopped(StopReason.NOTHING_REMOVED_IN_BATCH);
                    break;
                }
                searchResults = search(client, filter);
                if (searchResults == null) {
                    ui.stopped(StopReason.ISSUE_SEARCH_FAILED);
                    break;
                }
            }
        } catch (IOException e) {
            ui.error("An error has occurred. See log for detail.");
            ui.stopped(StopReason.ERROR);
        }
    }

    private boolean verifyConnectivity(JiraClientAdapter client) {
        try {
            client.getFavoriteFilters();
            ui.connectivityVerified();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Filter findFilter(final JiraClientAdapter client, final String filter) {
        ui.info("Searching favorite filters for \"{}\"", options.filter);
        Iterable<Filter> filters;
        try {
            filters = client.getFavoriteFilters();
        } catch (Exception e) {
            // User already notified by request handler
            return null;
        }

        List<Filter> matches = Lists.newArrayList(Iterables.filter(filters, new Predicate<Filter>() {
            @Override
            public boolean apply(Filter input) {
                return input.getName().trim().equalsIgnoreCase(filter.trim());
            }
        }));
        if (matches.size() == 0) {
            ui.error("Filter not found, make sure it's in favorites. Aborting.");
            return null;
        }
        if (matches.size() > 1) {
            ui.error("Found more than one filter with matching name. Aborting.");
            return null;
        }
        return matches.get(0);
    }

    private SearchResult search(JiraClientAdapter client, Filter filter) {
        try {
            return client.search(filter, options.queryBatchSize, skipped);
        } catch (Exception e) {
            // Already logged and retried by the client
            return null;
        }
    }

    private boolean removeBatch(JiraClientAdapter client, SearchResult searchResults) {
        remaining = searchResults.getTotal() - skipped;
        logStatus();
        ui.batchStarting();
        for (Issue issue : searchResults.getIssues()) {
            String key = issue.getKey();
            if (skippedKeys.contains(key)) {
                continue;
            }

            rateLimiter.acquire();

            ui.removing(issue);
            try {
                client.delete(issue);
                removed++;
            } catch (Exception e) {
                ui.removalFailed();
                skipped++;
                skippedKeys.add(key);
                if (!options.skipErrors) {
                    ui.info("Stopping on error. Please use the -s (or --skip-errors) option to skip errors.");
                    return false;
                }
            } finally {
                remaining--;
            }
        }
        ui.batchCompleted();
        return true;
    }

    private void logStatus() {
        if (removed > 0 || skipped > 0) {
            long batchTime = System.currentTimeMillis() - batchStart;
            long endTime = System.currentTimeMillis() + remaining * batchTime / options.queryBatchSize;
            ui.estimateAfterBatchCompletion(batchTime, options.queryBatchSize, new Date(endTime));
        }

        ui.progress(removed, skipped, remaining);
        batchStart = System.currentTimeMillis();
    }
}
