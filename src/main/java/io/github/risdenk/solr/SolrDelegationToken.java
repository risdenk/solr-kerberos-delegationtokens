package io.github.risdenk.solr;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpClientUtil;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.impl.Krb5HttpClientBuilder;
import org.apache.solr.client.solrj.impl.LBHttpSolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.request.DelegationTokenRequest;
import org.apache.solr.client.solrj.response.CollectionAdminResponse;
import org.apache.solr.client.solrj.response.DelegationTokenResponse;
import org.apache.solr.common.params.ModifiableSolrParams;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class SolrDelegationToken {
  void runHttp(String solrURL, String token) throws Exception {
    // Use Delegation Token
    try(SolrClient dtClient = getHttpSolrClient(solrURL, token)) {
      CollectionAdminRequest.List listRequest = new CollectionAdminRequest.List();
      CollectionAdminResponse response = listRequest.process(dtClient);
      System.out.println(response.toString());
    }
  }

  void runZK(String zk, String token) throws Exception {
    // Use Delegation Token
    try(SolrClient dtClient = getCloudSolrClient(zk, token)) {
      CollectionAdminRequest.List listRequest = new CollectionAdminRequest.List();
      CollectionAdminResponse response = listRequest.process(dtClient);
      System.out.println(response.toString());
    }
  }

  private SolrClient getHttpSolrClient(String solrURL, String token) {
    return new HttpSolrClient.Builder()
        .withBaseSolrUrl(solrURL)
        .withKerberosDelegationToken(token)
        .build();
  }

  private SolrClient getCloudSolrClient(String zk, String token) {
    String[] zkParts = zk.split("/", 2);
    List<String> zkHosts = Arrays.asList(zkParts[0].split(","));
    Optional<String> zkChroot = Optional.of('/' + zkParts[1]);
    return new CloudSolrClient.Builder(zkHosts, zkChroot).withLBHttpSolrClientBuilder(
                   new LBHttpSolrClient.Builder().withHttpSolrClientBuilder(
                       new HttpSolrClient.Builder().withKerberosDelegationToken(token)))
               .build();
  }

  private SolrClient getKerberosHttpSolrClient(String solrURL) {
    HttpClientUtil.setHttpClientBuilder(new Krb5HttpClientBuilder().getBuilder());
    CloseableHttpClient httpClient = HttpClientUtil.createClient(new ModifiableSolrParams());
    HttpClientUtil.resetHttpClientBuilder();

    return new HttpSolrClient.Builder(solrURL).withHttpClient(httpClient).build();
  }

  private SolrClient getKerberosCloudSolrClient(String zk) {
    String[] zkParts = zk.split("/", 2);
    List<String> zkHosts = Arrays.asList(zkParts[0].split(","));
    Optional<String> zkChroot = Optional.of('/' + zkParts[1]);

    HttpClientUtil.setHttpClientBuilder(new Krb5HttpClientBuilder().getBuilder());
    CloseableHttpClient httpClient = HttpClientUtil.createClient(new ModifiableSolrParams());
    HttpClientUtil.resetHttpClientBuilder();

    return new CloudSolrClient.Builder(zkHosts, zkChroot).withLBHttpSolrClientBuilder(
        new LBHttpSolrClient.Builder().withHttpSolrClientBuilder(
            new HttpSolrClient.Builder().withHttpClient(httpClient)))
               .build();
  }

  String getDelegationTokenHttp(String solrURL) throws Exception {
    try(SolrClient kerberosClient = getKerberosHttpSolrClient(solrURL)) {
      return getDelegationToken(kerberosClient);
    }
  }

  String getDelegationTokenCloud(String zk) throws Exception {
    try(SolrClient kerberosClient = getKerberosCloudSolrClient(zk)) {
      return getDelegationToken(kerberosClient);
    }
  }

  private String getDelegationToken(final SolrClient kerberosClient)
      throws Exception {
    DelegationTokenRequest.Get get = new DelegationTokenRequest.Get();
    DelegationTokenResponse.Get getResponse = get.process(kerberosClient);
    return getResponse.getDelegationToken();
  }

  private long renewDelegationToken(final SolrClient kerberosClient, final String token)
      throws Exception {
    DelegationTokenRequest.Renew renew = new DelegationTokenRequest.Renew(token);
    DelegationTokenResponse.Renew renewResponse = renew.process(kerberosClient);
    return renewResponse.getExpirationTime();
  }

  private void cancelDelegationTokenHttp(String solrURL, String token) throws Exception {
    try(SolrClient kerberosClient = getKerberosHttpSolrClient(solrURL)) {
      cancelDelegationToken(kerberosClient, token);
    }
  }

  private void cancelDelegationTokenCloud(String zk, String token) throws Exception {
    try(SolrClient kerberosClient = getKerberosCloudSolrClient(zk)) {
      cancelDelegationToken(kerberosClient, token);
    }
  }

  private void cancelDelegationToken(final SolrClient kerberosClient, String token)
      throws Exception {
    DelegationTokenRequest.Cancel cancel = new DelegationTokenRequest.Cancel(token);
    cancel.process(kerberosClient);
  }

  public static void main(String[] args) throws Exception {
    SolrDelegationToken solrDelegationToken = new SolrDelegationToken();

    // HTTP
    String solrURL = args[0];
    String httpDT = solrDelegationToken.getDelegationTokenHttp(solrURL);

    solrDelegationToken.runHttp(solrURL, httpDT);
    solrDelegationToken.runZK(args[1], httpDT);

    solrDelegationToken.cancelDelegationTokenHttp(solrURL, httpDT);

    try {
      solrDelegationToken.runHttp(solrURL, httpDT);
    } catch (HttpSolrClient.RemoteSolrException e) {
      e.printStackTrace();
    }
    try {
      solrDelegationToken.runZK(args[1], httpDT);
    } catch (HttpSolrClient.RemoteSolrException e) {
      e.printStackTrace();
    }

    // Cloud
    String zk = args[1];
    String cloudDT = solrDelegationToken.getDelegationTokenCloud(zk);

    solrDelegationToken.runHttp(solrURL, cloudDT);
    solrDelegationToken.runZK(args[1], cloudDT);

    solrDelegationToken.cancelDelegationTokenCloud(zk, cloudDT);

    try {
      solrDelegationToken.runHttp(solrURL, cloudDT);
    } catch (HttpSolrClient.RemoteSolrException e) {
      e.printStackTrace();
    }
    try {
      solrDelegationToken.runZK(args[1], cloudDT);
    } catch (HttpSolrClient.RemoteSolrException e) {
      e.printStackTrace();
    }
  }
}
