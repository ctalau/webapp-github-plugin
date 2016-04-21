package com.oxygenxml.examples.github;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;

import ro.sync.ecss.extensions.api.webapp.plugin.URLStreamHandlerWithContext;

/**
 * Used for opening proper GithubUrlConnections, making sure they have an access token
 * 
 * @author gabriel_titerlea
 *
 */
public class GithubUrlStreamHandler extends URLStreamHandlerWithContext {
  
  @Override
  protected URLConnection openConnectionInContext(String contextId, URL url,
      Proxy proxy) throws IOException {
    
    if (GithubUrlConnection.GIT_FILE_LIST_URL_HOST.equals(url.getHost())) {
      
      URLConnection getFileListConn = new URLConnection(url) {
        @Override
        public void connect() throws IOException {
          connected = true;
        }
      };
        
      return new GithubUrlConnection(getFileListConn, "54aac8b44cba2564570fd154d8d4e634f815c538", url.getPath());
      
    } else {
      String urlPathPart = url.getPath();
      
      // The github url path structure is: /$owner/$repo/&branch/$path
      String[] urlComponents = urlPathPart.split("/");
      
      String owner = urlComponents[1];
      String repo = urlComponents[2];
      String branch = urlComponents[3];
      String path = "";
      
      for (int i = 4; i < urlComponents.length; ++i) {
        path += "/" + urlComponents[i];
      }
      
      String githubApiUrlString = 
          (GitHubOauthServlet.apiUrl != null ? GitHubOauthServlet.apiUrl + "/api/v3" : "https://api.github.com") + 
          "/repos/" + owner + "/" + repo + "/contents" + path + "?ref=" + branch;
      
      // To increase the rate limit of the github api we must send the client secret and client id with each request
      if (GitHubOauthServlet.clientId != null && GitHubOauthServlet.clientSecret != null) {
        githubApiUrlString += "&client_id=" + GitHubOauthServlet.clientId + "&client_secret=" + GitHubOauthServlet.clientSecret;
      }
      
      URL apiUrl = new URL(githubApiUrlString);
      
      String accessToken = GitHubPlugin.accessTokens.getIfPresent(contextId);
      return new GithubUrlConnection(apiUrl.openConnection(), accessToken, urlPathPart);
    }
  }
}
