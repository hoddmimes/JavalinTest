package com.hoddmimes.javalin;

import com.google.gson.*;
import io.javalin.Javalin;
import io.javalin.community.ssl.SslPlugin;
import io.javalin.http.Context;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


public class Zink
{

    Javalin mApp;
    JsonArray jApiKeys;
    JsonObject jConfig;
    static Logger mLogger;
    static Authorize mAuthorize;


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

        // load server configuration keys
        try {
            jConfig = JsonParser.parseReader(new FileReader(tConfigFilename)).getAsJsonObject();
            JsonObject jAuthConfig = jConfig.get("authorization").getAsJsonObject();
            String tApiKeysFilename = jAuthConfig.get("file").getAsString();
            // load Api keys
            File tApiKeyFile = new File(tApiKeysFilename);
                if (tApiKeyFile.exists() && tApiKeyFile.canRead()) {
                    JsonObject jAuthApiKeys = JsonParser.parseReader(new FileReader(tApiKeysFilename)).getAsJsonObject();
                    mAuthorize = new Authorize(jAuthApiKeys.get("api-keys").getAsJsonArray(), jAuthConfig.get("save_restricted").getAsBoolean(), jAuthConfig.get("find_restricted").getAsBoolean());
                }
        } catch (IOException e) {
            new RuntimeException(e);
        }



        String tLogFilename = "./zink-server.log";
        if (jConfig.has("logfile")) {
            JsonElement j = jConfig.get("logfile");
            if (!j.isJsonNull()) {
                tLogFilename = j.getAsString();
            }
        }
        mLogger = new Logger(tLogFilename, tLogToTTY);
        mLogger.log("loaded API keys (" + jApiKeys.size() + ")");
        mLogger.log("loadedd configuration (" + tConfigFilename + ")");
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


    public static void getHello(Context ctx) {
        ctx.result("Hello world");
    }

    public static void getFind( Context ctx ) {
        mLogger.log("[REQUEST [" + ctx.method() + "] rmthst: " + ctx.req().getRemoteHost() + " url: " + ctx.req().getRequestURL().toString());
        Map<String,String> tParams = paramsToMap( ctx );

        if (tParams.size() == 0) {
            ctx.status(400).result("No query parameters present in request");
            return;
        }

        if (!tParams.containsKey("application")){
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



    }
    private void declare() {
        mApp.get("/hello", Zink::getHello);
        mApp.get("/find", Zink::getFind);
    }
    private void run() {
      mApp.start();
    }

    static private void sendError( String mMsg, int pCode, Context ctx) {

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
        return tMap;

    }
    public static void main(String[] args) {
        Zink server = new Zink();
        server.loadConfig( args );
        server.loadApp();
        server.declare();
        server.run();
    }
}
