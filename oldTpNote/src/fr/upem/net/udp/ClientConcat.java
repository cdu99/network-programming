package fr.upem.net.udp;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ClientConcat {

   private static Logger logger = Logger.getLogger(ClientConcat.class.getName());
   private static final Charset ISO = Charset.forName("ISO-8859-15");
   private static final Charset UTF = Charset.forName("UTF-8");
   private static final int BUFFER_SIZE = 4096;
   private final InetSocketAddress serverAddress;
   private final DatagramChannel dc;
   private final BlockingQueue<String> queue = new SynchronousQueue<>();

   public ClientConcat(InetSocketAddress serverAddress) throws IOException {
      this.serverAddress = serverAddress;
      this.dc = DatagramChannel.open();
      dc.bind(null);
   }

   private void listenerThreadRun() {
      var recBuff = ByteBuffer.allocate(BUFFER_SIZE);
      while (!Thread.interrupted()) {
         try {
            var exp = (InetSocketAddress) dc.receive(recBuff);
            recBuff.flip();
            logger.info("Received " + recBuff.remaining());
            queue.put(UTF.decode(recBuff).toString());
            recBuff.clear();
         } catch (AsynchronousCloseException | InterruptedException e) {
            Thread.currentThread().interrupt();
         } catch (IOException e) {
            logger.warning("caca prout");
         }
      }
   }


   public static void usage() {
      System.out.println("Usage : ClientIdUpperCaseUDPOneByOne host port ");
   }

   public static void main(String[] args) throws IOException, InterruptedException {
      if (args.length != 2) {
         usage();
         return;
      }

      String host = args[0];
      int port = Integer.parseInt(args[1]);
      InetSocketAddress serverAddress = new InetSocketAddress(host, port);
      ClientConcat client = new ClientConcat(serverAddress);
      client.launch();
   }

   public void launch() throws IOException, InterruptedException {
      Thread listenerThread = new Thread(this::listenerThreadRun);
      listenerThread.start();

      Scanner scan = new Scanner(System.in);
      var buff = ByteBuffer.allocate(BUFFER_SIZE);
      var currentTime = System.currentTimeMillis();
      long lastSent = 0;
      while (scan.hasNextLine()) {
         String currentString = scan.nextLine();
         if (currentString.equals("")) {
            String msg = null;
            while (msg == null) {
               buff.flip();
               logger.info("Sending " + buff.remaining());
               dc.send(buff, serverAddress);
               msg = queue.poll(300, TimeUnit.MILLISECONDS);
            }
            System.out.println(msg);
            buff.clear();
         } else {
            var encoded = ISO.encode(currentString);
            buff.putInt(encoded.remaining());
            buff.put(encoded);
         }
      }
      scan.close();
   }
}


