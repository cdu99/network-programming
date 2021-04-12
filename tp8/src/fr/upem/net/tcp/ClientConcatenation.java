package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.swing.text.html.Option;

public class ClientConcatenation {

   private static final int BUFFER_SIZE = 1024;
   public static final Logger logger = Logger.getLogger(ClientConcatenation.class.getName());
   private static final Charset UTF = Charset.forName("UTF-8");
   private static final ArrayList<Integer> sizes = new ArrayList<>();

   private static boolean readFully(SocketChannel sc, ByteBuffer bb) throws IOException {
      bb.clear();
      while (bb.hasRemaining()) {
         if (sc.read(bb) == -1) {
            return false;
         }
      }
      return true;
   }

   public static void main(String[] args) throws IOException {
      InetSocketAddress server = new InetSocketAddress(args[0], Integer.valueOf(args[1]));
      ByteBuffer buff = ByteBuffer.allocate(BUFFER_SIZE);
      ByteBuffer receiveBuff = ByteBuffer.allocate(Integer.BYTES);
      try (SocketChannel sc = SocketChannel.open(server);
           var scan = new Scanner(System.in)) {
         int nbOfStrings = 0;
         buff.position(Integer.BYTES);
         while (scan.hasNextLine()) {
            String currentString = scan.nextLine();
            if (currentString.equals("")) {
               buff.position(0);
               buff.putInt(nbOfStrings);
               buff.position(0);
               buff.limit(sizes.size() * Integer.BYTES + sizes.stream().mapToInt(Integer::intValue).sum() + Integer.BYTES);
               sc.write(buff);
               buff.clear();
               buff.position(Integer.BYTES);
               if (!readFully(sc, receiveBuff)) {
                  logger.info("Connection with server lost.");
                  return;
               }
               receiveBuff.flip();
               var responseBuff = ByteBuffer.allocate(receiveBuff.getInt());
               if (!readFully(sc, responseBuff)) {
                  logger.info("Connection with server lost.");
                  return;
               }
               receiveBuff.clear();
               responseBuff.flip();
               logger.info("Received: " + UTF.decode(responseBuff));
               sizes.clear();
               nbOfStrings = 0;
            } else {
               var encoded = UTF.encode(currentString);
               var size = encoded.remaining();
               sizes.add(size);
               buff.putInt(size);
               buff.put(encoded);
               nbOfStrings++;
            }
         }
      }
   }
}