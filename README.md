# xtest

## web ui
Web server uses 9090. Ensure starts api server, db server before testing web page http://localhost:9090/index.html

Start ui and apis server at port 9090
```
# shadow-cljs is used to build the web ui
npx shadow-cljs watch app

# web server
clj -X:run-x
```

## database
XTDB in docker, saved in local directory. Bi-teimporality.

```
mkdir -p ~/xtest-xtdb-data

# start xtdb in docker
# 5432: Postgres wire-compatible server (primary API)
# 8080: Monitoring/healthz HTTP endpoints
# 3000: HTTP query/tx API
docker run -it --pull=always \
-p 5432:5432 \
-p 8080:8080 \
-p 3000:3000 \
-v ~/xtest-xtdb-data:/var/lib/xtdb \
ghcr.io/xtdb/xtdb

# run psql to connect to the database
psql -h localhost -U xtdb xtdb

```

## rest service
### apis
* projects

Cases and runs have exactly 1 owner project.

A project has attributes: id, description.

* users

 A user is identified by email address. Each user has a password created when creating the user.
 Passwords must be at least 10 characters long, contain at least one lowercase ASCII letter, one uppercase ASCII letter, one digit, and one special symbol from the set: `! @ # $ % ^ & * ( ) - _ = + [ ] { } | / \ < > , . : ; " '`

* roles
  * admin: can manager users
  * tester: can add cases, runs. A tester has a project id(s) assigned. The tester can only
  manager data for the assigned project(s).

* variables

A variable has name. A vriable assignment assign a value to a variable.

A variables environment is a list of variables assignments.

A test step definition may have a list of variable parameters. An actual step in a test case must assign values to these parameters.

* steps

An actual test step is a step definition with parameters assigned.


* cases
  
cases are organized by tagging. Example, for hierarchy, tag a case as A/B/C.
Each case has:
    * title: unique
    * id: system assigned
    * steps: description of test steps. Each step specifies a step definition and how the parameters are assigned.

* runs

Each run has attributes: id, time, start_time, end_time. A run is a list of tests runs.
Each test run contains: case_id, start_time, end_time, logs, result

    * run result: pass, fail. 

* reports

A report is usually the aggregated results of a test run.
gi
* attachments

Each case, tests run, test run, project, report can have a number attachments linked to them.

### example commands
```
# run tests in namespace
clojure -M:test   -n xtest.case-test

# create a user (first, no basic token passed)
curl -i -X POST http://localhost:9090/api/users/create          -H "Content-Type: application/json"          -d '{
               "first-name": "Alice",t
               "last-name" : "Smith3",
               "email"     : "alice.smith3@example.com",
               "password"  : "Secur3P@ssword!"}'

# fetch a user
curl -G http://localhost:9090/api/users/get   \
 -H "Authorization: Basic YWxpY2Uuc21pdGgzQGV4YW1wbGUuY29tOlNlY3VyM1BAc3N3b3JkIQ=="   \
        -H "Accept: application/json"          --data-urlencode "email=alice.smith3@example.com"
 
 # update password
 curl -i -X POST http://localhost:9090/api/users/update      \
  -H "Authorization: Basic YWxpY2Uuc21pdGgzQGV4YW1wbGUuY29tOlNlY3VyM1BAc3N3b3JkIQ=="   \
     -H "Content-Type: application/json"          -d '{
               "new-password": "abcdeABCDE01!",
               "email"     : "alice.smith3@example.com",
               "original-password"  : "Secur3P@ssword!"}'


# pass basic token of existing user
curl -i -X POST http://localhost:9090/api/users/create   -H "Authorization: Basic YWxpY2Uuc21pdGgzQGV4YW1wbGUuY29tOlNlY3VyM1BAc3N3b3JkIQ=="        -H "Content-Type: application/json"          -d '{
               "first-name": "Alice",
               "last-name" : "Smith3",
               "email"     : "alice.smith2@example.com",
               "password"  : "Secur3P@ssword!"
             }'


 curl -X POST "http://localhost:9090/api/users/login"        -H "Content-Type: application/json"        -d '{"email":"alice.smith3@example.com","password":"Secur3P@ssword!"}'


# fetch projects by ids
curl -G http://localhost:9090/api/projects/get   \
 -H "Authorization: Basic YWxpY2Uuc21pdGgzQGV4YW1wbGUuY29tOlNlY3VyM1BAc3N3b3JkIQ=="   \
        -H "Accept: application/json"          --data-urlencode "ids=project-1,project-2"
 
# fetch projects by names
curl -G http://localhost:9090/api/projects/get   \
 -H "Authorization: Basic YWxpY2Uuc21pdGgzQGV4YW1wbGUuY29tOlNlY3VyM1BAc3N3b3JkIQ=="   \
        -H "Accept: application/json"          --data-urlencode "names=Test project"


# create a project
curl -i -X POST http://localhost:9090/api/projects/create      \
 -H "Authorization: Basic YWxpY2Uuc21pdGgzQGV4YW1wbGUuY29tOlNlY3VyM1BAc3N3b3JkIQ=="   \
    -H "Content-Type: application/json"          -d '{
               "id": "project-2",
               "name" : "Test Project 2"}'

# create a case
curl -i -X POST http://localhost:9090/api/cases/create      \
 -H "Authorization: Basic YWxpY2Uuc21pdGgzQGV4YW1wbGUuY29tOlNlY3VyM1BAc3N3b3JkIQ=="   \
    -H "Content-Type: application/json"          -d '{
               "id": "case-1",
               "name": "case 1",
                "project-id": "project-1",
                 "description": "case 1 for project 1",
                 "steps":[{"description": "step 1", "precondition": "precondition 1", "postcondition": "postcondition1"}], 
                 "tags":["name1:value1"]}'

# fetch cases
curl -G http://localhost:9090/api/cases/get   \
 -H "Authorization: Basic YWxpY2Uuc21pdGgzQGV4YW1wbGUuY29tOlNlY3VyM1BAc3N3b3JkIQ=="   \
        -H "Accept: application/json"          --data-urlencode "ids=project-1,project-2"

```