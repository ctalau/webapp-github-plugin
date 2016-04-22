package com.oxygenxml.examples.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
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
   * Provides access to git repositories.
   */
  private GitAccess gitAccess = new GitAccess(repositoryProvider);
  
  /**
   * Credentials provider user to authenticate git requests.
   */
  private CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(username, password);
  
  /**
   * The URI of the repository used for tests.
   */
  private static final String REPOSITORY_URI = "https://github.com/g-tit-oxygen/GitAccessTests.git";

  /**
   * The URI of the second repository used for tests.
   */
  private static final String REPOSITORY_URI_2 = "git://github.com/g-tit-oxygen/GitAccessTest2.git";

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
    final String NEW_BRANCH_NAME = "secora_non_existent";

    Git git = repositoryProvider.getRepository(REPOSITORY_URI, credentialsProvider);
    
    gitAccess.switchToBranch(git, NEW_BRANCH_NAME, credentialsProvider);
    String newBranch = git.getRepository().getBranch();
    assertTrue(NEW_BRANCH_NAME.equals(newBranch));
    
    git.close();
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
    
    git.close();
  }
  
  /**
   * <p><b>Description:</b> Tests that the listFiles method correctly returns the files in a directory.</p>
   * <p><b>Bug ID:</b>WA-560</p>
   *
   * @author gabriel_titerlea
   *
   * @throws Exception When it fails.
   */
  @Test
  public void testListFiles() throws Exception {
    String branchName = "1000+";
    String path = "more_files";
    
    File[] listFiles = gitAccess.listFiles(REPOSITORY_URI, branchName, path, credentialsProvider);
    
    assertEquals(1200, listFiles.length);
  }
  
  /**
   * <p><b>Description:</b> Tests that the listFiles method returns correctly when called concurrently from multiple threads.</p>
   * <p><b>Bug ID:</b>WA-560</p>
   *
   * @author gabriel_titerlea
   *
   * @throws Exception When it fails.
   */
  @Test
  public void testListFilesConcurrent() throws Exception {
    final String branchName = "secora_non_existent";
    final String path = "flowers/tasks";
    
    final String branchName2 = "master";
    final String path2 = "topics";

    final String branchName3 = "secora";
    final String path3 = "flowers/concepts";
    
    final HashMap<String, Integer> nrFiles = new HashMap<String, Integer>();
    
    Thread t1 = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          nrFiles.put("t1", gitAccess.listFiles(REPOSITORY_URI, branchName, path, credentialsProvider).length);
        } catch (TransportException e) {
        } catch (IOException e) {
        } catch (GitAPIException e) {
        }
      }
    });

    Thread t2 = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          nrFiles.put("t2", gitAccess.listFiles(REPOSITORY_URI_2, branchName2, path2, credentialsProvider).length);
        } catch (TransportException e) {
        } catch (IOException e) {
        } catch (GitAPIException e) {
        }
      }
    });
    
    Thread t3 = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          nrFiles.put("t3", gitAccess.listFiles(REPOSITORY_URI, branchName3, path3, credentialsProvider).length);
        } catch (TransportException e) {
        } catch (IOException e) {
        } catch (GitAPIException e) {
        }
      }
    });
    
    t1.start();
    t2.start();
    t3.start();
    
    t1.join();
    t2.join();
    t3.join();
    
    assertEquals((Integer) 2, (Integer) nrFiles.get("t1"));
    assertEquals((Integer) 5, (Integer) nrFiles.get("t2"));
    assertEquals((Integer) 12, (Integer) nrFiles.get("t3"));
  }
}
