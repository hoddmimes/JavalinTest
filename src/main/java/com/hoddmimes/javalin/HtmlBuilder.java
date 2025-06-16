package com.hoddmimes.javalin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import j2html.tags.ContainerTag;

import java.util.ArrayList;
import java.util.List;

import static j2html.TagCreator.*;


public class HtmlBuilder {

    public static String buildTable(String pHeader, JsonArray jRows) {

        List<JsonObject> tRows = new ArrayList<>(jRows.size());
        jRows.forEach( jRow -> { tRows.add(jRow.getAsJsonObject()); });

        ContainerTag tHtml = html(
                head(
                        title("Zink")
                ),
                body(
                        div(
                                h3("Application: test").withStyle("font-family:arial; margin-top: 10px;"),
                                table(
                                        tr(
                                                th("name").withStyle("text-align: center;border-collapse: collapse;border: 1px solid darkgray"),
                                                th("value").withStyle("text-align: center;border-collapse: collapse;border: 1px solid darkgray")
                                        ),
                                        (each(tRows, r -> tr(
                                                td(getAttribute("time", r)).withStyle("text-align: center;border-collapse: collapse;border: 1px solid darkgray"),
                                                td(getAttribute("application", r)).withStyle("text-align: center;border-collapse: collapse;border: 1px solid darkgray"),
                                                td(getAttribute("tag", r)).withStyle("text-align: center;border-collapse: collapse;border: 1px solid darkgray"),
                                                td(getAttribute("data", r)).withStyle("text-align: center;border-collapse: collapse;border: 1px solid darkgray"))))
                                ).withStyle("margin: 0 auto; font-family:arial; margin-top: 30px;  margin-bottom: 20px;")
                        ).withStyle("border:2px solid black; width:80%; margin-top: 50px;background-color:#f2f2f2")
                )
        );
        return tHtml.renderFormatted();
    }

    private static String getAttribute( String pTag, JsonObject jRow ) {
        if (jRow.has(pTag)) {
            return jRow.get(pTag).getAsString();
        }
        return "";
    }

}
