package com.oxygenxml.examples.git;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.input.ReaderInputStream;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.CredentialsProvider;

import com.oxygenxml.examples.github.GithubUtil;

import ro.sync.ecss.extensions.api.webapp.plugin.WebappServletPluginExtension;

/**
 * Servlet class used to dispatch git requests to to methods.  
 * @author gabriel_titerlea
 */
public class RESTGitAccess extends WebappServletPluginExtension {
  private GitAccess gitAccess = new GitAccess();
  
  private RepositoryProvider repositoryProvider;
  
  @Override
  public String getPath() {
    return "git";
  }
  
  @Override
  public void init() throws ServletException {
    repositoryProvider = new RepositoryProvider(new File("")); // Read location from config file?
  }
  
  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    
    String pathInfo = req.getPathInfo();
    if (pathInfo.matches("\\.*git/content")) {
      handleContentRequest(req, resp);
    }
  }

  /**
   * Handles a request for the content of a file from a git repository.
   * @param req The HTTP request object.
   * @param resp The HTTP response object
   * 
   * @throws ServletException When failing to retrieve the file contents from the git repository.
   * @throws IOException
   */
  private void handleContentRequest(HttpServletRequest req,
      HttpServletResponse resp) throws ServletException, IOException {
    
    CredentialsProvider credentialsProvider = (CredentialsProvider) req.getSession()
        .getAttribute("git.credentials.provider");

    if (credentialsProvider == null) {
      resp.setStatus(403);
      resp.getWriter().write("You need to authorize before making requests.");
      return;
    }
    
    String requestBodyString = GithubUtil.inputStreamToString(req.getInputStream());
    HashMap<String, Object> requestBody = GithubUtil.parseJSON(requestBodyString);
    
    String repositoryUri = (String) requestBody.get("repositoryUri");
    String filePath = (String) requestBody.get("filePath");
    String branchName = (String) requestBody.get("branchName");
    
    try {
      Git git = repositoryProvider.getRepository(repositoryUri, credentialsProvider);
      Reader fileContent = gitAccess.getFileContents(git, branchName, filePath, credentialsProvider);

      ReaderInputStream inputStream = new ReaderInputStream(fileContent);
      
      resp.setHeader("X-FILE-SHA", "file-sha");
      IOUtils.copy(inputStream, resp.getOutputStream());
    } catch (TransportException e) {
      throw new ServletException("Failed to retrieve file content.");
    } catch (GitAPIException e) {
      throw new ServletException("Failed to retrieve file content.");
    }
  }
}
