package io.github.risdenk.solr;

import org.junit.Test;

public class TestSolrDelegationToken {
  @Test
  public void testSolrDelegationToken() throws Exception {
    String solrURL = "http://localhost:8983/solr";;

    SolrDelegationToken solrDelegationToken = new SolrDelegationToken();
    String httpDT = solrDelegationToken.getDelegationTokenHttp(solrURL);
    solrDelegationToken.runHttp(solrURL, httpDT);
  }
}
