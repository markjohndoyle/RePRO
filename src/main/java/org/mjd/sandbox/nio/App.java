//package org.mjd.sandbox.nio;
//
//import java.io.IOException;
//import java.util.concurrent.Executors;
//
//import com.google.common.util.concurrent.ThreadFactoryBuilder;
//
//public class App
//{
//    public static void main(String[] args)
//    {
//        startServer();
//    }
//
//    private static void startServer()
//    {
//        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("Server").build()).submit(() -> {
//            try
//            {
//                Server server = new Server();
//                server.start();
//            }
//            catch (IOException e)
//            {
//                e.printStackTrace();
//                System.out.println("SERVER SHUTDOWN");
//            }
//        });
//    }
//}
