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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

/**
 *
 * @author dave
 */
public class FileSentry {
    
    static File PATH_LIST_INI = new File("path_list.txt").getAbsoluteFile();
    
    Connection connection = null;
    
    private Map<String, Integer> fileModCounters = new HashMap<>();
    private Integer sentryOpId;   
    private String computerName = new String();
    
    public Integer getSentryOpId() {
        return sentryOpId;
    }

    public String getComputerName() {
        return computerName;
    }

    public void setComputerName() {
        
        String computerName = "UNKNOWN";

        try
        {
            InetAddress addr;
            addr = InetAddress.getLocalHost();
            computerName = addr.getHostName();
            
        }
        catch (UnknownHostException ex)
        {
            System.out.println("Hostname can not be resolved");
        }
        this.computerName = computerName;
    }
    
    
    public Integer getFileDelta(String key) {
        return fileModCounters.get(key);
    }

    public void setFileDelta(String key, Integer count) {
        fileModCounters.put(key, count);
    }
    
    public void incrementFileDelta(String key) {
        Integer incrementValue = fileModCounters.get(key) + 1;
        fileModCounters.put(key, incrementValue);
    }
    
    public FileSentry() {
        this.setFileDelta("totalFiles", 0);
        this.setFileDelta("newFiles", 0);
        this.setFileDelta("changedFiles", 0);
        this.setFileDelta("removedFiles", 0);
    }
    
    public String getCurrentDateTime() {
        LocalDateTime today = new LocalDateTime();
        DateTimeFormatter dtf = ISODateTimeFormat.dateTimeNoMillis();
        String todayString = dtf.print(today);
        todayString = todayString.replace("T"," ");
        return todayString;
    }
    
    public void initDatabase() throws SQLException {
        
        connection = DriverManager.getConnection("jdbc:sqlite:filesentry.db");
        Statement statement = connection.createStatement();
        statement.setQueryTimeout(30);
        
        statement.executeUpdate("drop table if exists notable");
        
        String createFileHashesTable = null;
        String createSentryLogTable = null;
        String createSentryOpsTable = null;
        String createPathListTable = null;
        
        createSentryOpsTable = "create table if not exists sentry_ops"
                + "(rowid integer primary key,"
                + "computer_name text,"
                + "run_date text not null)";
        
        createFileHashesTable = "create table if not exists file_hashes";
        createFileHashesTable += "(file_path text not null,";
        createFileHashesTable += "file_path_hash text primary key,";
        createFileHashesTable += "file_bit_hash text not null,";
        createFileHashesTable += "flag_file_found int default 1,";
        createFileHashesTable += "flag_as_changed int default 0,";
        createFileHashesTable += "last_modified text,";
        createFileHashesTable += "last_modified_by text,";
        createFileHashesTable += "last_hash_date text)";
        
        createSentryLogTable = "create table if not exists sentry_log" +
                "(rowid integer primary key," +
                "computer_name text not null," +
                "file_operation text not null," +
                "op_date text," +
                "sentry_op_id integer," +
                "file_name text not null," +
                "file_name_hash text not null," +
                "file_bit_hash_prev text," +
                "file_bit_hash_curr text," +
                "file_modified_date text)";
        
        statement.execute(createSentryOpsTable);
        statement.execute(createFileHashesTable);
        statement.execute(createSentryLogTable);
       
        connection.close();
        
    }
    
    public void logEntry(Map<String,Object> logEntryData) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:filesentry.db");
        Statement statement = connection.createStatement();
        String logEntrySql = "insert into sentry_log ("
                + "computer_name, file_operation, op_date, sentry_op_id, file_name, file_name_hash,"
                + "file_bit_hash_prev, file_bit_hash_curr, file_modified_date) values ("
                + "'" + this.getComputerName() + "', "
                + "'" + logEntryData.get("file_operation") + "',"
                + "'" + logEntryData.get("op_date") + "',"
                + "" + this.getSentryOpId() + ","
                + "'" + logEntryData.get("file_name") + "',"
                + "'" + logEntryData.get("file_name_hash") + "',"
                + "'" + logEntryData.get("file_bit_hash_prev") + "',"
                + "'" + logEntryData.get("file_bit_hash_curr") + "',"
                + "'" + logEntryData.get("file_modified_date") + "')";
        System.out.println("## DEBUG: " + logEntrySql); 
        statement.executeUpdate(logEntrySql);
        
    }
    
    public void resetFoundFlags() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:filesentry.db");
        Statement statement = connection.createStatement();
        
        String resetFoundFlagsSql = "update file_hashes set flag_file_found = 0 where 1";
        statement.executeUpdate(resetFoundFlagsSql);
        statement.close();
        connection.close();
    }
    
    public void newSentryOpRecord() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:filesentry.db");
        Statement statement = connection.createStatement();
        Statement getOpStmt = connection.createStatement();
        
        String createSentryOpSql = "insert into sentry_ops (computer_name, run_date)"
                + "values ('" + this.getComputerName() + "', "
                + "'" + getCurrentDateTime() + "')";
        String getSentryOpSql = "select rowid from sentry_ops order by rowid desc limit 1";
        
        System.out.println("## DEBUG: " + createSentryOpSql);
        statement.executeUpdate(createSentryOpSql);
        statement.close();
        
        ResultSet rs = getOpStmt.executeQuery(getSentryOpSql);
        Integer rowId = rs.getInt(1);
        this.sentryOpId = rowId;  
        System.out.println("## DEBUG: Sentry Op ID " + rowId); 
        
        getOpStmt.close();
        connection.close();
    }
    
    public void getPathList() throws FileNotFoundException, IOException, SQLException {
        
        System.out.println(PATH_LIST_INI);
        
        if (!PATH_LIST_INI.exists()) {
            System.out.println("path_list.txt not found, must be in same directory as FileSentry.jar" +
                    "and contain a list of paths to scan, one per line.");
            System.exit(0);
        }
        else {
            System.out.println("path_list.txt found, proceeding with path scan");
            this.newSentryOpRecord();
        }
        
        FileReader fileReader = new FileReader(PATH_LIST_INI);
        List<String> listOfPaths;
        try (BufferedReader bufferedReader = new BufferedReader(fileReader)) {
            listOfPaths = new ArrayList<>();
            String line = null;
            while ((line = bufferedReader.readLine()) != null ) {
                System.out.println("Adding path " + line + " to list");
                listOfPaths.add(line);
            }
        }
        
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
            
            if (file.exists() && file.isDirectory() && file.canRead()) {
                try {
                    walkDirectory(file);
                }
                catch (IOException | NullPointerException e) {
                    Logger.getLogger(FileSentry.class.getName()).log(Level.SEVERE, null, e);
                }
            }
            else if (file.exists() && file.isFile()) {
                this.incrementFileDelta("totalFiles");
                System.out.println("** >> Reading file " + file.getAbsolutePath());
                try {
                    checkFile(file);
                    
                    // do file hashing and checking stuff here
                } catch (FileNotFoundException | SQLException | NullPointerException ex) {
                    Logger.getLogger(FileSentry.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            else {
                System.out.println("File " + file.getName() + " is not a file or cannot be read, skipping");
            }
        }
        
    }
    
    public void checkFile(File fileToCheck) throws FileNotFoundException, IOException, SQLException {
        
        
        connection = DriverManager.getConnection("jdbc:sqlite:filesentry.db");
        Statement statement = connection.createStatement();
        
        DateTime today = new DateTime();
        DateTimeFormatter dtf = ISODateTimeFormat.dateTime();
        String todayString = dtf.print(today);
        
        long fileModifiedDate = fileToCheck.lastModified();
        DateTimeFormatter thisDtf = ISODateTimeFormat.dateTimeNoMillis();
        String fileModDateString = thisDtf.print(fileModifiedDate);
                
        DigestUtils md = new DigestUtils();
        FileInputStream fis = new FileInputStream(fileToCheck);
        byte[] dataBytes = new byte[1024];
        
        int nread = 0; 
 
        while ((nread = fis.read(dataBytes)) != -1) {};
        
        String fileShaHash = DigestUtils.sha1Hex(dataBytes);
        String fileNameShaHash = DigestUtils.sha1Hex(fileToCheck.getAbsolutePath());
        
        String findMatchingHashSql = "select * from file_hashes where file_path_hash = '" + fileNameShaHash + "' limit 1";
        ResultSet fileLookupResult = statement.executeQuery(findMatchingHashSql);
        
        String dbFileShaHash = null;
        String dbFileNameShaHash = null;
        
        int match = 0;
        while (fileLookupResult.next()) {
            dbFileNameShaHash = fileLookupResult.getString("file_path_hash");
            dbFileShaHash = fileLookupResult.getString("file_bit_hash");
            System.out.println("FILE PATH: " + fileToCheck.getAbsolutePath());
            System.out.println("FILE HASH: " + fileShaHash + " : DB HASH: " + dbFileShaHash);
            match++;
        }
        
        if (match > 0) {
            // File was found in the database, let's check it
            if (!fileShaHash.equals(dbFileShaHash)) {
                // File has changed since we last ran FileSentry, let's update the database record
                
                String flagAsChangedSql = "update file_hashes set flag_as_changed = 1, "
                        + "file_bit_hash = '" + fileShaHash + "', "
                        + "last_modified = '" + fileModDateString + "', "
                        + "last_hash_date = '" + todayString + "' "
                        + "where file_path_hash = '" + dbFileNameShaHash + "'";
                try (Statement flagUpdateStmt = connection.createStatement()) {
                    flagUpdateStmt.executeUpdate(flagAsChangedSql);
                }
                this.incrementFileDelta("changedFiles");
                Map<String,Object> logEntryMap = new HashMap<>();
                logEntryMap.put("file_operation", "changed");
                logEntryMap.put("op_date", todayString);
                logEntryMap.put("sentry_op_id", this.getSentryOpId());
                logEntryMap.put("file_name", fileToCheck.getAbsolutePath());
                logEntryMap.put("file_name_hash", fileNameShaHash);
                logEntryMap.put("file_bit_hash_prev", dbFileShaHash);
                logEntryMap.put("file_bit_hash_curr", fileShaHash);
                logEntryMap.put("file_modified_date", fileModDateString);
                this.logEntry(logEntryMap);
                System.out.println("-- updating database with new file hash");
            }
            
        }
        else {
            // File was not found in the database, let's add it
            System.out.println("FILE PATH: " + fileToCheck.getAbsolutePath());
            System.out.println("File not in hashes database, adding new record for it");
            String updateRecordSql = "insert into file_hashes "
                    + "(file_path, file_path_hash, file_bit_hash, last_modified, last_hash_date, flag_file_found) "
                    + "values ('" + fileToCheck.getAbsolutePath() + "', "
                    + "'" + fileNameShaHash + "', "
                    + "'" + fileShaHash + "', "
                    + "'" + fileModDateString + "', "
                    + "'" + todayString + "', "
                    + "1)";
            
            try (Statement updateStmt = connection.createStatement()) {
                updateStmt.executeUpdate(updateRecordSql);
            }
            this.incrementFileDelta("newFiles");
            Map<String,Object> logEntryMap = new HashMap<>();
            logEntryMap.put("file_operation", "new");
            logEntryMap.put("op_date", todayString);
            logEntryMap.put("sentry_op_id", this.getSentryOpId());
            logEntryMap.put("file_name", fileToCheck.getAbsolutePath());
            logEntryMap.put("file_name_hash", fileNameShaHash);
            logEntryMap.put("file_bit_hash_prev", "");
            logEntryMap.put("file_bit_hash_curr", fileShaHash);
            logEntryMap.put("file_modified_date", fileModDateString);
            this.logEntry(logEntryMap);
        }
        
        System.out.println("-------------------------------------------------------------------------------------");
        String fileFoundUpdateSql = "update file_hashes set flag_file_found = 1 where file_path_hash = '" + dbFileNameShaHash + "'";
        Statement fileFoundStmt = connection.createStatement();
        fileFoundStmt.executeUpdate(fileFoundUpdateSql);
        fileFoundStmt.close();
                
        statement.close();
        connection.close();
    }
    
    public void removeDeadFileRecords() throws SQLException {
        
        connection = DriverManager.getConnection("jdbc:sqlite:filesentry.db");
        try (Statement statement = connection.createStatement()) {
            String countDeadRecordsSql = "select count(*) from file_hashes where flag_file_found = 0";
            String deleteDeadRecordsSql = "delete from file_hashes where flag_file_found = 0";
            ResultSet deadCountResult = statement.executeQuery(countDeadRecordsSql);
            this.setFileDelta("removedFiles", deadCountResult.getInt(1));
            statement.executeUpdate(deleteDeadRecordsSql);
            // TODO: routine to log deleted files here
        }
        connection.close();
    }
    
    public void sendEmail(
            String toAddress,
            String mailServer,
            int smtpPort,
            String smtpUser,
            String smtpPass
            ) throws EmailException {
        
        SimpleEmail simpleEmail = new SimpleEmail();
        
        String computerName = "UNKNOWN";

        try
        {
            InetAddress addr;
            addr = InetAddress.getLocalHost();
            computerName = addr.getHostName();
            
        }
        catch (UnknownHostException ex)
        {
            System.out.println("Hostname can not be resolved");
        }
        
        LocalDateTime today = new LocalDateTime();
        DateTimeFormatter dtf = ISODateTimeFormat.dateTimeNoMillis();
        String todayString = dtf.print(today);
        todayString = todayString.replace("T"," ");
        
        String emailMsg = "FileSentry Report for Computer " + computerName
                + "\nReport generated " + todayString;
        
        
//        simpleEmail.setHostName(mailServer);
//        simpleEmail.setSmtpPort(smtpPort);
//        simpleEmail.setSSLOnConnect(true);
//        simpleEmail.setStartTLSEnabled(true);
//        simpleEmail.setAuthentication(smtpUser,smtpPass);
//        simpleEmail.setFrom(smtpUser);
//        simpleEmail.setSubject("FileSentry Report for Computer " + computerName);
//        simpleEmail.addTo("baneofspam@gmail.com");
//        simpleEmail.setMsg(emailMsg);
//        
//        simpleEmail.send();
        
    }

    
    public static void main(String[] args) throws FileNotFoundException, SQLException {
        
        Instant startTime = new Instant();
               
        FileSentry fileSentry = new FileSentry();
        
        try {
            fileSentry.initDatabase();
        } catch (SQLException ex) {
            Logger.getLogger(FileSentry.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        
        try {
            try {
                fileSentry.resetFoundFlags();
            } catch (SQLException ex) {
                Logger.getLogger(FileSentry.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            fileSentry.getPathList();
            System.out.println("File hash checking complete.");
        } catch (IOException ex) {
            Logger.getLogger(FileSentry.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        System.out.println("Deleting records with no matching files...");
        try {
            fileSentry.removeDeadFileRecords();
        } catch (SQLException ex) {
            Logger.getLogger(FileSentry.class.getName()).log(Level.SEVERE, null, ex);
        }
        
//        try {
//            fileSentry.sendEmail("dave.baker@maybanksystems.com", "mail.maybanksystems.com", 587, "testalerts@maybanksystems.com", "Password123");
//        } catch (EmailException ex) {
//            Logger.getLogger(FileSentry.class.getName()).log(Level.SEVERE, null, ex);
//        }
        
        Duration runDuration = new Duration(startTime, new Instant());
        double durInSeconds = runDuration.getMillis() * 0.001;
        
        System.out.println(">> Program complete.  Total running time was " + durInSeconds + " seconds.");
        System.out.println("TOTAL FILES PROCESSED: " + fileSentry.getFileDelta("totalFiles"));
        System.out.println("NEW FILES PROCESSED: " + fileSentry.getFileDelta("newFiles"));
        System.out.println("CHANGED FILES PROCESSED: " + fileSentry.getFileDelta("changedFiles"));
        System.out.println("REMOVED FILES PROCESSED: " + fileSentry.getFileDelta("removedFiles"));
        
        
    }
    
}
