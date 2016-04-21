package com.expium.massdelete.remover;

import com.atlassian.jira.rest.client.api.AuthenticationHandler;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Filter;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.auth.BasicHttpAuthenticationHandler;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.atlassian.util.concurrent.Promise;
import com.expium.massdelete.Options;
import com.expium.massdelete.ui.UI;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * Copyright 2015-2016 Expium LLC
 * http://expium.com/
 */
public class JiraClientAdapter implements Closeable {
    private static final int RETRY_ATTEMPTS = 3;
    private static final int RETRY_INTERVAL_SECONDS = 10;

    private final UI ui;

    private final AuthenticationHandler auth;
    private final JiraRestClient client;

    public JiraClientAdapter(UI ui, Options options) {
        this.ui = ui;

        auth = new BasicHttpAuthenticationHandler(options.user, options.password);
        client = new AsynchronousJiraRestClientFactory().create(options.url, auth);
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    public Iterable<Filter> getFavoriteFilters() throws Exception {
        return unwrap(client.getSearchClient().getFavouriteFilters(), "get favorite filters");
    }

    public SearchResult search(final Filter filter, final int maxResults, final int offset) throws Exception {
        return tryWithRetries(new Callable<SearchResult>() {
            @Override
            public SearchResult call() throws Exception {
                Set<String> fields = new HashSet<>();
                fields.add("summary");
                fields.add("key");
                fields.add("issuetype");
                fields.add("created");
                fields.add("updated");
                fields.add("project");
                fields.add("status");
                return unwrap(client.getSearchClient().searchJql(filter.getJql(), maxResults, offset, fields),
                        "search for issues");
            }
        }, RETRY_ATTEMPTS);

    }

    public void delete(final Issue issue) throws Exception {
        tryWithRetries(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                String key = issue.getKey();
                return unwrap(client.getIssueClient().deleteIssue(key, true), "delete issue " + key);
            }
        }, RETRY_ATTEMPTS);
    }

    private <T> T tryWithRetries(Callable<T> producer, int attempts) throws Exception {
        try {
            return producer.call();
        } catch (Exception e) {
            if (isIoException(e) && attempts > 0) {
                ui.info("The operation will be attempted {} more time(s), next try in {} seconds", attempts,
                        RETRY_INTERVAL_SECONDS);
                Thread.sleep(RETRY_INTERVAL_SECONDS * 1000);
                return tryWithRetries(producer, attempts - 1);
            } else {
                throw e;
            }
        }
    }

    <T> T unwrap(Promise<T> value, String operation) throws Exception {
        try {
            return value.get();
        } catch (InterruptedException e) {
            ui.interrupted();
            throw e;
        } catch (ExecutionException ee) {
            Throwable e = ee.getCause();
            ui.jiraRequestFailed(operation, e);

            if (e instanceof Exception) {
                throw (Exception) e;
            } else if (e instanceof Error) {
                throw (Error) e;
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    private boolean isIoException(Throwable e) {
        return Iterables.any(Throwables.getCausalChain(e), Predicates.instanceOf(IOException.class));
    }
}
