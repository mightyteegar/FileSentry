/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package filesentry;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.codec.digest.DigestUtils;



/**
 *
 * @author dave
 */
public class FileSentry {
    
    static File PATH_LIST_INI = new File("path_list.txt").getAbsoluteFile();
    static File FILE_CHECK_LIST = new File(".\\file_check_list.txt");
    
    public void getPathList() throws FileNotFoundException, IOException {
        
        System.out.println(PATH_LIST_INI);
        
        if (!PATH_LIST_INI.exists()) {
            System.out.println("path_list.txt not found, must be in same directory as program file");
            System.exit(0);
        }
        else {
            System.out.println("path_list.txt found, continuing");
        }
        
        FileReader fileReader = new FileReader(PATH_LIST_INI);
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        
        List<String> listOfPaths = new ArrayList<String>();
        
        String line = null;        
        
        while ((line = bufferedReader.readLine()) != null ) {
            System.out.println("Adding path " + line + " to list");
            listOfPaths.add(line);
        }
        
        bufferedReader.close();
        
        for (String pathString : listOfPaths) {
            File filePath = new File(pathString);
            if (!filePath.exists()) { 
                System.out.println(filePath.toString() + " not found, skipping");
                break; 
            }
            else {
                walkDirectory(filePath);
            }
            
        }

       
    }
    
    public void walkDirectory(File rootDir) throws IOException {
        
        File[] listOfFiles = rootDir.listFiles();
        for (File file : listOfFiles) {
            if (file.exists() && file.isDirectory()) {
                try {
                    walkDirectory(file);
                }
                catch (Exception e) {
                    Logger.getLogger(FileSentry.class.getName()).log(Level.SEVERE, null, e);
                }
            }
            else if (file.exists() && file.isFile()) {
                checkFile(file);
                
                // do file hashing and checking stuff here
            }
            else {
                System.out.println("File " + file.getName() + " is not a file or directory, skipping");
            }
        }
        
    }
    
    public void checkFile(File fileToCheck) throws FileNotFoundException, IOException {
        
        
        DigestUtils md = new DigestUtils();
        FileInputStream fis = new FileInputStream(fileToCheck);
        byte[] dataBytes = new byte[1024];
        
        int nread = 0; 
 
        while ((nread = fis.read(dataBytes)) != -1) {
            
        };
        
        String fileShaHash = DigestUtils.sha1Hex(dataBytes);
        
        
        System.out.println("FILE: " + fileToCheck.getAbsolutePath() + " |  HASH: " + fileShaHash);
        
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
        
        FileSentry fileSentry = new FileSentry();
        try {
            fileSentry.getPathList();
            System.out.println("Reading path_list.txt...");
        } catch (IOException ex) {
            Logger.getLogger(FileSentry.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
}
