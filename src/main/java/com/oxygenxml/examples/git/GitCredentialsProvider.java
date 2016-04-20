package com.oxygenxml.examples.git;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.eclipse.jgit.transport.CredentialsProvider;

import com.oxygenxml.examples.github.GithubUtil;

/**
 * Class used to manage session ids and credentials for OAuth access to git repositories. 
 * @author gabriel_titerlea
 */
public class GitCredentialsProvider {
  
  /**
   * A map of sessionIds to JGit CredentialProviders.
   */
  private Map<String, CredentialsProvider> credentials = new HashMap<String, CredentialsProvider>();
  
  /**
   * Returns the credentials for a given session id.
   * @param sessionId The session id to get the credentials for.
   * @return The credentials for a given session id.
   */
  public CredentialsProvider getCredentials(String sessionId) {
    return credentials.get(sessionId);
  }
  
  public void setCredentials(String sessionId, CredentialsProvider credential) {
    this.credentials.put(sessionId, credential);
  }
  
  /**
   * Handles the request for git credentials.
   * Used before showing users links to start an OAuth flow.
   * 
   * @param req The HTTP request object.
   * @param resp The HTTP response object.
   * @throws IOException When failed to read the request body. 
   */
  public void handleRequestForCredentials(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException {
    // Identifying the user making the request
    HttpSession session = httpRequest.getSession();
    
    // Getting the request body
    String requestBodyString = GithubUtil.inputStreamToString(httpRequest.getInputStream());
    HashMap<String, Object> requestBody = GithubUtil.parseJSON(requestBodyString);
    
    syncTokens(requestBody);
    maybeReset(requestBody);
  }

  /**
   * 
   * @param requestBody
   */
  private void syncTokens(HashMap<String, Object> requestBody) {
    // TODO: Sync specific tokens
    //syncGithubTokens(requestBody);
    //syncBitbucketTokens(requestBody);
  }
  
  /**
   * Resets the credentials to null if necessary. 
   * @param requestBody The http request body parameters.
   */
  private void maybeReset(HashMap<String, Object> requestBody) {
    // TODO Auto-generated method stub
    
  }
}
