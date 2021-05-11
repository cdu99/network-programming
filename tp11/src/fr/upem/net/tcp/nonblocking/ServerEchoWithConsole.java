package fr.upem.net.tcp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerEchoWithConsole {

   static private class Context {
      final private SelectionKey key;
      final private SocketChannel sc;
      final private ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);

      private boolean closed = false;

      private Context(SelectionKey key) {
         this.key = key;
         this.sc = (SocketChannel) key.channel();
      }

      /**
       * Update the interestOps of the key looking
       * only at values of the boolean closed and
       * the ByteBuffer buff.
       * <p>
       * The convention is that buff is in write-mode.
       */
      private void updateInterestOps() {
         int newINterestOPs = 0;
         if (bb.hasRemaining() && !closed) {
            newINterestOPs |= SelectionKey.OP_READ;
         }
         if (bb.position() != 0) {
            newINterestOPs |= SelectionKey.OP_WRITE;
         }
         if (newINterestOPs == 0) {
            silentlyClose();
         } else {
            key.interestOps(newINterestOPs);
         }

//         if (closed && bb.position() == 0) {
//            silentlyClose();
//         }
//         if (closed && bb.position() != 0) {
//            key.interestOps(SelectionKey.OP_WRITE);
//         }
//         if (bb.position() > BUFFER_SIZE / 2) {
//            key.interestOps(SelectionKey.OP_WRITE);
//         }
//         if (bb.position() > 0){
//            key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
//         } else {
//            key.interestOps(SelectionKey.OP_READ);
//         }
      }

      /**
       * Performs the read action on sc
       * <p>
       * The convention is that buff is in write-mode before calling doRead
       * and is in write-mode after calling doRead
       *
       * @throws IOException
       */
      private void doRead() throws IOException {
         if (sc.read(bb) == -1) {
            logger.info("Input stream closed");
            closed = true;
         }

         updateInterestOps();
      }

      /**
       * Performs the write action on sc
       * <p>
       * The convention is that buff is in write-mode before calling doWrite
       * and is in write-mode after calling doWrite
       *
       * @throws IOException
       */
      private void doWrite() throws IOException {
         bb.flip();
         sc.write(bb);
         bb.compact();
         updateInterestOps();
      }

      private void silentlyClose() {
         try {
            sc.close();
         } catch (IOException e) {
            // ignore exception
         }
      }
   }

   static private int BUFFER_SIZE = 1_024;
   static private Logger logger = Logger.getLogger(ServerEchoWithConsole.class.getName());

   private final ServerSocketChannel serverSocketChannel;
   private final Selector selector;
   private final ArrayBlockingQueue<String> commandQueue = new ArrayBlockingQueue<>(10);
   private final Thread console;
   private final Thread server;

   public ServerEchoWithConsole(int port) throws IOException {
      serverSocketChannel = ServerSocketChannel.open();
      serverSocketChannel.bind(new InetSocketAddress(port));
      selector = Selector.open();
      this.console = new Thread(this::consoleRun);
      server = new Thread(this::serverRun);
   }

   private void serverRun() {
      while (!Thread.interrupted()) {
         printKeys(); // for debug
         System.out.println("Starting select");
         try {
            selector.select(this::treatKey);
         } catch (IOException e) {
            throw new UncheckedIOException(e);
         }
         processCommands();

         System.out.println("Select finished");
      }
   }

   private void consoleRun() {
      try {
         var scan = new Scanner(System.in);
         while (scan.hasNextLine()) {
            var msg = scan.nextLine();
            sendCommand(msg);
         }
      } catch (InterruptedException e) {
         logger.info("Console thread has been interrupted");
      } finally {
         logger.info("Console thread stopping");
      }
   }

   private void sendCommand(String msg) throws InterruptedException {
      commandQueue.put(msg);
      selector.wakeup();
   }

   public void launch() throws IOException {
      serverSocketChannel.configureBlocking(false);
      serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
      console.start();

      server.start();
   }

   private void processCommands() {
      while (!commandQueue.isEmpty()) {
         var msg = commandQueue.remove();
         switch (msg) {
            case "INFO":
               var counter = 0;
               for (var key : selector.keys()) {
                  if (key.attachment() != null) {
                     counter++;
                  }
               }
               logger.info("INFO: " + counter + " clients connected");
               break;
            case "SHUTDOWN":
               logger.info("SHUTDOWN: No new clients");
               try {
                  serverSocketChannel.close();
               } catch (IOException e) {
                  throw new UncheckedIOException(e);
               }
               break;
            case "SHUTDOWNOW":
               logger.info("SHUTDOWNNOW: Stop the server");
               for (SelectionKey key : selector.selectedKeys()) {
                  var context = (Context) key.attachment();
                  context.silentlyClose();
               }
               server.interrupt();
               break;
            default:
               logger.info("UNKNOWN COMMAND");
               break;
         }
//            uniqueContext.queueMessage(bb.flip());
      }
   }

   private void treatKey(SelectionKey key) {
      printSelectedKey(key); // for debug
      try {
         if (key.isValid() && key.isAcceptable()) {
            doAccept(key);
         }
      } catch (IOException ioe) {
         // lambda call in select requires to tunnel IOException
         throw new UncheckedIOException(ioe);
      }
      try {
         if (key.isValid() && key.isWritable()) {
            ((Context) key.attachment()).doWrite();
         }
         if (key.isValid() && key.isReadable()) {
            ((Context) key.attachment()).doRead();
         }
      } catch (IOException e) {
         logger.log(Level.INFO, "Connection closed with client due to IOException", e);
         silentlyClose(key);
      }
   }

   private void doAccept(SelectionKey key) throws IOException {
      var ssc = (ServerSocketChannel) key.channel();
      var sc = ssc.accept();
      if (sc == null) {
         return;
      }
      sc.configureBlocking(false);
      var clientKey = sc.register(selector, SelectionKey.OP_READ);
      clientKey.attach(new Context(clientKey));
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
      new ServerEchoWithConsole(Integer.parseInt(args[0])).launch();
   }

   private static void usage() {
      System.out.println("Usage : ServerEcho port");
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
