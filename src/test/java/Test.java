import j2html.tags.ContainerTag;
import org.conscrypt.ApplicationProtocolSelector;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import static j2html.TagCreator.*;

public class Test
{
    public static void main(String[] args) {
        Test test = new Test();
        test.test();
    }

    List<Foo> generateFoo( int pCount) {
        ArrayList arrayList = new ArrayList();
        for (int i = 0; i < pCount; i++) {
            arrayList.add( new Foo("Name" + String.valueOf(i), "Value" + String.valueOf(i)));
        }
        return arrayList;
    }

    private void test() {
        List<Foo> fooList = generateFoo(10);


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
                                        (each(fooList, fe -> tr(
                                                td(fe.name).withStyle("text-align: center;border-collapse: collapse;border: 1px solid darkgray"),
                                                td(fe.value).withStyle("text-align: center;border-collapse: collapse;border: 1px solid darkgray"))))
                                ).withStyle("margin: 0 auto; font-family:arial; margin-top: 30px;  margin-bottom: 20px;")
                        ).withStyle("border:2px solid black; width:80%; margin-top: 50px;background-color:#f2f2f2")
                )
        );
        try {
            PrintWriter tOut =  new PrintWriter("./test.html");
            String t = tHtml.renderFormatted();
            tOut.write(t);
            System.out.println(t);
            tOut.flush();
            tOut.close();
        }
        catch( IOException e ) {

        }
    }

    class Foo {
        String name;
        String value;

        Foo( String name, String value) {
            this.name = name;
            this.value = value;
        }
    }
}
