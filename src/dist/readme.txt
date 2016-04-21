Expium Mass Delete for JIRA @version@
===============================================================================

This application removes all issues matching a filter from JIRA using the REST
API. It uses username and password for authentication and requires no
configuration. The credentials are not stored or sent anywhere.

It requires Java to be installed in the system. It supports Java 7 and newer.

To run the application, execute one of the following.

Linux/Mac:
bin/mass-delete-for-jira-cli [options]

Windows:
bin/mass-delete-for-jira-cli.bat [options]

For example, to delete all issues matching the filter called "Test filter",
logging in as "admin" and using default settings for batch size and rate limit,
the command looks like:

bin/mass-delete-for-jira-cli --url http://company.com/jira --user admin --filter "Test filter"

bin/mass-delete-for-jira-cli -j http://company.com/jira -u admin -f "Test filter"

Each option has a short and long version that may be used interchangeably. The
available options are:

 -? (--help)             : Print this help (default: false)
 -b (--batch-size) N     : How many issues to query at a time (default: 100)
 -f (--filter) VAL       : Name of filter in JIRA (must be in favorites)
 -j (--url) URI          : Base JIRA URL
 -m (--max-per-second) N : Max issues to delete per second, may be fractional
                           (default: 1000.0)
 -s (--skip-errors)      : When set, the program will skip issues it was unable
                           to delete and try to continue. (default: false)
 -u (--user) VAL         : JIRA user name (login)
 -v (--verbose)          : Enable verbose output (default: false)

When executed, the program will ask for the JIRA password. Then it will query
the first batch of issues from JIRA, display the count and ask for confirmation.
The removal will only begin after the confirmation.

Troubleshooting
---------------

Q: The applications fails to run, printing an error similar to the following:
Exception in thread "main" java.lang.UnsupportedClassVersionError:
com/expium/massdelete/Main : Unsupported major.minor version 51.0

A: This error appears when the software is ran with Java 6 or older. It requires
Java 7 or newer.

To verify the installed Java version, use the following command:

java -version

The printed version needs to look like 1.7, 1.8 etc.

As of December 2015, Java 6 and 7 have reached end of life and if possible it's
recommended to update. Note that JIRA 6.2 may not support Java 8.

If update is not possible, Mass Delete can be ran from any computer which
has Java 7 or newer. It works over the network and will be able to successfully
perform its job as long as the computer is able to reach JIRA.

-------------------------

Copyright 2015-2016 Expium LLC
http://expium.com/
