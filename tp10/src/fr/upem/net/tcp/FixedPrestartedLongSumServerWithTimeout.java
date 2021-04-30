package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FixedPrestartedLongSumServerWithTimeout {

   private static final Logger logger = Logger.getLogger(FixedPrestartedLongSumServerWithTimeout.class.getName());
   private final ServerSocketChannel serverSocketChannel;
   private final int maxClients;
   private final List<ThreadData> inactivityHandler;
   private static final int TIMEOUT = 2000;

   public FixedPrestartedLongSumServerWithTimeout(int port, int maxClients) throws IOException {
      serverSocketChannel = ServerSocketChannel.open();
      serverSocketChannel.bind(new InetSocketAddress(port));
      logger.info(this.getClass().getName()
            + " starts on port " + port);
      this.maxClients = maxClients;
      inactivityHandler = new ArrayList<>();
   }

   public class ThreadData {
      private long lastActivity;
      private SocketChannel client;
      private final Object lock = new Object();

      void setSocketChannel(SocketChannel client) {
         this.client = client;
      }

      void tick() {
         synchronized (lock) {
            lastActivity = System.currentTimeMillis();
         }
      }

      void closeIfInactive(int timeout) {
         synchronized (lock) {
            if (client != null) {
               if (System.currentTimeMillis() - lastActivity > timeout) {
                  close();
                  client = null;
               }
            }
         }
      }

      void close() {
         silentlyClose(client);
      }

      boolean isConncted() {
         return client != null;
      }
   }


   /**
    * Iterative server main loop
    *
    * @throws IOException
    */

   public void launch() throws IOException {
      logger.info("Server started");
      var workerThreads = new ArrayList<Thread>();
      for (var i = 0; i < maxClients; i++) {
         workerThreads.add(new Thread(() -> {
            var data = new ThreadData();
            inactivityHandler.add(data);
            while (!Thread.interrupted()) {
               SocketChannel client = null;
               try {
                  client = serverSocketChannel.accept();
                  data.setSocketChannel(client);
               } catch (IOException e) {
                  logger.info("Thread interrupted by IOException");
                  Thread.currentThread().interrupt();
               }
               try {
                  logger.info("Connection accepted from " + client.getRemoteAddress());
                  serve(client, data);
               } catch (IOException ioe) {
                  logger.log(Level.INFO, "Connection terminated with client by IOException", ioe.getCause());
               } finally {
                  silentlyClose(client);
               }
            }
         }));
      }
      workerThreads.forEach(Thread::start);

      new Thread(() -> {
         long lastCheck = 0;
         while (!Thread.interrupted()) {
            if (System.currentTimeMillis() - lastCheck > TIMEOUT) {
               for (ThreadData data : inactivityHandler) {
                  data.closeIfInactive(TIMEOUT);
               }
               lastCheck = System.currentTimeMillis();
            }

         }
      }).start();

      new Thread(() -> {
         try (Scanner scan = new Scanner(System.in)) {
            while (scan.hasNextLine()) {
               String currentString = scan.nextLine();
               switch (currentString) {
                  case "INFO":
                     var counter = 0;
                     for (var data : inactivityHandler) {
                        if (data.isConncted()) {
                           counter++;
                        }
                     }
                     logger.info(counter + " clients connected");
                     break;
                  case "SHUTDOWN":
                     serverSocketChannel.close();
                     break;
                  case "SHUTDOWNNOW":
                     workerThreads.forEach(Thread::interrupt);
                     break;
                  default:
                     logger.info("Command unknown");
               }

            }
         } catch (IOException e) {
            logger.info("Thread stopped by IOException");
         }
      }).start();
   }

   /**
    * Treat the connection sc applying the protocole
    * All IOException are thrown
    *
    * @param sc
    * @throws IOException
    * @throws InterruptedException
    */
   private void serve(SocketChannel sc, ThreadData data) throws IOException {
      while (true) {
         var buff = ByteBuffer.allocate(Integer.BYTES);

         if (!readFully(sc, buff)) {
            return;
         }
         data.tick();
         buff.flip();
//         logger.info("Received " + buff.remaining() + " bytes from " + sc.getRemoteAddress());
         var nbOfOperand = buff.getInt();
         buff = ByteBuffer.allocate(nbOfOperand * Long.BYTES);
         if (!readFully(sc, buff)) {
            return;
         }
         data.tick();
         long sum = 0;
         buff.flip();
//         logger.info("Received " + buff.remaining() + " bytes from " + sc.getRemoteAddress());
         for (var i = 0; i < nbOfOperand; i++) {
            sum += buff.getLong();
         }
         var responseBuff = ByteBuffer.allocate(Long.BYTES);
         responseBuff.putLong(sum);
         responseBuff.flip();
//         logger.info("Sending " + responseBuff.remaining() + " bytes to " + sc.getRemoteAddress());
         sc.write(responseBuff);
         data.tick();
      }
   }

   /**
    * Close a SocketChannel while ignoring IOExecption
    *
    * @param sc
    */

   private void silentlyClose(SocketChannel sc) {
      if (sc != null) {
         try {
            sc.close();
         } catch (IOException e) {
            // Do nothing
         }
      }
   }

   static boolean readFully(SocketChannel sc, ByteBuffer bb) throws IOException {
      while (bb.hasRemaining()) {
         if (sc.read(bb) == -1) {
            logger.info("Input stream closed");
            return false;
         }
      }
      return true;
   }

   public static void main(String[] args) throws NumberFormatException, IOException {
      FixedPrestartedLongSumServerWithTimeout server = new FixedPrestartedLongSumServerWithTimeout(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
      server.launch();
   }
}


