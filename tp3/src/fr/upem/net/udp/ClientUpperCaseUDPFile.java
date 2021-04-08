package fr.upem.net.udp;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;


// Il peut y avoir des doublons dans le fichier de sortie. Dans le cas où le serveur prend plus de 300ms à répondre, nous
// renvoyons le même paquet or il est possible que le serveur réponde après ces 300ms, nous nous retrouvons avec un doublon.

public class ClientUpperCaseUDPFile {
   private final static Charset UTF8 = StandardCharsets.UTF_8;
   private final static int BUFFER_SIZE = 1024;
   private static final Logger logger = Logger.getLogger(ClientUpperCaseUDPFile.class.getName());


   private static void usage() {
      System.out.println("Usage : ClientUpperCaseUDPFile in-filename out-filename timeout host port ");
   }


   public static void main(String[] args) throws IOException, InterruptedException {
      if (args.length != 5) {
         usage();
         return;
      }

      String inFilename = args[0];
      String outFilename = args[1];
      int timeout = Integer.valueOf(args[2]);
      String host = args[3];
      int port = Integer.valueOf(args[4]);
      SocketAddress dest = new InetSocketAddress(host, port);


      //Read all lines of inFilename opened in UTF-8
      List<String> lines = Files.readAllLines(Paths.get(args[0]), UTF8);
      ArrayList<String> upperCaseLines = new ArrayList<>();
      ArrayBlockingQueue<ByteBuffer> queue = new ArrayBlockingQueue<>(10);

      ByteBuffer bbSender = ByteBuffer.allocate(BUFFER_SIZE);
      ByteBuffer bbReceiver = ByteBuffer.allocate(BUFFER_SIZE);

      DatagramChannel dc = DatagramChannel.open();
      // listener
      Thread listener = new Thread(() -> {
         while (!Thread.interrupted()) {
            try {
               InetSocketAddress exp = (InetSocketAddress) dc.receive(bbReceiver);
               bbReceiver.flip();
               queue.put(bbReceiver);
               System.out.println("Received " + bbReceiver.remaining() + " bytes from " + exp);
               bbReceiver.clear();
            } catch (AsynchronousCloseException | InterruptedException ie) {
               logger.info("Listener thread is interrupted");
               Thread.currentThread().interrupt();
            } catch (IOException e) {
               logger.severe("Error during receiving");
            }
         }
      });
      listener.start();

      // Send every lines
      for (String line : lines) {
         try {
            ByteBuffer msg = null;
            bbSender.put(UTF8.encode(line));
            while (msg == null) {
               bbSender.flip();
               dc.send(bbSender, dest);
               msg = queue.poll(timeout, TimeUnit.MILLISECONDS);
               if (msg == null) {
                  logger.info("Server did not respond on time, resending packet");
               } else {
                  upperCaseLines.add(UTF8.decode(msg).toString());
                  msg.clear();
               }
            }
            bbSender.clear();
         } catch (InterruptedException ie) {
            logger.info("Sender thread is interrupted");
            Thread.currentThread().interrupt();
         }
      }
      listener.interrupt();
      listener.join();

      // Write upperCaseLines to outFilename in UTF-8
      Files.write(Paths.get(outFilename), upperCaseLines, UTF8,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING);

      dc.close();
   }
}

