package com.example.demo;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class SimpleHttpServer {
    private final Selector selector;
    int port;
    private Set<SocketChannel> allConnections = new HashSet<>();
    volatile boolean run = false;
    HttpServlet servlet;
    ExecutorService executor = Executors.newFixedThreadPool(5);
    public SimpleHttpServer(int port, HttpServlet servlet) throws IOException {
        this.port = port;
        this.servlet = servlet;
        ServerSocketChannel listenerChannel = ServerSocketChannel.open();
        selector = Selector.open();//打开一个选择器供channel注册
        listenerChannel.bind(new InetSocketAddress(port));
        listenerChannel.configureBlocking(false);
        listenerChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    public Thread start() {
        run = true;
        Thread thread = new Thread(() -> {
            try {
                while (run) {
                    dispatch();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, "selector-io");
        thread.start();
        return thread;
    }

    public void stop(int delay) {
        run = false;
    }

    private void dispatch() throws IOException {
        int select = selector.select(2000);
        Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
        while (iterator.hasNext()) {
            SelectionKey  key = iterator.next();
            iterator.remove();
            if (key.isAcceptable()) {//此键的通道是否已准备好接受新的套接字连接
                ServerSocketChannel channel = (ServerSocketChannel) key.channel();
                SocketChannel socketChannel = channel.accept();
                socketChannel.configureBlocking(false);
                socketChannel.register(selector, SelectionKey.OP_READ);
            } else if (key.isReadable()) {//此键的通道是否已准备好进行读取
                final SocketChannel channel = (SocketChannel) key.channel();
                ByteBuffer buffer = ByteBuffer.allocate(1024);
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                while (channel.read(buffer) >0) {
                    buffer.flip();
                    out.write(buffer.array(), 0, buffer.limit());
                    buffer.clear();
                }
                if (out.size() <= 0) {
                    channel.close();
                    continue;
                }
                System.out.println("当前通道："+channel);
                //解码
                executor.submit(() -> {
                    try {
                        Request request = decode(out.toByteArray());
                        Response response = new Response();
                        if (request.method.equalsIgnoreCase("GET")) {
                            servlet.doGet(request, response);
                        } else {
                            servlet.doPost(request, response);
                        }
                        channel.write(ByteBuffer.wrap(encode(response)));
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                });
            }
        }


    }


    // 解码Http服务
    private Request decode(byte[] bytes) throws IOException {
        Request request = new Request();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes)));
        String firstLine = reader.readLine();
        System.out.println(firstLine);
        String[] split = firstLine.trim().split(" ");
        request.method = split[0];
        request.url = split[1];
        request.version = split[2];

        //读取请求头
        Map<String, String> heads = new HashMap<>();
        while (true) {
            String line = reader.readLine();
            if (line.trim().equals("")) {
                break;
            }
            String[] split1 = line.split(":");
            heads.put(split1[0], split1[1]);
        }
        request.heads = heads;
        request.params = getUrlParams(request.url);
        //读取请求体
        request.body = reader.readLine();
        return request;

    }

    //编码Http 服务
    private byte[] encode(Response response) {
        StringBuilder builder = new StringBuilder(512);
        builder.append("HTTP/1.1 ")
                .append(response.code).append(Code.msg(response.code)).append("\r\n");

        if (response.body != null && response.body.length() != 0) {
            builder.append("Content-Length: ")
                    .append(response.body.length()).append("\r\n")
                    .append("Content-Type: text/html\r\n");
        }
        if (response.headers!=null) {
            String headStr = response.headers.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue())
                    .collect(Collectors.joining("\r\n"));
            builder.append(headStr+"\r\n");
        }


//      builder.append ("Connection: close\r\n");// 执行完后关闭链接
        builder.append("\r\n").append(response.body).append("\r\n").append("");
        System.out.println(builder.toString());
        return builder.toString().getBytes();
    }


    public abstract static class HttpServlet {

        abstract void doGet(Request request, Response response);

        abstract void doPost(Request request, Response response);
    }

    public static class Request {
        Map<String, String> heads;
        String url;
        String method;
        String version;
        String body;    //请求内容
        Map<String, String> params;
    }

    public static class Response {
        Map<String, String> headers;
        int code;
        String body; //返回结果
    }


    private static Map getUrlParams(String url) {
        Map<String, String> map = new HashMap();
        url = url.replace("?", ";");
        if (!url.contains(";")) {
            return map;
        }
        if (url.split(";").length > 0) {
            String[] arr = url.split(";")[1].split("&");
            for (String s : arr) {
                if (s.contains("=")) {
                    String key = s.split("=")[0];
                    String value = s.split("=")[1];
                    map.put(key, value);
                }else {
                    map.put(s,null);
                }
            }
            return map;

        } else {
            return map;
        }
    }
}