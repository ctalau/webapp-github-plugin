package com.oxygenxml.examples.git;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ReaderInputStream;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.CredentialsProvider;

import ro.sync.ecss.extensions.api.webapp.plugin.WebappServletPluginExtension;
import ro.sync.servlet.StartupServlet;

import com.oxygenxml.examples.github.GithubUtil;

/**
 * Servlet class used to dispatch git requests to to methods.  
 * @author gabriel_titerlea
 */
public class RESTGitAccess extends WebappServletPluginExtension {

  /**
   * Provides access to git repositories.
   */
  static RepositoryProvider repositoryProvider;
  
  /**
   * Provides access to git commands.
   */
  public static GitAccess access;
  
  @Override
  public String getPath() {
    return "git";
  }
  
  @Override
  public void init() throws ServletException {
    ServletContext servletContext = getServletConfig().getServletContext();
    
    File tempDir = (File) servletContext
        .getAttribute(StartupServlet.JAVAX_SERVLET_CONTEXT_TEMPDIR);
    
    repositoryProvider = new RepositoryProvider(new File(tempDir, "git-repos-location"));
    access = new GitAccess(repositoryProvider);
  }
  
  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    
    CredentialsProvider credentialsProvider = (CredentialsProvider) req.getSession()
        .getAttribute("git.credentials.provider");

    if (credentialsProvider == null) {
      resp.setStatus(403);
      resp.getWriter().write("You need to authorize before making requests.");
      return;
    } 
    
    String pathInfo = req.getPathInfo();
    if (pathInfo.matches("\\.*git/content")) {
      handleContentRequest(req, resp, credentialsProvider);
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
      HttpServletResponse resp, CredentialsProvider credentialsProvider) throws ServletException, IOException {
    
    String requestBodyString = GithubUtil.inputStreamToString(req.getInputStream());
    HashMap<String, Object> requestBody = GithubUtil.parseJSON(requestBodyString);
    
    String repositoryUri = (String) requestBody.get("repositoryUri");
    String filePath = (String) requestBody.get("filePath");
    String branchName = (String) requestBody.get("branchName");
    
    try {
      Git git = repositoryProvider.getRepository(repositoryUri, credentialsProvider);
      Reader fileContent = access.getFileContents(git, branchName, filePath, credentialsProvider);

      ReaderInputStream inputStream = new ReaderInputStream(fileContent);
      
      resp.setHeader("OXY-FILE-SHA", "file-sha");
      IOUtils.copy(inputStream, resp.getOutputStream());
    } catch (TransportException e) {
      throw new ServletException("Failed to retrieve file content.");
    } catch (GitAPIException e) {
      throw new ServletException("Failed to retrieve file content.");
    }
  }
}
