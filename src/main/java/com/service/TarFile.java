/**
 * This class is used to copy an archive and script to a remote host.
 * The script will be executed on the the remote host which will
 * install the contents of the tar file then the script will clean up
 * after itself.
 */

package com.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

public class TarFile {

/**
 * Reference to the log4j logger
 */
  private static final Logger LOG = Logger.getLogger(TarFile.class);

/**
 * Reference to ChannelSftp
 */
  private static ChannelSftp sftpChannel;

/**
 * Reference to session
 */
  private static Session session = null;

/**
 * Reference to remote user
 */
  private static final String USER = "root";

/**
 * Reference to remote password
 */
  private static final String PWD = "password";
	  	  

/**
 * An IP Address, archive and shell script are passed to this class at runtime.
 * The archive and script are copied to the remote host via the IP Address that is passed
 * to the class. The script is run on the remote host. The script probably
 * will untar the archive and distribute its contents on the remote host. The script
 * then would delete the the archive, the untarred archive and the script itself.
 */
  public static void main(String[] args) {

    if (args.length == 3) { 
      try {
        String archive = args[1];
        String folder = archive.substring(0, archive.length() - 7);
        String script = args[2];

        extract(archive);
        connectToServer(args[0]);
        putFile(archive);
        putFile(folder + "/" + script);		
        setPerm("/home/" + script);	
        runShell("/home/" + script);
        cleanUp(folder);

      } finally {
        sftpChannel.exit();
        session.disconnect();
      }
    } else {
        System.out.println("The proper arguments were not passed to the Jar. Required arguments are IP Address, archive file and script.");
        System.out.println("Usage: java -jar InstallRemoteTar.jar <IP Address> <archive.tar.gz> <install.sh>");
        System.exit(0);
    }
  }

/**
 * This method extracts the tar file locally to get the shell script which will be
 * copied to the remote host.
 * @param tarFile The archive
 */
  public static void extract(final String tarFile) {

    InputStream in = null;
    TarArchiveInputStream tin = null;
    FileOutputStream outputStream = null;
    TarArchiveEntry entry = null;

    try {			  		 
      in = new FileInputStream(new File(tarFile));			  		  
      tin = new TarArchiveInputStream(new GzipCompressorInputStream(in));

      while ((entry = (TarArchiveEntry) tin.getNextEntry()) != null) {

        File outputFile = new File(entry.getName());

        if (entry.isDirectory()) {
            if (!outputFile.exists()) {
            	outputFile.mkdirs();
            }
        } else {
          outputStream = new FileOutputStream(outputFile); 
          IOUtils.copy(tin, outputStream);
        }
      }
    } catch (FileNotFoundException e) {		
      LOG.error("FileNotFoundException: " + e.getMessage());
    } catch (IOException e) {
      LOG.error("IOException: " + e.getMessage());

    } finally {		  
      IOUtils.closeQuietly(in);
      IOUtils.closeQuietly(tin);
      IOUtils.closeQuietly(outputStream);		    
    }
  }

/**
 * This method runs a shell script on a remote host.
 * @param fileName The shell script
 */
  private static void runShell(String fileName) {			

    Channel channel = null;

    try {
      channel = session.openChannel("exec");
    } catch (JSchException e) {
      LOG.error("JSchException: " + e.getMessage());
    }
    ((ChannelExec) channel).setCommand(fileName);
    ((ChannelExec) channel).setErrStream(System.err);
    ((ChannelExec) channel).setOutputStream(System.out);

    try {
      channel.connect();
    } catch (JSchException e) {
      LOG.error("JSchException: " + e.getMessage());

    } finally {
      channel.disconnect();		
    }
  }

/**
 * This method creates a secure connection to a remote host.
 * @param host The remote host
 */
  private static void connectToServer(final String host) {

    JSch jsch = null;    

    try {
      jsch = new JSch();
      session = jsch.getSession(USER, host, 22);
      session.setPassword(PWD);
      session.setConfig("StrictHostKeyChecking", "no");
      session.connect();

      if (LOG.isInfoEnabled()) {
        LOG.info("Connection established to: " + host);
      }
      sftpChannel = (ChannelSftp) session.openChannel("sftp");
      sftpChannel.connect();
    } catch (JSchException e) {
      LOG.error("JSchException: " + e.getMessage());
    }
  }

/**
 * This method copies a file to a remote host.
 * @param fileName The file to copy
 */
  private static void putFile(final String fileName) {

    File file = new File(fileName);

    try {
      sftpChannel.cd("/home");
      sftpChannel.put(new FileInputStream(file), file.getName(), 775);
      LOG.info("File transfered successfully to host.");   

    } catch (FileNotFoundException e) {
      LOG.error("FileNotFoundException: " + e.getMessage());
    } catch (SftpException e) {
      LOG.error("SftpException: " + e.getMessage());
    }
  }

/**
 * This method sets the permissions on the script that is copied to the remote host.
 * @param fileName The script
 */
  private static void setPerm(final String fileName) {

    try {
      sftpChannel.chmod(Integer.parseInt("777", 8), fileName);

    } catch (SftpException e) {
      LOG.error("SftpException: " + e.getMessage());
    }
  }	  

/**
 * This method cleans up on the local server.
 * @param folder The folder where the archive is extracted
 */
  private static void cleanUp(final String folder) {

    try {
      FileUtils.deleteDirectory(new File(folder));
    } catch (IOException e) {
      LOG.error("IOException: " + e.getMessage());
    }			
  }
}
