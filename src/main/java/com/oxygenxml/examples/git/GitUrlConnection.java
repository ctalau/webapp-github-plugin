package com.oxygenxml.examples.git;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.CredentialsProvider;

import ro.sync.ecss.extensions.api.webapp.plugin.FilterURLConnection;
import ro.sync.ecss.extensions.api.webapp.plugin.UserActionRequiredException;
import ro.sync.net.protocol.FileBrowsingConnection;
import ro.sync.net.protocol.FolderEntryDescriptor;
import ro.sync.util.URLUtil;

public class GitUrlConnection extends FilterURLConnection implements FileBrowsingConnection {

  /**
   * Credentials used to authenticate git requests.
   */
  private CredentialsProvider credentials;
  
  /**
   * The path part of the original url.
   */
  private String urlPathPart; // Will be used later.

  /**
   * Constructor.
   * 
   * @param delegateConnection
   * @param credentials
   * @param urlPathPart
   */
  public GitUrlConnection(URLConnection delegateConnection, CredentialsProvider credentials, String urlPathPart) {
    super(delegateConnection);

    this.credentials = credentials;
    this.urlPathPart = urlPathPart;
  }
  
  @Override
  public List<FolderEntryDescriptor> listFolder() throws IOException,
      UserActionRequiredException {
    List<FolderEntryDescriptor> filesRet = null;
    
    URL url = delegateConnection.getURL();
    String[] pathComponents = url.getPath().split("/");
    
    String repositoryUri = URLUtil.decodeURIComponent(pathComponents[1]);
    String branchName = URLUtil.decodeURIComponent(pathComponents[2]);
    String path = URLUtil.decodeURIComponent(pathComponents[3]);
    
    try {
      File[] filesList = RESTGitAccess.access.listFiles(repositoryUri, branchName, path, credentials);
      
      filesRet = new ArrayList<FolderEntryDescriptor>(filesList.length);
      
      for (File file : filesList) {
        String absolutePath = file.getAbsolutePath().replace("\\", "/");
        if (file.isDirectory()) {
          absolutePath += "/";
        }
        
        filesRet.add(new FolderEntryDescriptor(absolutePath));
      }
    } catch (TransportException e) {
      throw new IOException(e.getMessage());
    } catch (GitAPIException e) {
      throw new IOException(e.getMessage());
    }
    
    return filesRet == null ? new ArrayList<FolderEntryDescriptor>() : filesRet;
  }
}
