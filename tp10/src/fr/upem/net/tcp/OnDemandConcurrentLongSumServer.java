package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OnDemandConcurrentLongSumServer {

   private static final Logger logger = Logger.getLogger(OnDemandConcurrentLongSumServer.class.getName());
   private static final int BUFFER_SIZE = 1024;
   private final ServerSocketChannel serverSocketChannel;

   public OnDemandConcurrentLongSumServer(int port) throws IOException {
      serverSocketChannel = ServerSocketChannel.open();
      serverSocketChannel.bind(new InetSocketAddress(port));
      logger.info(this.getClass().getName()
            + " starts on port " + port);
   }

   /**
    * Iterative server main loop
    *
    * @throws IOException
    */

   public void launch() throws IOException {
      logger.info("Server started");

      while (!Thread.interrupted()) {
         SocketChannel client = serverSocketChannel.accept();
         new Thread(() -> {
            try {
               logger.info("Connection accepted from " + client.getRemoteAddress());
               serve(client);
            } catch (IOException ioe) {
               logger.log(Level.INFO, "Connection terminated with client by IOException", ioe.getCause());
            } finally {
               silentlyClose(client);
            }
         }).start();
      }
   }

   /**
    * Treat the connection sc applying the protocole
    * All IOException are thrown
    *
    * @param sc
    * @throws IOException
    * @throws InterruptedException
    */
   private void serve(SocketChannel sc) throws IOException {
      while (true) {
         var buff = ByteBuffer.allocate(Integer.BYTES);

         if (!readFully(sc, buff)) {
            return;
         }
         buff.flip();
         logger.info("Received " + buff.remaining() + " bytes from " + sc.getRemoteAddress());
         var nbOfOperand = buff.getInt();
         buff = ByteBuffer.allocate(nbOfOperand * Long.BYTES);
         if (!readFully(sc, buff)) {
            return;
         }
         long sum = 0;
         buff.flip();
         logger.info("Received " + buff.remaining() + " bytes from " + sc.getRemoteAddress());
         for (var i = 0; i < nbOfOperand; i++) {
            sum += buff.getLong();
         }
         var responseBuff = ByteBuffer.allocate(Long.BYTES);
         responseBuff.putLong(sum);
         responseBuff.flip();
         logger.info("Sending " + responseBuff.remaining() + " bytes to " + sc.getRemoteAddress());
         sc.write(responseBuff);
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
      OnDemandConcurrentLongSumServer server = new OnDemandConcurrentLongSumServer(Integer.parseInt(args[0]));
      server.launch();
   }
}

// L'OS met en attente les clients qui essayent de se connecter
