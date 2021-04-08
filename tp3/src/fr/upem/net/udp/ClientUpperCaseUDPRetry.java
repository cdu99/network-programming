package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ClientUpperCaseUDPRetry {

   public static final int BUFFER_SIZE = 1024;
   private static final Logger logger = Logger.getLogger(ClientUpperCaseUDPTimeout.class.getName());

   private static void usage() {
      System.out.println("Usage : NetcatUDP host port charset");
   }

   public static void main(String[] args) throws IOException {
      if (args.length != 3) {
         usage();
         return;
      }

      InetSocketAddress server = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
      Charset cs = Charset.forName(args[2]);
      ByteBuffer bbSender = ByteBuffer.allocate(BUFFER_SIZE);
      ByteBuffer bbReceiver = ByteBuffer.allocate(BUFFER_SIZE);

      ArrayBlockingQueue<ByteBuffer> queue = new ArrayBlockingQueue<>(10);

      try (Scanner scan = new Scanner(System.in); DatagramChannel dc = DatagramChannel.open()) {
         // listener
         new Thread(() -> {
            while (!Thread.interrupted()) {
               try {
                  InetSocketAddress exp = (InetSocketAddress) dc.receive(bbReceiver);
                  bbReceiver.flip();
                  queue.put(bbReceiver);
                  System.out.println("Received " + bbReceiver.remaining() + " bytes from " + exp);
                  bbReceiver.clear();
               } catch (AsynchronousCloseException | InterruptedException ie) {
                  logger.info("Listener thread is interrupted");
               } catch (IOException e) {
                  logger.severe("Error during receiving");
               }
            }
         }).start();

         while (scan.hasNextLine()) {
            String line = scan.nextLine();
            try {
               ByteBuffer msg = null;
               bbSender.put(cs.encode(line));
               while(msg == null) {
                  bbSender.flip();
                  dc.send(bbSender, server);
                  msg = queue.poll(1, TimeUnit.SECONDS);
                  if (msg == null) {
                     logger.info("Server did not respond on time, resending packet");
                  } else {
                     System.out.println("String: " + cs.decode(msg).toString());
                  }
               }
               bbSender.clear();
            } catch (InterruptedException ie) {
               logger.info("Sender thread is interrupted");
               return;
            }
         }
      }
   }
}