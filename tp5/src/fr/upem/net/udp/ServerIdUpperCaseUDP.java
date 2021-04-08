package fr.upem.net.udp;

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;

public class ServerIdUpperCaseUDP {

   private static final Logger logger = Logger.getLogger(ServerIdUpperCaseUDP.class.getName());
   private static final int BUFFER_SIZE = 1024;
   private static final Charset UTF8 = StandardCharsets.UTF_8;
   private final DatagramChannel dc;
   private final ByteBuffer buff = ByteBuffer.allocateDirect(BUFFER_SIZE);
   private final ByteBuffer buffSend = ByteBuffer.allocateDirect(BUFFER_SIZE);

   public ServerIdUpperCaseUDP(int port) throws IOException {
      dc = DatagramChannel.open();
      dc.bind(new InetSocketAddress(port));
      logger.info("ServerIdUpperCaseUDP started on port " + port);
   }

   public void serve() throws IOException {
      while (!Thread.interrupted()) {
         // Receive request from client
         buff.clear();
         InetSocketAddress exp = (InetSocketAddress) dc.receive(buff);
         buff.flip();
         logger.info("Received " + buff.remaining() + " bytes from " + exp.toString());
         if (buff.remaining() > 8) {
            // Read id
            var id = buff.getLong();
            // Decode msg in request
            var msg = UTF8.decode(buff).toString();
            var upperCaseMsg = msg.toUpperCase();
            // Create packet with id, upperCaseMsg in UTF-8
            buffSend.clear();
            buffSend.putLong(id);
            var encodedMsg = UTF8.encode(upperCaseMsg);
            if (encodedMsg.remaining() < BUFFER_SIZE - 8) {
               buffSend.put(encodedMsg).flip();
               // Send the packet to client
               logger.info("Sending " + buffSend.remaining() + " bytes to " + exp.toString());
               dc.send(buffSend, exp);
            } else {
               logger.warning("Received incorrect packet");
            }
         } else {
            logger.warning("Received incorrect packet");
         }
      }
   }

   public static void usage() {
      System.out.println("Usage : ServerIdUpperCaseUDP port");
   }

   public static void main(String[] args) throws IOException {
      if (args.length != 1) {
         usage();
         return;
      }
      ServerIdUpperCaseUDP server;
      int port = Integer.valueOf(args[0]);
      if (!(port >= 1024) & port <= 65535) {
         logger.severe("The port number must be between 1024 and 65535");
         return;
      }
      try {
         server = new ServerIdUpperCaseUDP(port);
      } catch (BindException e) {
         logger.severe("Server could not bind on " + port + "\nAnother server is probably running on this port.");
         return;
      }
      server.serve();
   }
}