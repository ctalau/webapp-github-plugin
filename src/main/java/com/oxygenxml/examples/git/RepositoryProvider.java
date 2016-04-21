package com.oxygenxml.examples.git;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.transport.CredentialsProvider;

/**
 * Provides access to local git repositories.
 * @author gabriel_titerlea
 */
public class RepositoryProvider {
  
  /**
   * The directory in which repositories will be cloned.
   */
  private File reposDir;
  
  /**
   * Constructor.
   * @param cloneLocation The directory where git repositories will be cloned locally.
   */
  public RepositoryProvider(File cloneLocation) {
    if (cloneLocation.exists() && !cloneLocation.isDirectory()) {
      throw new IllegalArgumentException("The clone location file should be a directory.");
    }

    cloneLocation.mkdirs();
    this.reposDir = cloneLocation;
  }
  
  /**
   * Returns a Git repository access object for the given repository URL.
   * 
   * @param repositoryUri The URI of the repository.
   * @param branchName The branch of the repository.
   * @param credentialsProvider Credentials provider with which to authenticate git requests.
   * 
   * @return A Git repository access object.
   * 
   * @throws IOException
   * @throws TransportException
   * @throws GitAPIException
   */
  public Git getRepository(String repositoryUri, CredentialsProvider credentialsProvider) throws IOException, TransportException, GitAPIException {
    // repository URIs contain characters which can't be included in file names
    String repoDirName = "r" + repositoryUri.hashCode();
    
    File repositoryDir = new File(reposDir, repoDirName);

    if (!repositoryDir.exists()) {
      cloneRepository(repositoryUri, repositoryDir, credentialsProvider);
    }
    
    try {
      return Git.open(repositoryDir);
    } catch (RepositoryNotFoundException e) {
      // If the repository directory contains some files, but not all the .git files (It was not properly deleted)
      FileUtils.cleanDirectory(repositoryDir);
      cloneRepository(repositoryUri, repositoryDir, credentialsProvider);
      
      return Git.open(repositoryDir);
    }
  }

  /**
   * Clones a repository from the given URI to the given repository directory.
   * 
   * @param repositoryUri The URI of the repository to clone.
   * @param branchName The name of the branch to clone.
   * @param repositoryDir The directory in which to clone the repository.
   * @param credentialsProvider Used to authenticate the clone request.
   * 
   * @throws InvalidRemoteException
   * @throws TransportException
   * @throws GitAPIException
   */
  private void cloneRepository(String repositoryUri, File repositoryDir, CredentialsProvider credentialsProvider) throws InvalidRemoteException, TransportException, GitAPIException {
    Git.cloneRepository()
    .setURI(repositoryUri)
    .setCloneAllBranches(true)
    .setDirectory(repositoryDir)
    .setCredentialsProvider(credentialsProvider)
    .call();
  }
}
