package com.expium.massdelete;

/**
 * Copyright 2015-2016 Expium LLC
 * http://expium.com/
 */
public enum StopReason {
    NO_CONNECTION_TO_JIRA,
    FILTER_NOT_FOUND,
    USER_DID_NOT_CONFIRM_REMOVAL,
    ERROR,
    NO_MATCHING_ISSUES,
    COMPLETED,
    NOTHING_REMOVED_IN_BATCH,
    ISSUE_SEARCH_FAILED
}
