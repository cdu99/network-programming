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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ClientIdUpperCaseUDPOneByOne {

   private static Logger logger = Logger.getLogger(ClientIdUpperCaseUDPOneByOne.class.getName());
   private static final Charset UTF8 = StandardCharsets.UTF_8;
   private static final int BUFFER_SIZE = 1024;
   private final List<String> lines;
   private final List<String> upperCaseLines = new ArrayList<>(); //
   private final int timeout;
   private final String outFilename;
   private final InetSocketAddress serverAddress;
   private final DatagramChannel dc;

   private final BlockingQueue<Response> queue = new SynchronousQueue<>();


   public static void usage() {
      System.out.println("Usage : ClientIdUpperCaseUDPOneByOne in-filename out-filename timeout host port ");
   }

   public ClientIdUpperCaseUDPOneByOne(List<String> lines, int timeout, InetSocketAddress serverAddress, String outFilename) throws IOException {
      this.lines = lines;
      this.timeout = timeout;
      this.outFilename = outFilename;
      this.serverAddress = serverAddress;
      this.dc = DatagramChannel.open();
      dc.bind(null);
   }

   private void listenerThreadRun() {
      var bbReceiver = ByteBuffer.allocate(BUFFER_SIZE);
      while (!Thread.interrupted()) {
         try {
            InetSocketAddress exp = (InetSocketAddress) dc.receive(bbReceiver);
            bbReceiver.flip();
            queue.put(new Response(bbReceiver.getLong(), UTF8.decode(bbReceiver).toString()));
            System.out.println("Received " + bbReceiver.remaining() + " bytes from " + exp);
            bbReceiver.clear();
         } catch (AsynchronousCloseException | InterruptedException ie) {
            Thread.currentThread().interrupt();
         } catch (IOException e) {
            logger.severe("Error caused by IOException");
         } finally {
            logger.info("Listener thread is interrupted");
         }
      }
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
      InetSocketAddress serverAddress = new InetSocketAddress(host, port);

      //Read all lines of inFilename opened in UTF-8
      List<String> lines = Files.readAllLines(Paths.get(inFilename), UTF8);
      //Create client with the parameters and launch it
      ClientIdUpperCaseUDPOneByOne client = new ClientIdUpperCaseUDPOneByOne(lines, timeout, serverAddress, outFilename);
      client.launch();

   }

   public void launch() throws IOException, InterruptedException {
      Thread listenerThread = new Thread(this::listenerThreadRun);
      listenerThread.start();

      //sender
      var bbSender = ByteBuffer.allocate(BUFFER_SIZE);
      long idCounter = 0;
      long lastSent = 0;
      for (String line : lines) {
         try {
            Response response = null;
            bbSender.putLong(idCounter);
            bbSender.put(UTF8.encode(line));
            while (response == null || response.id != idCounter) {
               if (System.currentTimeMillis() - lastSent >= timeout) {
                  bbSender.flip();
                  dc.send(bbSender, serverAddress);
                  lastSent = System.currentTimeMillis();
               }

               response = queue.poll(timeout - (System.currentTimeMillis() - lastSent), TimeUnit.MILLISECONDS);

               if (response == null || response.id != idCounter) {
                  logger.info("Resending packet");
               } else {
                  upperCaseLines.add(response.msg);
               }
            }
            bbSender.clear();
            idCounter++;
         } catch (InterruptedException | AsynchronousCloseException e) {
            Thread.currentThread().interrupt();
         } catch (IOException e) {
            logger.severe("Error caused by IOException");
         } finally {
            logger.info("Listener thread is interrupted");
         }
      }

      Files.write(Paths.get(outFilename), upperCaseLines, UTF8,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING);

      listenerThread.interrupt();
      listenerThread.join();
      return;
   }


   private static class Response {
      long id;
      String msg;

      Response(long id, String msg) {
         this.id = id;
         this.msg = msg;
      }
   }
}