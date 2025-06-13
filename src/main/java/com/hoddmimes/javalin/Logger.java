package com.hoddmimes.javalin;

import org.eclipse.jetty.util.IO;

import java.io.*;
import java.text.SimpleDateFormat;

public class Logger
{
    final FileWriter fp;
    final boolean toTTY;


    public Logger( String pFilename, boolean pToTTY ) {
      try {
          fp = new FileWriter(pFilename, true);
          toTTY = pToTTY;
      }
      catch( IOException e) {
          throw new RuntimeException(e);
      }
    }

    public void log( String pMsg ) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        try {
            fp.write( sdf.format( System.currentTimeMillis()) + " " + pMsg);
            fp.flush();
            if (toTTY) {
                System.out.println( sdf.format( System.currentTimeMillis()) + " " + pMsg);
                System.out.flush();
            }
        }
        catch( IOException e) {
            e.printStackTrace();
        }
    }

    public void error( String pMsg, Exception pException) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        try {
            fp.write( sdf.format( System.currentTimeMillis()) + " Error: " + pMsg + " Reason: " + pException.getMessage());
            fp.flush();
        }
        catch( IOException e) {
            e.printStackTrace();
        }
    }
}
