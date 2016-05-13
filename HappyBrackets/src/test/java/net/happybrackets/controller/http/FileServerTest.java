package net.happybrackets.controller.http;

import net.happybrackets.core.ControllerConfig;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

/**
 * Created by Sam on 26/04/2016.
 */
public class FileServerTest {
    private FileServer server;
    private ControllerConfig config;

    @Before
    public void setUp() throws Exception {
        config = new ControllerConfig();
        server = new FileServer(config);
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
    }

    @Test
    public void readFile() throws Exception {
        //add some diagnostics for current path
        Path currentRelativePath = Paths.get("");
        String s = currentRelativePath.toAbsolutePath().toString();
        System.out.println("Current relative path is: " + s);

        String json = server.readFile("src/test/config/test-device-config.json", "utf8");
        assertFalse( json == null );
        assertFalse( json.isEmpty() );
        assertTrue(  json.contains("wifi") );
    }

    @Test
    public void getPage() throws Exception {
//        CloseableHttpClient httpclient = HttpClients.createDefault();
//        HttpGet httpget = new HttpGet("http://localhost:" + config.getControllerHTTPPort());
//        CloseableHttpResponse response = httpclient.execute(httpget);
//        String string = new String(readContents(response.getEntity()), "UTF-8");
//        Assert.assertEquals("<xml/>", string);
//        response.close();
        OkHttpClient client = new OkHttpClient();
        Request request = new okhttp3.Request.Builder()
                .url("http://localhost:" + config.getControllerHTTPPort() + "/")
                .build();

        Response response = client.newCall(request).execute();
        String json = response.body().string();

        assertFalse( json == null );
        assertFalse( json.isEmpty() );
        assertTrue(  json.contains("wifi") );
    }

}