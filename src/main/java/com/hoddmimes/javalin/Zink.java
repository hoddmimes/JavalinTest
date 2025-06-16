package com.hoddmimes.javalin;

import com.google.gson.*;
import io.javalin.Javalin;
import io.javalin.community.ssl.SslPlugin;
import io.javalin.http.Context;
import io.javalin.json.JavalinJackson;


import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


public class Zink
{
    DBBase db;
    Javalin mApp;
    JsonObject jConfig;
    Logger mLogger;
    Authorize mAuthorize;
    static Zink sZinkServer;
    boolean mVerbose = false;

    private void loadConfig(String[] pArgs) {
        int i = 0;

        String tConfigFilename = "./zink-server-sqlite3.json";

        while (i < pArgs.length) {
            if (pArgs[i].contentEquals("-config")) {
                tConfigFilename = pArgs[++i];
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
            if (jConfig.has("verbose")) {
                mVerbose  = Boolean.parseBoolean(jConfig.get("verbose").getAsString());
            }


            mLogger = new Logger(tLogFilename, mVerbose);
            mLogger.log("loaded configuration (" + tConfigFilename + ")");


            // Load API keys if file exists

            JsonObject jAuthConfig = jConfig.get("authorization").getAsJsonObject();
            String tApiKeysFilename = jAuthConfig.get("file").getAsString();
            // load Api keys
            File tApiKeyFile = new File(tApiKeysFilename);
                if (tApiKeyFile.exists() && tApiKeyFile.canRead()) {
                    JsonObject jAuthApiKeys = JsonParser.parseReader(new FileReader(tApiKeysFilename)).getAsJsonObject();
                    mAuthorize = new Authorize(jAuthApiKeys.get("api-keys").getAsJsonArray(), jAuthConfig.get("save_restricted").getAsBoolean(), jAuthConfig.get("find_restricted").getAsBoolean());
                    mLogger.log("loaded API keys (" + mAuthorize.size() + ") from file: " + tApiKeysFilename );
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
                    config.jsonMapper(new JavalinJackson());
                });

    }


    public static void getHelloS(Context ctx) {
        ctx.result("Hello world");
    }
    public static void postFindS( Context ctx ) {
        sZinkServer.postFind(ctx);
    }

    public void postFind( Context ctx ) {
        mLogger.log("[REQUEST [" + ctx.method() + "] rmthst: " + ctx.req().getRemoteHost() + " url: " + ctx.req().getRequestURL().toString());

        String contentType = ctx.contentType();
        if (!"application/json".equalsIgnoreCase(contentType)) {
            ctx.status(400).result("Invalid json request \n" + ctx.body());
        }

        Map<String, String> tParams = paramsToMap(ctx);

        String tApiKey = getApiKey(ctx);
        if ((mAuthorize != null) && mAuthorize.isFindRestricted()) {
            if (!mAuthorize.validate(tApiKey, Authorize.Action.FIND)) {
                ctx.status(401).result("unauthorized apikey");
            }
        }

        if (tParams.size() == 0) {
            ctx.status(400).result("No query parameters present in request");
            return;
        }

        if (!tParams.containsKey("application")) {
            ctx.status(400).result("invalid query parameter \"application\" is missing");
            return;
        }

        // Get data from the DB

        try {
            JsonArray jResult = db.find(tParams.get("application"),
                                        tParams.get("tag"),
                                        tParams.get("before"),
                                        tParams.get("after"),
                                        Integer.parseInt(tParams.get("limit")));

            ctx.result(jResult.toString());

        } catch (Exception e) {
            ctx.status(500).result(e.getMessage());
        }
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
            JsonArray jResult = db.find(tParams.get(tParams.get("application")),
                    tParams.get("tag"),
                    tParams.get("before"),
                    tParams.get("after"),
                    Integer.parseInt(tParams.get("limit")));

            ctx.result( HtmlBuilder.buildTable(getQryHdr( tParams ), jResult));

        } catch (Exception e) {
            ctx.status(500).result(e.getMessage());
        }
    }

    public static void postSaveS( Context ctx ) {
        sZinkServer.postSave(ctx);
    }

    public void postSave( Context ctx ) {
        mLogger.log("[REQUEST [" + ctx.method() + "] rmthst: " + ctx.req().getRemoteHost() + " url: " + ctx.req().getRequestURL().toString());

        String contentType = ctx.contentType();
        if (!"application/json".equalsIgnoreCase(contentType)) {
            ctx.status(400).result("Invalid json request \n" + ctx.body());
        }

        Map<String, String> tParams = paramsToMap(ctx);

        String tApiKey = getApiKey(ctx);
        if ((mAuthorize != null) && mAuthorize.isSaveRestricted()) {
            if (!mAuthorize.validate(tApiKey, Authorize.Action.SAVE)) {
                ctx.status(401).result("unauthorized apikey");
            }
        }

        if (!tParams.containsKey("application")) {
            ctx.status(400).result("invalid query parameter \"application\" is missing");
            return;
        }
        if (!tParams.containsKey("data")) {
            ctx.status(400).result("invalid query parameter \"data\" is missing");
            return;
        }

        if ((mAuthorize != null) && (mAuthorize.isSaveRestricted()) && (!mAuthorize.validate( tApiKey, Authorize.Action.SAVE))) {
            ctx.status(400).result("unauthorized apikey");
        }

        try {
            db.save(tParams.get("application"), tParams.get("tag"), tParams.get("data"));
        }
        catch( Exception e) {
            mLogger.error("failed to save", e);
        }
        mLogger.log("[RESPONSE] status: 200");
        ctx.status(200);
    }

    private String getApiKey( Context ctx ) {
        String authHeader = ctx.header("Authorization");
        if (authHeader != null && authHeader.startsWith("apikey ")) {
            return authHeader.substring(7);
        } else {
            return null;
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
        mApp.post("/find", Zink::postFindS);
        mApp.post("/save", Zink::postSaveS);
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

        if (ctx.method().toString().equals("GET")) {
            ctx.pathParamMap();
        } else  if (ctx.method().toString().equals("POST")) {
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
