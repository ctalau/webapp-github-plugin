package com.oxygenxml.examples.git;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;

/**
 * Provides access to git repositories. 
 * @author gabriel_titerlea
 */
public class GitAccess {

  /**
   * The name of the remote location.
   */
  private String remote = "origin";

  /**
   * Provides access to git repositories.
   */
  private RepositoryProvider repositoryProvider;
  
  /**
   * Constructor.
   * @param repositoryProvider
   */
  public GitAccess(RepositoryProvider repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }
  
  public void commitFile(String repoUri, String branchName, String filePath, 
      String fileContents, String commitMessage, CredentialsProvider credentialsProvider, String committer) {
    try {
      Git git = repositoryProvider.getRepository(repoUri, credentialsProvider);
      
      prepareRepository(git, branchName, credentialsProvider);
      File repoRootDir = getGitRepoDir(git);
      
      // Write the file contents on disk so we can commit it.
      File fileToCommit = new File(repoRootDir, filePath);
      
      FileWriter fileWriter = new FileWriter(fileToCommit);
      fileWriter.write(fileContents);
      fileWriter.close();
      
      // Stage the file for commit.
      git.add()
      .addFilepattern(filePath)
      .call();
      
      git.commit()
      .setCommitter(committer, "")
      .setAuthor(committer, "")
      .setMessage(commitMessage)
      .call();
      
      git.push()
      .setCredentialsProvider(credentialsProvider)
      .call();
    } catch (TransportException e) {
      // Throw to user
    } catch (IOException e) {
      // Throw to user
    } catch (GitAPIException e) {
      // Throw to user
    }
  }

  /**
   * Cleans the given repository in preparation for git actions on it.
   * TODO: document
   * 
   * @param branchName
   * @param credentialsProvider
   * @throws IOException
   * @throws TransportException
   * @throws GitAPIException
   * @throws CheckoutConflictException
   */
  private void prepareRepository(Git git, String branchName, CredentialsProvider credentialsProvider) throws IOException,
      TransportException, GitAPIException, CheckoutConflictException {
    Repository repository = git.getRepository();
    String activeBranch = repository.getBranch();
    
    if (!branchName.equals(activeBranch)) {
      switchToBranch(git, branchName, credentialsProvider);
    } else {
      git.reset()
      .setMode(ResetType.HARD)
      .call();
      
      git.clean()
      .setCleanDirectories(true)
      .call();  
    }
  }
  
  /**
   * Returns a reader over a file from a given git repository.
   * 
   * @param repositoryUri The URI of the repository to read from.
   * @param branchName The branch of the repository.
   * @param filePath The path of the file relative to the repository root.
   * @param credentialsProvider A credentials provider used to authenticate git requests.
   * 
   * @return A reader over a file from a given git repository.
   */
  public Reader getFileContents(Git git, String branchName, String filePath, CredentialsProvider credentialsProvider) {
    try {
      prepareRepository(git, branchName, credentialsProvider);
      
      git.pull()
      .call();
      
      File directory = getGitRepoDir(git);
      File fileToRead = new File(directory, filePath);
      
      return new FileReader(fileToRead);
    } catch (TransportException e) {
      // TODO: Throw it to the user.
    } catch (IOException e) {
      // TODO: Throw it to the user.
    } catch (GitAPIException e) {
      // TODO: Throw it to the user.
    }
    
    return null;
  }
  
  /**
   * Gets the root directory of a given git access object.
   * @param git The git access object.
   * @return The root directory of a given git access object.
   */
  public File getGitRepoDir(Git git) {
    return git.getRepository().getDirectory().getParentFile();
  }

  /**
   * Switches the working repository of the given Git access object to the given branch name.
   * (Package private to be visible in tests)
   * 
   * @param git The Git access object.
   * @param branchName The new branch to switch to.
   * @throws IOException
   */
  void switchToBranch(Git git, String branchName, CredentialsProvider credentialsProvider) throws IOException {
    Repository repository = git.getRepository();
    
    String branch = repository.getBranch();
    
    if (!branch.equals(branchName)) {
      try {
        try {
          // Create the branch locally so we can switch to it.
          git.branchCreate()
          .setName(branchName)
          .setStartPoint(remote + "/" + branchName)
          .call();
          
          switchBranchInternal(git, branchName);
        } catch (RefNotFoundException e) {
          createBranch(git, branchName, credentialsProvider);
        }
        
      } catch (RefAlreadyExistsException e) {
        // This exception is thrown only if setOrphan is set to true for the checkout command so its safe to ignore it here.
      } catch (InvalidRefNameException e) {
       
        // Throw back to the user
        
        e.printStackTrace();
      } catch (CheckoutConflictException e) {
        
        // This should not happen, need to revert everything before checkout
        
        e.printStackTrace();
      } catch (GitAPIException e) {
        
        // Tell the user it failed
        
        e.printStackTrace();
      }
    }
  }
  
  /**
   * Creates a new branch if it does not already exist.
   * 
   * @param git The git access object used to make the create branch reques.
   * @param branchName The name of the branch to create.
   * 
   * @throws RefNotFoundException
   * @throws InvalidRefNameException
   * @throws GitAPIException
   */
  public void createBranch(Git git, String branchName, CredentialsProvider credentialsProvider) throws RefNotFoundException, InvalidRefNameException, GitAPIException {
    try {
      // Create the new branch.
      git.branchCreate()
      .setName(branchName)
      .call();
    } catch (RefAlreadyExistsException e) {
      // Great, no need to create it then.
    }
    
    switchBranchInternal(git, branchName);
    
    // Push the newly created branch.
    git.push()
    .setCredentialsProvider(credentialsProvider)
    .call();
  }
  
  /**
   * Switches the local repository to the given branch.
   * @param git
   * @param branchName
   * @throws RefAlreadyExistsException
   * @throws RefNotFoundException
   * @throws InvalidRefNameException
   * @throws CheckoutConflictException
   * @throws GitAPIException
   */
  private void switchBranchInternal(Git git, String branchName) throws RefAlreadyExistsException, RefNotFoundException, InvalidRefNameException, CheckoutConflictException, GitAPIException {
    // Switch the working copy to the newly created branch.
    git.checkout()
    .setName(branchName)
    .call();
    
    // In case the branch already existed we revert any local changes.
    git.reset()
    .setMode(ResetType.HARD)
    .call();
    
    // Clean the local repository of any not-versioned files.
    git.clean()
    .setCleanDirectories(true)
    .call();
  }

  /**
   * Returns a list of the files from the given repository and path.
   * 
   * @param repositoryUri The repository URI.
   * @param branchName The branch.
   * @param path The path of the file relative to the repository root.
   * @param credentialsProvider Used to authenticate git requests.
   * @return A list of the files from the given repository and path.
   * 
   * @throws TransportException
   * @throws IOException
   * @throws GitAPIException
   */
  public File[] listFiles(String repositoryUri, String branchName, String path,
      CredentialsProvider credentialsProvider) throws TransportException, IOException, GitAPIException {

    Git git = repositoryProvider.getRepository(repositoryUri, credentialsProvider);
    prepareRepository(git, branchName, credentialsProvider);

    // Make sure we are serving the latest version of the files.
    git.pull()
    .setCredentialsProvider(credentialsProvider)
    .call();
    
    File rootDir = getGitRepoDir(git);
    File folderToListFileFor = new File(rootDir, path);

    File[] filesList = null;
    if (folderToListFileFor.isDirectory()) {
      filesList = folderToListFileFor.listFiles();
    }
    return filesList != null ? filesList : new File[0];
  }
}
