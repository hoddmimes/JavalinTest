package com.hoddmimes.javalin;

import com.google.gson.*;
import io.javalin.Javalin;
import io.javalin.community.ssl.SslPlugin;
import io.javalin.http.Context;
import org.eclipse.jetty.util.thread.TryExecutor;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Zink
{
    DBBase db;
    Javalin mApp;
    JsonArray jApiKeys;
    JsonObject jConfig;
    Logger mLogger;
    Authorize mAuthorize;
    static Zink sZinkServer;

    private void loadConfig(String[] pArgs) {
        int i = 0;
        boolean tLogToTTY = false;

        String tConfigFilename = "./zink-server-sqlite3.json";

        while (i < pArgs.length) {
            if (pArgs[i].contentEquals("-config")) {
                tConfigFilename = pArgs[i++];
            }
            if (pArgs[i].contentEquals("-verbose")) {
                tLogToTTY = Boolean.parseBoolean(pArgs[i++]);
            }
            i++;
        }

        // load server API keys
        try {
            jConfig = JsonParser.parseReader(new FileReader(tConfigFilename)).getAsJsonObject();

            // Enable logfile
            String tLogFilename = "./zink-server.log";
            if (jConfig.has("logfile")) {
                JsonElement j = jConfig.get("logfile");
                if (!j.isJsonNull()) {
                    tLogFilename = j.getAsString();
                }
            }
            mLogger = new Logger(tLogFilename, tLogToTTY);
            mLogger.log("loaded configuration (" + tConfigFilename + ")");


            // Load API keys if file exists

            JsonObject jAuthConfig = jConfig.get("authorization").getAsJsonObject();
            String tApiKeysFilename = jAuthConfig.get("file").getAsString();
            // load Api keys
            File tApiKeyFile = new File(tApiKeysFilename);
                if (tApiKeyFile.exists() && tApiKeyFile.canRead()) {
                    JsonObject jAuthApiKeys = JsonParser.parseReader(new FileReader(tApiKeysFilename)).getAsJsonObject();
                    mAuthorize = new Authorize(jAuthApiKeys.get("api-keys").getAsJsonArray(), jAuthConfig.get("save_restricted").getAsBoolean(), jAuthConfig.get("find_restricted").getAsBoolean());
                    mLogger.log("loaded API keys (" + jApiKeys.size() + ") from file: " + tApiKeysFilename );
                } else {
                    mLogger.log("Warning: API key file not found or could not be read, file : " + tApiKeysFilename );
                }
        } catch (IOException e) {
            new RuntimeException(e);
        }

    }

    private void loadApp() {
        int port = 0;
        boolean tFlag = false;

        if (jConfig.has("http_port")) {
            JsonElement jsonElement = jConfig.get("http_port");
            if (!jsonElement.isJsonNull()) {
                tFlag = true;
                port = jsonElement.getAsInt();
            }
        }

        final boolean tInsecurePort = tFlag;
        final int https_port = jConfig.get("https_port").getAsInt();
        final int http_port = port;

        SslPlugin sslPlugin = new SslPlugin(conf -> {
            conf.pemFromPath("cert.pem", "key.pem");
            conf.insecure=tInsecurePort;
            conf.http2=true;
            conf.sniHostCheck=false;
            conf.securePort=https_port;
            conf.insecurePort=http_port;
        });

        mApp = Javalin.create( config -> {
                    config.showJavalinBanner = false;
                    config.registerPlugin(sslPlugin);
                });

    }


    public static void getHelloS(Context ctx) {
        ctx.result("Hello world");
    }

    public static void getFindS( Context ctx ) {
        sZinkServer.getFind(ctx);
    }

    public void getFind( Context ctx ) {
        mLogger.log("[REQUEST [" + ctx.method() + "] rmthst: " + ctx.req().getRemoteHost() + " url: " + ctx.req().getRequestURL().toString());
        Map<String, String> tParams = paramsToMap(ctx);

        if (tParams.size() == 0) {
            ctx.status(400).result("No query parameters present in request");
            return;
        }

        if (!tParams.containsKey("application")) {
            ctx.status(400).result("invalid query parameter \"application\" is missing");
            return;
        }

        if ((mAuthorize != null) && (mAuthorize.isFindRestricted()) && (!tParams.containsKey("apikey"))) {
            ctx.status(400).result("invalid query parameter \"apikey\" is missing");
            return;
        }
        if ((mAuthorize != null) && (mAuthorize.isFindRestricted()) && (!mAuthorize.validate(tParams.get("apikey"), Authorize.Action.FIND))) {
            ctx.status(400).result("unauthorized apikey");
            return;
        }

        // Get data from the DB

        try {
            List<JsonObject> tResult = db.find(tParams.get(tParams.get("application")),
                    tParams.get("tag"),
                    tParams.get("before"),
                    tParams.get("after"),
                    Integer.parseInt(tParams.get("limit")));

            ctx.result( HtmlBuilder.buildTable(getQryHdr( tParams ), tResult));

        } catch (Exception e) {
            ctx.status(500).result(e.getMessage());
        }
    }

    private String getQryHdr( Map<String,String> pParams ) {
        StringBuilder sb = new StringBuilder();
        sb.append("application: " + pParams.get("application"));
        if (pParams.containsKey("tag")) {
            sb.append(" tag: " + pParams.get("application"));
        }
        if (pParams.containsKey("before")) {
            sb.append(" before: " + pParams.get("application"));
        }
        if (pParams.containsKey("after")) {
            sb.append(" after: " + pParams.get("application"));
        }
        return sb.toString();
    }


    private void declare() {
        mApp.get("/hello", Zink::getHelloS);
        mApp.get("/find", Zink::getFindS);
    }
    private void run() {
      mApp.start();
    }

    private void loadDB() {
        JsonObject jDatabase = jConfig.get("database").getAsJsonObject();
        JsonObject jDbConfig = jDatabase.get("configuration").getAsJsonObject();
        if (jDatabase.get("type").getAsString().contentEquals("sqlite3")) {
            db = new DBSqlite3(jDbConfig.get("db_file").getAsString());
            db.connect();
            mLogger.log("Conneted to sqlite3 database \"" + jDbConfig.get("db_file").getAsString() + "\"");
        } else if (jDatabase.get("type").getAsString().contentEquals("mongo")) {
            mLogger.log("Conneted to Mongo database " + jDbConfig.get("db_file").getAsString() +
                    " host: " + jDbConfig.get("host").getAsString() + " port: " + jDbConfig.get("host").getAsInt());
        }
    }
    static private HashMap<String,String> paramsToMap( Context ctx ) {
        HashMap<String,String> tMap = new HashMap<>();
        if (ctx.method().equals("GET")) {
            ctx.pathParamMap();
        } else  if (ctx.method().equals("POST")) {
            String jString = ctx.body();
            JsonObject jParams = JsonParser.parseString( jString ).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : jParams.entrySet()) {
                tMap.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        if (!tMap.containsKey("limit")) {
            tMap.put("limit", String.valueOf(Integer.MAX_VALUE));
        }
        return tMap;

    }
    public static void main(String[] args) {
        Zink s = new Zink();
        s.sZinkServer = s;
        s.loadConfig( args );
        s.loadDB();
        s.loadApp();
        s.declare();
        s.run();
    }
}
