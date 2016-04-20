package com.oxygenxml.examples.git;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;

import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import com.oxygenxml.examples.github.GitHubPlugin;

import ro.sync.ecss.extensions.api.webapp.plugin.URLStreamHandlerWithContext;

public class GitUrlStreamHandler extends URLStreamHandlerWithContext {
  @Override
  protected URLConnection openConnectionInContext(String contextId, URL url, Proxy proxy) throws IOException {
    // TODO: get credentials from the GitAuthorizationProvider
    
    String accessToken = GitHubPlugin.accessTokens.getIfPresent(contextId);
    UsernamePasswordCredentialsProvider credentials = new UsernamePasswordCredentialsProvider(accessToken, "");
    
    return new GitUrlConnection(url.openConnection(), credentials, url.getPath());
  }
}
