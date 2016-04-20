package com.oxygenxml.examples.git;

import java.net.URLStreamHandler;

import com.oxygenxml.examples.github.GithubUrlStreamHandler;

import ro.sync.exml.plugin.urlstreamhandler.URLStreamHandlerPluginExtension;
import ro.sync.exml.workspace.api.Platform;
import ro.sync.exml.workspace.api.PluginWorkspaceProvider;

/**
 * Used to open a proper UrlStreamHandler if the url is a git url
 *  
 * @author gabriel_titerlea
 */
public class GitURLStreamHandlerPluginExtension implements URLStreamHandlerPluginExtension {
  public URLStreamHandler getURLStreamHandler(String protocol) {
    
    boolean isWebapp = Platform.WEBAPP.equals(PluginWorkspaceProvider.getPluginWorkspace().getPlatform());
    URLStreamHandler handler = null;
    
    // If this is a url like: git://method/params
    if (isWebapp && "git".equals(protocol)) {
      handler = new GitUrlStreamHandler();
    } 

    return handler;
  }
}
