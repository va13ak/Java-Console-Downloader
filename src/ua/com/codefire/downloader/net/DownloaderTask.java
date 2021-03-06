/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ua.com.codefire.downloader.net;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;
import ua.com.codefire.downloader.Main;

/**
 *
 * @author Valery
 */
public class DownloaderTask implements Runnable {

    private final Downloader downloader;
    private final File store;
    private final URL source;
    private File target;
    private int bufferSize = 524288;
    private long downloaded;
    private long total;
    private long streamReadingTime = 0;
    private long currentDataChunkReadingTime = 0;
    private long totalDownloadingTime = 0;
    private int bytesRead;
    private int totalBytesRead = 0;
    private MessageDigest messageDigest5;
    

    public DownloaderTask(Downloader downloader, File store, URL source) {
        this.downloader = downloader;
        this.store = store;
        this.source = source;
    }

    public long getTotal() {
        return total;
    }

    public File getTarget() {
        return target;
    }

    public URL getSource() {
        return source;
    }

    public long getDownloaded() {
        return downloaded;
    }
    
    public long getSpeed() {
        return totalBytesRead / streamReadingTime;
    }
    
    public long getVelocity() {
        return bytesRead / currentDataChunkReadingTime;
    }
    
    public String getMD5() {
        StringBuilder sb = new StringBuilder();
        
        if (messageDigest5 != null) {
            byte[] mdbytes = messageDigest5.digest();

            //convert the byte to hex format
            for (int i = 0; i < mdbytes.length; i++) {
                sb.append(Integer.toString((mdbytes[i] & 0xff) + 0x100, 16).substring(1));
            }
        } else {
            sb.append("UNKNOWN");
        }
        
        return sb.toString();
    }

    @Override
    public void run() {
        try {
            URLConnection conn = source.openConnection();
            conn.getContentType();

            URL targetUrl = conn.getURL();

            total = conn.getContentLengthLong();

            String sourcefile = new File(targetUrl.getFile()).getName();
            String filename = new String(sourcefile.getBytes("ISO-8859-1"), "UTF-8");
            target = new File(store, URLDecoder.decode(filename, "UTF-8"));

            downloader.downloadBegin(this);

            try {
                messageDigest5 = MessageDigest.getInstance("md5");
            } catch (NoSuchAlgorithmException ex) {
                Logger.getLogger(Downloader.class.getName()).log(Level.SEVERE, null, ex);
                messageDigest5 = null;
            }

            try (BufferedInputStream bis = new BufferedInputStream(targetUrl.openStream());
                    FileOutputStream fos = new FileOutputStream(target)) {

                byte[] buffer = new byte[bufferSize];

                while (true) {
                    
                    synchronized (this) {
                        long startTimeBlockReading = System.currentTimeMillis();
                        bytesRead = bis.read(buffer);
                        currentDataChunkReadingTime = System.currentTimeMillis() - startTimeBlockReading;
                        streamReadingTime += currentDataChunkReadingTime;
                        totalBytesRead += bytesRead;
                    }
                    
                    if (bytesRead < 0) {
                        break;
                    }

                    fos.write(buffer, 0, bytesRead);
                    fos.flush();
                    
                    if (messageDigest5 != null) {
                        messageDigest5.update(buffer, 0, bytesRead);
                    }

                    downloaded += bytesRead;

                    if (bytesRead > 0) {
                        downloader.downloadProgress(this);
                    }
                }
            }

            downloader.downloadComplete(this);

        } catch (MalformedURLException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

}
