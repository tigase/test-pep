# Test PEP notifications on automatic subscriptions using +notify

To launch this test you need Java JDK (ie. JDK 11) and Maven 3.

With that in place, you can execute from the command line (from the root directory of the project):

````bash
mvn test -Daccount="admin@localhost" -Dpassword="admin"
````

where:
- account - is a bare JID to use for testing
- password - is a password for this account
