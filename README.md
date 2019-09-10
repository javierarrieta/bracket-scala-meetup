# bracket-scala-meetup
Repository for code examples for the Dublin Scala Meetup about bracket

We will explore how to build a simple webapp using `Resource` for monadic resource acquisition and release.
We will be using scala 2.13.0, http4s, zio, cats-effect and plain JDBC to see how to interact with legacy libraries.

Link to the slides [here](https://docs.google.com/presentation/d/1DEy_2j9dMoEbwiStHDKd4XUd4GxErlHP0LlDdDSoX64)

## How to run the example app
You will need to start a database, for instance with docker:
```bash
docker run --name postgres -p 5432:5432 -e POSTGRES_USER=admin -e POSTGRES_PASSWORD=admin -e POSTGRES_DB=customers --rm postgres:alpine
```

Then import the schema and an example row in the table
(use `admin` as password as set in the step before):

```
psql -h localhost -U admin -d customers -f test-data.sql
```