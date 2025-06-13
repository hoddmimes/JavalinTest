package com.hoddmimes.javalin;

import com.google.gson.JsonObject;

import java.util.List;

public interface DBBase
{
    public void connect();

    public void save(String pApplication, String pTag, String pData) throws DBException ;

    public List<JsonObject> find(String pApplication, String pTag, String pBefore, String pAfter, int pLimit) throws DBException;

    public void close();
}
