# solr-kerberos-delegationtokens

* `./gradlew jar`

* `solrj-jaas.conf`

```
SolrJClient {
  com.sun.security.auth.module.Krb5LoginModule required
  useTicketCache=true;
};
```

* `kinit`

* `java -Djava.security.auth.login.config=solrj-jaas.conf -jar solr-delegationtoken-1.0-SNAPSHOT.jar "http://localhost:8983/solr" "zk1:2181,zk2:2181,zk3:2181/solr"`
