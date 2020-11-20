package com.example.demo;

import java.io.IOException;
import java.util.HashMap;

public class HttpServerTest {

    public static void simpleHttpTest() throws IOException, InterruptedException {
        SimpleHttpServer simpleHttpServer = new SimpleHttpServer(8080, new SimpleHttpServer.HttpServlet() {
            @Override
            void doGet(SimpleHttpServer.Request request, SimpleHttpServer.Response response) {
                System.out.println(request.url);
                ;

                response.body=request.params.toString();
                response.code=200;
                response.headers=new HashMap<>();
                if (request.params.containsKey("short")) {
                    response.headers.put("Connection", "close");
                }else if(request.params.containsKey("long")){
                    response.headers.put("Connection", "keep-alive");
                    response.headers.put("Keep-Alive", "timeout=30,max=300");
                }
            }

            @Override
            void doPost(SimpleHttpServer.Request request, SimpleHttpServer.Response response) {

            }
        });
        simpleHttpServer.start().join();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        simpleHttpTest();
    }

}
