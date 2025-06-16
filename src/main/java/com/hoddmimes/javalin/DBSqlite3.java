package com.hoddmimes.javalin;

import java.io.File;
import java.security.PrivateKey;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.sqlite.SQLiteConfig;

import javax.imageio.plugins.tiff.TIFFImageReadParam;

public class DBSqlite3 implements DBBase
{
    private static String DB_TABLE = "entries";
    private static String DB_FLD_ID = "id";
    private static String DB_FLD_APPLICATION = "application";
    private static String DB_FLD_TAG = "tag";
    private static String DB_FLD_DATA = "data";
    private static String DB_FLD_TIME = "time";

    private PreparedStatement mSaveStatement;

    private static String SQl_SAVE_STMT = "INSERT INTO entries (" + DB_FLD_APPLICATION +  "," + DB_FLD_TAG + "," + DB_FLD_DATA + "," + DB_FLD_TIME + ") VALUES (?,?,?,?)";

    final String mSqlFile;
    private Connection mConnection = null;

    public DBSqlite3( String DB_filename ) {
        mSqlFile = DB_filename;
    }

    @Override
    public void connect() {
        try {
            File tFile = new File( mSqlFile );
            if (!tFile.exists() || (!tFile.canRead())) {
                createDatabase();
            }
            openDatabase();
            mSaveStatement = mConnection.prepareStatement( SQl_SAVE_STMT );

        }
        catch( Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void createDatabase() throws DBException {
        String url = "jdbc:sqlite:" + mSqlFile;

        try {
            mConnection = DriverManager.getConnection(url);
            if (mConnection != null) {
                createCollectionTable();
            }

        } catch (SQLException e) {
            throw new DBException(e);
        }
    }

    private void createCollectionTable() throws DBException {

        String sql_stmt = "CREATE TABLE IF NOT EXISTS " + DB_TABLE +
                " (" + DB_FLD_ID + " INTEGER PRIMARY KEY," +
                DB_FLD_APPLICATION + " TEXT NOT NULL," +
                DB_FLD_TAG + " TEXT," +
                DB_FLD_DATA + " TEXT NOT NULL," +
                DB_FLD_TIME + " TEXT NOT NULL)";


        try {
            Statement stmt = mConnection.createStatement();
            // create a new table
            stmt.execute(sql_stmt);

            sql_stmt = "CREATE INDEX index_entries ON entries (" + DB_FLD_APPLICATION + "," + DB_FLD_TAG + "," + DB_FLD_TIME + ")";
            stmt = mConnection.createStatement();
            // create a new index
            stmt.execute(sql_stmt);

            mConnection.close();

        } catch (SQLException e) {
            throw new DBException(e.getMessage(), e);
        }
    }

    private void openDatabase() throws DBException
    {
        String url = "jdbc:sqlite:" + mSqlFile;

        File dbFile = new File( mSqlFile );
        if (!dbFile.exists()) {
            throw new DBException("Database file" + mSqlFile + " is not found");
        }

        try {
            try {Class.forName("org.sqlite.JDBC");}
            catch( Exception e) {
                e.printStackTrace();
            }
            // Set all pragmas
            SQLiteConfig tConfig = new SQLiteConfig();
            tConfig.setJournalMode(SQLiteConfig.JournalMode.WAL);

            tConfig.setTempStore(SQLiteConfig.TempStore.DEFAULT);
            tConfig.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
            tConfig.enforceForeignKeys(false);
            tConfig.enableCountChanges(false);



            mConnection = DriverManager.getConnection(url, tConfig.toProperties());
            if (mConnection != null) {
                DatabaseMetaData meta = mConnection.getMetaData();
                //System.out.println("The driver name is " + meta.getDriverName());
                //System.out.println("Database has been opened.");
            }

        } catch (SQLException e) {
            throw new DBException(e);
        }
    }

    @Override
    public void save(String pApplication, String pTag, String pData) throws DBException
    {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        try {
            mSaveStatement.setString(1, pApplication);
            mSaveStatement.setString(2, pTag);
            mSaveStatement.setString(3, pData);
            mSaveStatement.setString(4, simpleDateFormat.format(System.currentTimeMillis()));
            int i = mSaveStatement.executeUpdate();
            System.out.println("InsertValue: " + i);
        }
        catch(SQLException e) {
            throw new DBException( e);
        }
    }

    @Override
    public JsonArray find(String pApplication, String pTag, String pBefore, String pAfter, int pLimit) throws DBException{
        String sql = "SELECT * FROM " + DB_TABLE + " WHERE " + DB_FLD_APPLICATION + " = \"" + pApplication + "\"";

        if (pTag != null) {
            sql += (" AND tag = \"" + pTag + "\"");
        }

        if (pBefore != null) {
            String _before = pBefore + "0000-00-00 00:00:00.000".substring(pBefore.length());
            sql += " AND time <= \"" + _before + "\"";
        }

        if (pAfter != null) {
            String _after = pAfter + "9999-99-99 23:59:59.999".substring(pAfter.length());
            sql += (" AND time >= \"" + _after + "\"");
        }
        sql += " ORDER BY time DESC";


        if (pLimit > 0) {
            sql += " LIMIT " + String.valueOf(pLimit);
        }
        System.out.println( "SQL FIND STATEMENT: " + sql);

        JsonArray jResult = new JsonArray();
        try {
            Statement sql_stmt = mConnection.createStatement();

            ResultSet tSqlResult = sql_stmt.executeQuery(sql);


            while (tSqlResult.next()) {
                JsonObject jRow = new JsonObject();
                jRow.addProperty("application", tSqlResult.getString(2));
                String tTag = tSqlResult.getString(3);
                if (tTag != null) {
                    jRow.addProperty("tag", tTag);
                }
                jRow.addProperty("data", tSqlResult.getString(4));
                jRow.addProperty("time", tSqlResult.getString(5));
                jResult.add(jRow);
            }
        }
        catch( SQLException e) {
            throw new DBException(e);
        }
        return jResult;

    }

    @Override
    public void close() {
        try {
            mConnection.close();
        } catch (SQLException e) {
            DBException dbException = new DBException(e);
            dbException.printStackTrace();
        }
    }

    private void test() {
        File tFile = new File( this.mSqlFile );
        if (tFile.exists()) {
            tFile.delete();
        }

        this.connect();


        // Save some records
        try {
            for (int i = 0; i < 20; i++) {
                if ((i % 2) == 0) {
                    this.save("test", "tag-" + String.valueOf(i), "Data value for row " + String.valueOf(i));

                }
            }
        }
        catch(DBException e) {
            e.printStackTrace();
        }
        this.close();
        this.connect();

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");



        testFind("test", null,null,null, 0 );
        testFind("test", null,null,null, 3 );
        testFind("test", "tag-12",null,null, 0 );

        String tTime = simpleDateFormat.format(System.currentTimeMillis() - 1000L);
        testFind("test", null,null,tTime, 0 );
        testFind("test", null,tTime,null, 0 );

        tTime = simpleDateFormat.format(System.currentTimeMillis() + 1000L);
        testFind("test", null,null,tTime, 0 );
        testFind("test", null,tTime,null, 0 );


        this.close();

    }

    private void testFind( String pApplication, String pTag, String pBefore, String pAfter, int pLimit) {
        System.out.println("Find Parameters Application: " + pApplication + " Tag: " + pTag + " Before: " + pBefore + " After: " + pAfter + " Limit: " + pLimit);
        try {
            JsonArray jResults = this.find(pApplication, pTag, pBefore, pAfter, pLimit);
            jResults.forEach(jrow -> {System.out.println( jrow.getAsJsonObject().toString() );});
        } catch (DBException e) {
            e.printStackTrace();
        }
        System.out.println("\n\n");
    }




    public static void main(String[] args) {
        DBSqlite3 t = new DBSqlite3("./dbsqlite3test.db");
        t.test();
    }
}
