package com.adobe;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;


/**
 * 
 * Run the tool after stopping the AEM from the shell script. It will bring up the AEM in the past state.
 * 
 * Execute the tool in the following flavour:-
 * 
 * 1. Restate the AEM to previous state
 * 
 * 2. Restore the AEM to current state
 * 
 * @author saumukhe
 *
 */
public class RestoreTool {

	public static void main(String[] args) {
		
		Integer lineNumber = new Integer(0);
		String journalFilename = null;
		String lastLine = null;
		String lastToken = null;
		Path journalPath = null;
		Integer NUM_OF_ENTRIES_PER_MINUTE = new Integer(4);
		
		Scanner scanner = new Scanner(System.in);
		
		Path currentPath = Paths.get(".").toAbsolutePath().normalize();
		
		try {
		journalPath = currentPath.resolve("crx-quickstart/repository/segmentstore/journal.log");
		
		System.out.println("Found the journal file at location : "+journalPath.toString());
		
		journalFilename = journalPath.toString();
		
		} catch(Exception exception) {
			
		}
		
		int minutes = 0;
		
		System.out.println("Please provide how many minutes to go back :");
		
		String input = scanner.nextLine();
		
		
		
		try {
			minutes = Integer.parseInt(input);
			lineNumber = minutes * NUM_OF_ENTRIES_PER_MINUTE;
		} catch (Exception e) {
			System.out.println("Wrong entry found, exiting the application..");
			System.exit(0);
		}
		
		RandomAccessFile f;
		try {
			f = new RandomAccessFile(journalFilename, "rw");
			
			while(lineNumber !=0) {
				long length = f.length() - 1;
				byte b;
				do {                     
				  length -= 1;
				  f.seek(length);
				  b = f.readByte();
				} while(b != 10);
				f.setLength(length+1);
				lineNumber--;
			}
			f.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			FileInputStream in = new FileInputStream(journalFilename);
			BufferedReader br = new BufferedReader( new InputStreamReader(in));
			String strLine = null , tmp;
			while ((tmp = br.readLine()) != null ) {
			strLine = tmp; }
			lastLine = strLine;
//			System.out.println(lastLine);
			in.close(); 
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(lastLine != null) {
			lastToken = lastLine.substring(0, lastLine.indexOf(":"));
//			System.out.println(lastToken);
		}
		
		
		
		//Find the number in tar files
		File dir = journalPath.getParent().toFile();
		File [] files = dir.listFiles(new FilenameFilter() {
		    @Override
		    public boolean accept(File dir, String name) {
		        return name.startsWith("data") && name.contains(".tar");
		    }
		});

		Arrays.sort(files, new Comparator<File>() {
		    public int compare(File f1, File f2) {
		        return Long.compare(f1.lastModified(), f2.lastModified());
		    }
		});
		
		boolean isLastLineFound = false;
		
		try {
			
			for(File file:files) {
				System.out.println("Parsing the file : "+file.getName());
				System.out.println("------------------------------------");

				if(isLastLineFound) {
					System.out.println("Deleting the file : "+file.getName());
					file.delete();
				} else {
					File tempFolder = new File(file.getParentFile().getPath()+File.separator+"tempFolder");
					tempFolder.mkdir();
					
					/* Read TAR File into TarArchiveInputStream */
					File tempFile = new File(file.getParentFile().getPath()+File.separator+"tempFolder"+File.separator+file.getName());
					Path tarFile = Files.copy(file.toPath(), tempFile.toPath());
	                TarArchiveInputStream myTarFile=new TarArchiveInputStream(new FileInputStream(tarFile.toFile()));
	                
	                /* To read individual TAR file */
	                TarArchiveEntry entry = null;
	                String individualFiles;
	                List<String> tarEntriesList = new ArrayList();
	                
	                /* Create a loop to read every single entry in TAR file */
	                while ((entry = myTarFile.getNextTarEntry()) != null) {
	                	
	                		//System.out.println("Entry name : "+entry.getName());
	                	
	                        /* Get the name of the file */
	                        individualFiles = entry.getName();
	                        
	                        if(individualFiles.contains(lastToken) && !file.getName().endsWith(".bak")) {
	                        	System.out.println("Found a match with filename = "+individualFiles);
	                        	isLastLineFound = true;
	                        	
	                        	File curfile = new File(tempFolder, individualFiles); 
	                        	FileOutputStream out = new FileOutputStream(curfile);
	                        	IOUtils.copy(myTarFile, out);
	                        	out.close();
	                        	
		                        tarEntriesList.add(curfile.getAbsolutePath());
	                        }
	                        
	                        if(!isLastLineFound) {
		                        File curfile = new File(tempFolder, individualFiles); 
	                        	FileOutputStream out = new FileOutputStream(curfile);
	                        	IOUtils.copy(myTarFile, out);
	                        	out.close();
	                        	tarEntriesList.add(curfile.getAbsolutePath());
	                        } else {//A match is now found. Parse for Graph and Index file
	                        	if(individualFiles.endsWith(".tar.gph") || individualFiles.endsWith(".tar.idx")) {
	                        		File curfile = new File(tempFolder, individualFiles); 
		                        	FileOutputStream out = new FileOutputStream(curfile);
		                        	IOUtils.copy(myTarFile, out);
		                        	out.close();
		                        	tarEntriesList.add(curfile.getAbsolutePath());
	                        	}
	                        }
	                }               
	                /* Close TarAchiveInputStream */
	                myTarFile.close();
	                
	                //Deletes the tar file
	                tempFile.delete();
	                
	                if(!isLastLineFound) {
	                	
	                	tarEntriesList.clear();
	                	
		                //Delete all contained files
		                for(File childFile:tempFolder.listFiles()){
		                	childFile.delete();
		                }
		                
//		                System.out.println("About to delete the temp file : "+tempFolder.getPath());
		                boolean isdeleted = tempFolder.delete();
		                if(!isdeleted) {
		                	System.out.println("Could not delete the temp folder. Exiting...");
		                	System.exit(0);
		                }
	                }
	                else {//Last line is found
	                	String filePath = file.getAbsolutePath();
	                	
	                	//Now remove the tar file
	                	file.delete();
	                	
	                	File archiveFile = new File(filePath);
	                	
	                	//Tar back the contents into the tar file
	                	TarArchiveOutputStream out = new TarArchiveOutputStream(new FileOutputStream(archiveFile));
	                	
	                	for(String entryFilePath: tarEntriesList) {
	                		addFileToTar(out, entryFilePath, "");
	                	}
	                	
	                	out.finish();
	                	out.close();
	                	
	                	//Delete all contained files
		                for(File childFile:tempFolder.listFiles()){
		                	childFile.delete();
		                }
	                }
	                
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private static void addFileToTar(TarArchiveOutputStream tOut, String path, String base) throws IOException
    {
        File f = new File(path);
        String entryName = base + f.getName();
        TarArchiveEntry tarEntry = new TarArchiveEntry(f, entryName);

        tOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

        if(f.isFile())
        {
           tarEntry.setModTime(0);
           tOut.putArchiveEntry(tarEntry);

           IOUtils.copy(new FileInputStream(f), tOut);

           tOut.closeArchiveEntry();
        }
        else
        {
            File[] children = f.listFiles();
            Arrays.sort(children);

            if(children != null)
            {
                for(File child : children)
                {
                    addFileToTar(tOut, child.getAbsolutePath(), entryName + "/");
                }
            }
        }
    }
}
