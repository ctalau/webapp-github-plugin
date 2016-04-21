package com.oxygenxml.examples.github;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.jboss.resteasy.util.Base64;

import com.oxygenxml.examples.git.RESTGitAccess;

import ro.sync.ecss.extensions.api.webapp.WebappMessage;
import ro.sync.ecss.extensions.api.webapp.plugin.FilterURLConnection;
import ro.sync.ecss.extensions.api.webapp.plugin.UserActionRequiredException;
import ro.sync.net.protocol.FileBrowsingConnection;
import ro.sync.net.protocol.FolderEntryDescriptor;
import ro.sync.util.URLUtil;

/**
 * Used to handle requests for urls like: github://method/params.
 * 
 * @author gabriel_titerlea
 *
 */
public class GithubUrlConnection extends FilterURLConnection implements FileBrowsingConnection {

  /**
   * The host of the urls to git (not github) requests for files list.
   */
  public static final String GIT_FILE_LIST_URL_HOST = "getFileList";
  
  /**
   * The path of the opened url.
   */
  private String urlPathPart;
  
  /**
   * The github OAuth access token.
   */
  String accessToken;
  
  /**
   * Constructor
   * @param delegateConnection The underlying url connection
   * @param accessToken The github access token
   */
  public GithubUrlConnection(URLConnection delegateConnection, String accessToken, String urlPathPart) {
    super(delegateConnection);
    if (accessToken != null) {
      this.accessToken = accessToken;
      delegateConnection.setRequestProperty("Authorization", "token " + accessToken);
    }
    this.urlPathPart = urlPathPart;
    
    // Setting the version of the API for stability
    delegateConnection.setRequestProperty("Accept", "application/vnd.github.v3+json");
  }
  
  @Override
  public InputStream getInputStream() throws IOException {
    String githubJsonResult;
    try {
      // The response from github comes in a json like: // {"content":"BASE64ecnodedContent",otherProps}
      githubJsonResult = GithubUtil.inputStreamToString(delegateConnection.getInputStream());
    } catch (IOException e) {
      throw new IOException("404 Not Found for: " + urlPathPart);
    }
    HashMap<String, Object> result = GithubUtil.parseJSON(githubJsonResult);

    String base64Content = (String) result.get("content");
    byte[] decodedContent = Base64.decode(base64Content);

    return new ByteArrayInputStream(decodedContent);
  }
  
  @Override
  public OutputStream getOutputStream() throws IOException {
    return new ByteArrayOutputStream() {
      @Override
      public void close() throws IOException {
        try {
          // The github api requests the content of the files to be base64 encoded
          byte[] content = toByteArray();
          String encodedContent = Base64.encodeBytes(content);
          
          // The url of the delegateConnection happens to be exactly what the GitHub api for creating/updating a file requires.
          // It is built in GithubUrlStreamHandler.openConnectionInContext
          URL apiCallUrl = delegateConnection.getURL();

          // Making a GET request to see if the file exists already
          HttpURLConnection connToCheckIfFileExists = (HttpURLConnection) apiCallUrl.openConnection();
          connToCheckIfFileExists.setRequestProperty("Authorization", "token " + accessToken);
          connToCheckIfFileExists.setRequestMethod("GET");
          
          Integer responseCode = null;
          HashMap<String, Object> fileExistsResult = null;
          
          try {
            responseCode = connToCheckIfFileExists.getResponseCode();
            fileExistsResult = GithubUtil
                .parseJSON(GithubUtil.inputStreamToString(connToCheckIfFileExists
                        .getInputStream()));          
          } catch (IOException e) {
            String message = e.getMessage();
            if (message != null && message.startsWith("404 Not Found")) {
              // If the file does not exist urlConnectionToCheckIfFileExists.getResponseCode() throws an IOException
              responseCode = 404;
            } else {
              // If a different error occurred or if the exception was thrown by inputStreamToString we will set the responseCode to 500
              // This way the third if branch below will be triggered and an IOException thrown.
              responseCode = 500;
            }
          }

          // We need to send the branch as a property of the JSON request body
          // We can take it from the apiCallUrl. It's the ref query param
          String branch = null;
          String[] queryParams = apiCallUrl.getQuery().split("&");
          for (int i = 0; i < queryParams.length - 1; i++) {
            String[] propValue = queryParams[i].split("=");
            if (propValue.length == 2 && propValue[0].equals("ref")) {
              // We wil put the branch as a JSON property and we don't need to have it URL encoded there.
              branch = URLUtil.decodeURIComponent(propValue[1]);
              break;
            }
          }
          
          if (branch == null) {
            // This should never happen
            throw new IOException("Could not create or update file on GitHub, missing branch.");
          }
          
          String apiRequestBody;
          // If we didn't find the file we will create one
          if (responseCode == 404) {
            apiRequestBody = 
                "{"
                + "\"message\":\"Creating new file from template.\","
                + "\"content\":\"" + encodedContent + "\","
                + "\"branch\":\"" + branch + "\""
              + "}";
          } 
          // Otherwise we will update the existing file
          else if (fileExistsResult != null) {
            // To update a file the GitHub api requires the sha of the updated file.
            String sha = (String) fileExistsResult.get("sha");
            
            apiRequestBody = 
                "{"
                + "\"message\":\"Overwriting file.\","
                + "\"content\":\"" + encodedContent + "\","
                + "\"sha\":\"" + sha + "\","
                + "\"branch\":\"" + branch + "\""
              + "}";
          } else {
            throw new IOException("Could not create or update file on GitHub");
          }
          
          HttpURLConnection urlConnection = (HttpURLConnection) apiCallUrl.openConnection();
          urlConnection.setRequestProperty("Content-Type", "application/json");
          urlConnection.setRequestProperty("Authorization", "token " + accessToken);
          urlConnection.setRequestMethod("PUT");
          urlConnection.setDoOutput(true);
          
          OutputStream outputStream = urlConnection.getOutputStream();
          outputStream.write(apiRequestBody.getBytes());
          outputStream.flush();
          
          outputStream.close();
        } catch (IOException e) {
          filterClientSecret(e);
        } 
      }
    };
  }
  
  @Override
  public List<FolderEntryDescriptor> listFolder() throws IOException {
    List<FolderEntryDescriptor> filesList = null;
    
    if (GIT_FILE_LIST_URL_HOST.equals(delegateConnection.getURL().getHost())) {
      filesList = listFolderGit();
    } else {
      filesList = listFolderGitHub();
    }
    
    return filesList == null ? new ArrayList<FolderEntryDescriptor>() : filesList;
  }
  
  /**
   * @return A list of files from the folder specified in the url field.
   * 
   * @throws IOException When failing to retrieve the list of files.
   */
  private List<FolderEntryDescriptor> listFolderGit() throws IOException {
    List<FolderEntryDescriptor> filesList = null;
    
    String[] pathComponents = url.getPath().split("/");
    
    if (pathComponents.length < 4) {
      throw new IOException("Malformed request.");
    }
    
    String repositoryUri = URLUtil.decodeURIComponent(pathComponents[1]);
    String branchName = URLUtil.decodeURIComponent(pathComponents[2]);
    String path = URLUtil.decodeURIComponent(pathComponents[3]);
    
    try {
      UsernamePasswordCredentialsProvider credentials = new UsernamePasswordCredentialsProvider(accessToken, "");
      
      File[] fileList = RESTGitAccess.access.listFiles(repositoryUri, branchName, path, credentials);
      
      filesList = new ArrayList<FolderEntryDescriptor>(fileList.length);
      
      for (File file : fileList) {
        String absolutePath = file.getAbsolutePath().replace("\\", "/");
        if (file.isDirectory()) {
          absolutePath += "/";
        }
        
        filesList.add(new FolderEntryDescriptor(absolutePath));
      }
    } catch (TransportException e) {
      throw new IOException(e.getMessage());
    } catch (GitAPIException e) {
      throw new IOException(e.getMessage());
    }
    
    return filesList;
  }
  
  /**
   * @return A list of files from the folder specified in the url field.
   * 
   * @throws IOException When failing to retrieve the list of files.
   */
  private List<FolderEntryDescriptor> listFolderGitHub() throws IOException {
    List<FolderEntryDescriptor> filesList = null;

    try {
      String githubJsonResult = GithubUtil
          .inputStreamToString(delegateConnection.getInputStream());
      
      // If this is a Json array:
      // [{content:'content'},{content:'content'},{content:'content'}]
      if (githubJsonResult.charAt(0) == '[') {
        List<GithubApiResult> githubResults = GithubUtil
            .parseGithubListResult(githubJsonResult);

        filesList = new ArrayList<FolderEntryDescriptor>(githubResults.size());
        
        for (GithubApiResult result : githubResults) {
          // Add a '/' when the file is a directory because this is how upstream
          // identifies directories
          String dirChar = "";
          if (result.type.equals("dir")) {
            dirChar = "/";
          }

          filesList.add(new FolderEntryDescriptor(URLUtil
              .encodeURIComponent(result.name) + dirChar));
        }
      } else {
        filesList = new ArrayList<FolderEntryDescriptor>(1);
        
        // The result is a file and its content is Base64 encoded in the content
        // property
        GithubApiResult githubResult = GithubUtil
            .parseGithubResult(githubJsonResult);

        byte[] decodedContent = Base64.decode(githubResult.content);
        filesList.add(new FolderEntryDescriptor(new String(decodedContent)));
      }
      
      return filesList;
    } catch (IOException e) {
      if (e.getMessage().startsWith("401") || e.getMessage().startsWith("403 Forbidden")) {
        // if the user is not authorized
        throw new UserActionRequiredException(new WebappMessage(
            WebappMessage.MESSAGE_TYPE_CUSTOM, "Authentication required",
            "Authentication required", true));
      } if (e.getMessage().startsWith("404")) {
        checkRepositoryAccess(e);
      } else {
        filterClientSecret(e);
      }
    }
    
    return filesList;
  }
  
  /**
   * Checks whether the request was not completed because we don't have repo access or because of some other error.
   * 
   * @param e The exception which caused the checking for access. If we do have access we will re-throw this exception because it was a valid one.
   * 
   * @throws UserActionRequiredException If we suspect that the user does not have access to the repository.
   * @throws IOException When we are re-throwing the exception given as a parameter.
   */
  private void checkRepositoryAccess(IOException e) throws UserActionRequiredException, IOException {
    URL url = delegateConnection.getURL();
    
    // protocol://host:port/repos/:owner/:repo/
    String protocol = url.getProtocol();
    String host = url.getHost();
    int port = url.getPort();
    String[] pathComponents = url.getPath().split("/");
    String owner = pathComponents[2];
    String repo = pathComponents[3];
    
    String repositoryAccessCheck = protocol + "://" + host + (port != -1 ? ":" + port : "") + 
                                   "/repos/" + owner + "/" + repo;
    
    try {
      URL repositoryAccessCheckUrl = new URL(repositoryAccessCheck);
      HttpURLConnection accessCheckConn = (HttpURLConnection) repositoryAccessCheckUrl.openConnection();
      accessCheckConn.setRequestProperty("Authorization", "token " + accessToken);
      accessCheckConn.setRequestProperty("Accept", "application/vnd.github.v3+json");
      
      
      int responseCode = accessCheckConn.getResponseCode();
      
      // If a user does not have access to view a repository on GitHub then he/she will receive a 404 error
      // In this case we want to return a 401 to let the user know they do not have access.
      if (responseCode == 404 || responseCode == 401) {
        // The repository is not found, it means we do not have repo access. So throw 401.
        throw new UserActionRequiredException(new WebappMessage(
            WebappMessage.MESSAGE_TYPE_CUSTOM, "Authentication required",
            "Authentication required", true));
      }
    } catch (MalformedURLException _) {} catch (IOException ex) {
      if (ex.getMessage().startsWith("404") || ex.getMessage().startsWith("401")) {
        throw new UserActionRequiredException(new WebappMessage(
            WebappMessage.MESSAGE_TYPE_CUSTOM, "Authentication required",
            "Authentication required", true));
      }
    }
    
    // If we didn't throw our custom exception, then just let this exception throw.
    filterClientSecret(e);
  }
  
  /**
   * Filters out the client_secret from the message of an IOException.
   * @param e The exception from which to filter out.
   * @throws IOException Thrown again, but this time it does not contain any client_secret info.
   */
  private void filterClientSecret(IOException e) throws IOException {
    // We should never send the client_secret to the client. So if the error 
    // message contains a url with the client_secret we'll trim it out
    String message = e.getMessage();
    int indexOfClientSecret = message.indexOf("client_secret");
    if (indexOfClientSecret != -1) {
      throw new IOException(message.substring(0, indexOfClientSecret));
    } else {
      throw e;
    }
  }
}
