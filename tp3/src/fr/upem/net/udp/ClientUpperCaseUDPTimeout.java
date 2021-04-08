package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ClientUpperCaseUDPTimeout {

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
               } catch (IOException e) {
                  logger.warning("Error during receiving");
                  throw new AssertionError(e);
               } catch (InterruptedException ie) {
                  logger.info("Listener thread is interrupted");
                  Thread.currentThread().interrupt();
               }
            }
         }).start();

         while (scan.hasNextLine()) {
            String line = scan.nextLine();
            bbSender.put(cs.encode(line));
            bbSender.flip();
            dc.send(bbSender, server);
            try {
               var msg = queue.poll(1, TimeUnit.SECONDS);
               if (msg == null) {
                  System.out.println("Server did not respond in time");
               } else {
                  System.out.println("String: " + cs.decode(msg).toString());
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