package fr.upem.net.tcp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerSumOneShot {

   static private int BUFFER_SIZE = 2 * Integer.BYTES;
   static private Logger logger = Logger.getLogger(ServerSumOneShot.class.getName());

   private final ServerSocketChannel serverSocketChannel;
   private final Selector selector;

   public ServerSumOneShot(int port) throws IOException {
      serverSocketChannel = ServerSocketChannel.open();
      serverSocketChannel.bind(new InetSocketAddress(port));
      selector = Selector.open();
   }

   public void launch() throws IOException {
      serverSocketChannel.configureBlocking(false);
      serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
      while (!Thread.interrupted()) {
         printKeys(); // for debug
         System.out.println("Starting select");
         selector.select(this::treatKey);
         System.out.println("Select finished");
      }
   }

   private void treatKey(SelectionKey key) {
      printSelectedKey(key); // for debug
      try {
         if (key.isValid() && key.isAcceptable()) {
            doAccept(key);
         }
         if (key.isValid() && key.isWritable()) {
            doWrite(key);
         }
         if (key.isValid() && key.isReadable()) {
            doRead(key);
         }
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }
   }

   private void doAccept(SelectionKey key) throws IOException {
      var ssc = (ServerSocketChannel) key.channel();
      var sc = ssc.accept();
      if (sc == null) {
         return;
      }
      sc.configureBlocking(false);
      sc.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(BUFFER_SIZE));
   }

   private void doRead(SelectionKey key) throws IOException {
      var sc = (SocketChannel) key.channel();
      var buff = (ByteBuffer) key.attachment();

      if (sc.read(buff) == -1) {
         logger.info("Input stream closed");
         silentlyClose(key);
         return;
      }

      if (buff.hasRemaining()) {
         return;
      }

      buff.flip();
      var ope1 = buff.getInt();
      var ope2 = buff.getInt();
      buff.clear();
      buff.putInt(ope1 + ope2);
      buff.flip();
      key.interestOps(SelectionKey.OP_WRITE);
   }

   private void doWrite(SelectionKey key) throws IOException {
      var sc = (SocketChannel) key.channel();
      var buff = (ByteBuffer) key.attachment();

      sc.write(buff);

      if (buff.hasRemaining()) {
         return;
      }

      silentlyClose(key);
   }

   private void silentlyClose(SelectionKey key) {
      Channel sc = (Channel) key.channel();
      try {
         sc.close();
      } catch (IOException e) {
         // ignore exception
      }
   }

   public static void main(String[] args) throws NumberFormatException, IOException {
      if (args.length != 1) {
         usage();
         return;
      }
      new ServerSumOneShot(Integer.parseInt(args[0])).launch();
   }

   private static void usage() {
      System.out.println("Usage : ServerSumOneShot port");
   }

   /***
    *  Theses methods are here to help understanding the behavior of the selector
    ***/

   private String interestOpsToString(SelectionKey key) {
      if (!key.isValid()) {
         return "CANCELLED";
      }
      int interestOps = key.interestOps();
      ArrayList<String> list = new ArrayList<>();
      if ((interestOps & SelectionKey.OP_ACCEPT) != 0) list.add("OP_ACCEPT");
      if ((interestOps & SelectionKey.OP_READ) != 0) list.add("OP_READ");
      if ((interestOps & SelectionKey.OP_WRITE) != 0) list.add("OP_WRITE");
      return String.join("|", list);
   }

   public void printKeys() {
      Set<SelectionKey> selectionKeySet = selector.keys();
      if (selectionKeySet.isEmpty()) {
         System.out.println("The selector contains no key : this should not happen!");
         return;
      }
      System.out.println("The selector contains:");
      for (SelectionKey key : selectionKeySet) {
         SelectableChannel channel = key.channel();
         if (channel instanceof ServerSocketChannel) {
            System.out.println("\tKey for ServerSocketChannel : " + interestOpsToString(key));
         } else {
            SocketChannel sc = (SocketChannel) channel;
            System.out.println("\tKey for Client " + remoteAddressToString(sc) + " : " + interestOpsToString(key));
         }
      }
   }

   private String remoteAddressToString(SocketChannel sc) {
      try {
         return sc.getRemoteAddress().toString();
      } catch (IOException e) {
         return "???";
      }
   }

   public void printSelectedKey(SelectionKey key) {
      SelectableChannel channel = key.channel();
      if (channel instanceof ServerSocketChannel) {
         System.out.println("\tServerSocketChannel can perform : " + possibleActionsToString(key));
      } else {
         SocketChannel sc = (SocketChannel) channel;
         System.out.println("\tClient " + remoteAddressToString(sc) + " can perform : " + possibleActionsToString(key));
      }
   }

   private String possibleActionsToString(SelectionKey key) {
      if (!key.isValid()) {
         return "CANCELLED";
      }
      ArrayList<String> list = new ArrayList<>();
      if (key.isAcceptable()) list.add("ACCEPT");
      if (key.isReadable()) list.add("READ");
      if (key.isWritable()) list.add("WRITE");
      return String.join(" and ", list);
   }
}
