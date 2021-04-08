package fr.upem.net.udp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.logging.Logger;

public class ServerEchoMultiPort {

   private static final Logger logger = Logger.getLogger(ServerEchoMultiPort.class.getName());
   private final Selector selector;

   public ServerEchoMultiPort(String arg1, String arg2) throws IOException {
      var port1 = Integer.valueOf(arg1);
      var port2 = Integer.valueOf(arg2);
      selector = Selector.open();
      for (var i = port1; i < port2; i++) {
         var dc = DatagramChannel.open();
         dc.configureBlocking(false);
         var client = new InetSocketAddress(i);
         dc.bind(client);
         dc.register(selector, SelectionKey.OP_READ, new Context(client));
      }
   }

   public void serve() throws IOException {
      logger.info("ServerEchoMultiPort started");
      while (!Thread.interrupted()) {
         try {
            selector.select(this::treatKey);
         } catch (UncheckedIOException tunneled) {
            throw tunneled.getCause();
         }
      }
   }

   private void treatKey(SelectionKey key) {
      try {
         if (key.isValid() && key.isWritable()) {
            var context = (Context) key.attachment();
            context.doWrite(key);
         }
         if (key.isValid() && key.isReadable()) {
            var context = (Context) key.attachment();
            context.doRead(key);
         }
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }
   }

   public static void usage() {
      System.out.println("Usage : ServerEchoNonBlocking <port> <port>");
   }

   public static void main(String[] args) throws IOException {
      if (args.length != 2) {
         usage();
         return;
      }
      ServerEchoMultiPort server = new ServerEchoMultiPort(args[0], args[1]);
      server.serve();
   }

   private static class Context {
      private final int BUFFER_SIZE = 1024;
      private final ByteBuffer buff;
      private final InetSocketAddress client;

      Context(InetSocketAddress client) {
         buff = ByteBuffer.allocateDirect(BUFFER_SIZE);
         this.client = client;
      }

      private void doWrite(SelectionKey key) throws IOException {
         var channel = (DatagramChannel) key.channel();
         logger.info("Sending " + buff.remaining() + " bytes to " + client.toString());
         channel.send(buff, client);
         if (buff.hasRemaining()) {
            logger.warning("No packet sent");
            return;
         }
         key.interestOps(SelectionKey.OP_READ);
      }

      private void doRead(SelectionKey key) throws IOException {
         buff.clear();
         var channel = (DatagramChannel) key.channel();
         if (channel.receive(buff) == null) {
            logger.warning("No packet received");
            return;
         }
         buff.flip();
         logger.info("Received " + buff.remaining() + " bytes from " + client.toString());
         key.interestOps(SelectionKey.OP_WRITE);
      }
   }
}