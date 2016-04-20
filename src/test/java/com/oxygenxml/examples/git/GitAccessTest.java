package com.oxygenxml.examples.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.Test;

/**
 * Tests for git access methods.
 * 
 * @author gabriel_titerlea
 */
public class GitAccessTest {
  
  /**
   * Name of the test user.
   */
  private static final String username = "g-tit-oxygen";
  
  /**
   * Password of the test user.
   */
  private static final String password = "Oxygen18";
  
  /**
   * The directory where repositories will be cloned.
   */
  private static final File reposDirectory = new File("test/reposLocation");
  
  /**
   * Repository provider needed by git access.
   */
  private RepositoryProvider repositoryProvider = new RepositoryProvider(reposDirectory);
  
  /**
   * Credentials provider user to authenticate git requests.
   */
  private CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(username, password);
  
  /**
   * The URI of the repository used for tests.
   */
  private static final String REPOSITORY_URI = "https://github.com/g-tit-oxygen/GitAccessTests.git";

  /**
   * <p><b>Description:</b> Tests the the switch branch method successfully switches to a new branch.</p>
   * <p><b>Bug ID:</b>WA-84</p>
   *
   * @author gabriel_titerlea
   *
   * @throws Exception When it fails.
   */
  @Test
  public void testSwitchToBranch() throws Exception {
    final String CURRENT_BRANCH_NAME = "master";
    final String NEW_BRANCH_NAME = "secora_non_existent";
    
    FileUtils.cleanDirectory(reposDirectory);
    
    GitAccess gitAccess = new GitAccess(repositoryProvider);
    Git git = repositoryProvider.getRepository(REPOSITORY_URI, credentialsProvider);

    String currentBranch = git.getRepository().getBranch();
    assertTrue(CURRENT_BRANCH_NAME.equals(currentBranch));
    
    gitAccess.switchToBranch(git, NEW_BRANCH_NAME, credentialsProvider);
    String newBranch = git.getRepository().getBranch();
    assertTrue(NEW_BRANCH_NAME.equals(newBranch));
  }
  
  /**
   * <p><b>Description:</b> Tests the generic git commit file method commits a file successfully.</p>
   * <p><b>Bug ID:</b>WA-84</p>
   *
   * @author gabriel_titerlea
   *
   * @throws Exception When it fails.
   */
  @Test
  public void testCommitFile() throws Exception {
    String branchName = "master";
    String filePath = "flowers/topics/care.dita";
    String commitMessage = "Testing git commit file api.";
    String committer = "g-tit-oxygen";
    
    FileUtils.cleanDirectory(reposDirectory);
    
    GitAccess gitAccess = new GitAccess(repositoryProvider);
    Git git = repositoryProvider.getRepository(REPOSITORY_URI, credentialsProvider);
    
    File directory = gitAccess.getGitRepoDir(git);
    String fileContentOnDisk = FileUtils.readFileToString(new File(directory, filePath));
    
    String fileContents = "odd".equals(fileContentOnDisk) ? "even" : "odd";
    
    gitAccess.commitFile(REPOSITORY_URI, branchName, filePath, fileContents, commitMessage, credentialsProvider, committer);

    // After committing do a pull to make sure that the file was indeed committed.
    git.reset().setMode(ResetType.HARD).call();
    git.pull().setCredentialsProvider(credentialsProvider).call();
    
    fileContentOnDisk = FileUtils.readFileToString(new File(directory, filePath));
    
    assertEquals(fileContents, fileContentOnDisk);
  }
}
