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
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientIdUpperCaseUDPBurst {

   private static Logger logger = Logger.getLogger(ClientIdUpperCaseUDPBurst.class.getName());
   private static final Charset UTF8 = StandardCharsets.UTF_8;
   private static final int BUFFER_SIZE = 1024;
   private final List<String> lines;
   private final int nbLines;
   private final String[] upperCaseLines; //
   private final int timeout;
   private final String outFilename;
   private final InetSocketAddress serverAddress;
   private final DatagramChannel dc;
   private final AnswersLog answersLog;         // Thread-safe structure keeping track of missing responses

   public static void usage() {
      System.out.println("Usage : ClientIdUpperCaseUDPBurst in-filename out-filename timeout host port ");
   }

   public ClientIdUpperCaseUDPBurst(List<String> lines, int timeout, InetSocketAddress serverAddress, String outFilename) throws IOException {
      this.lines = lines;
      this.nbLines = lines.size();
      this.timeout = timeout;
      this.outFilename = outFilename;
      this.serverAddress = serverAddress;
      this.dc = DatagramChannel.open();
      dc.bind(null);
      this.upperCaseLines = new String[nbLines];
      this.answersLog = new AnswersLog();
   }

   private void senderThreadRun() {
      var bbSender = ByteBuffer.allocate(BUFFER_SIZE);
      long idCounter = 0;
      try {
         while (!Thread.interrupted()) {
            for (String line : lines) {
               if (answersLog.get((int) idCounter)) {
                  bbSender.putLong(idCounter);
                  bbSender.put(UTF8.encode(line));
                  bbSender.flip();
                  dc.send(bbSender, serverAddress);
                  bbSender.clear();
               }
               idCounter++;
            }
            idCounter = 0;
            Thread.sleep(timeout);
         }
      } catch (InterruptedException | AsynchronousCloseException e) {
         Thread.currentThread().interrupt();
      } catch (IOException e) {
         logger.severe("Error caused by IOException");
      } finally {
         logger.info("Sender thread is interrupted");
      }
   }

   public void launch() throws IOException, InterruptedException {
      answersLog.initialize(nbLines);
      Thread senderThread = new Thread(this::senderThreadRun);
      senderThread.start();
      var bbReceiver = ByteBuffer.allocate(BUFFER_SIZE);

      while (!answersLog.isFinished()) {
         InetSocketAddress exp = (InetSocketAddress) dc.receive(bbReceiver);
         bbReceiver.flip();
         int currentId = (int) bbReceiver.getLong();
         answersLog.setToFalse(currentId);
         upperCaseLines[currentId] = UTF8.decode(bbReceiver).toString();
         bbReceiver.clear();
      }

      Files.write(Paths.get(outFilename), Arrays.asList(upperCaseLines), UTF8,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING);

      senderThread.interrupt();
      senderThread.join();
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
      ClientIdUpperCaseUDPBurst client = new ClientIdUpperCaseUDPBurst(lines, timeout, serverAddress, outFilename);
      client.launch();

   }

   private static class AnswersLog {
      private final Object lock = new Object();
      private final BitSet bitSet;

      AnswersLog() {
         bitSet = new BitSet();
      }

      void initialize(int nbLines) {
         synchronized (lock) {
            for (int i = 0; i < nbLines; i++) {
               bitSet.set(i);
            }
         }
      }

      void setToFalse(int index) {
         synchronized (lock) {
            bitSet.set(index, false);
         }
      }

      boolean get(int index) {
         synchronized (lock) {
            return bitSet.get(index);
         }
      }

      boolean isFinished() {
         synchronized (lock) {
            return bitSet.isEmpty();
         }
      }
   }
}

