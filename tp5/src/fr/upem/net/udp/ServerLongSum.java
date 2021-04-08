package fr.upem.net.udp;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Optional;
import java.util.logging.Logger;
import javax.swing.text.html.Option;

public class ServerLongSum {
   private static final Logger logger = Logger.getLogger(ServerLongSum.class.getName());
   private static final int BUFFER_SIZE = 1024;
   private final DatagramChannel dc;
   private final ByteBuffer buffReceive = ByteBuffer.allocateDirect(BUFFER_SIZE);
   private final ByteBuffer buffSend = ByteBuffer.allocateDirect(BUFFER_SIZE);
   private final HashMap<InetSocketAddress, HashMap<Long, SumSession>> map = new HashMap<>();

   public ServerLongSum(int port) throws IOException {
      dc = DatagramChannel.open();
      dc.bind(new InetSocketAddress(port));
      logger.info("ServerLongSum started on port " + port);
   }

   private void serve() throws IOException {
      while (!Thread.interrupted()) {
         buffReceive.clear();
         InetSocketAddress exp = (InetSocketAddress) dc.receive(buffReceive);
         buffReceive.flip();
         logger.info("Received " + buffReceive.remaining() + " bytes from " + exp.toString());
         if (buffReceive.remaining() != 33) {
            logger.warning("Received incorrect packet");
            continue;
         }
         var packetType = buffReceive.get();
         if (packetType != 1) {
            logger.warning("Received incorrect packet");
            continue;
         }
         var sessionId = buffReceive.getLong();
         var idPosOper = buffReceive.getLong();
         var totalOper = buffReceive.getLong();
         var clientMap = map.computeIfAbsent(exp, e -> new HashMap<>());
         var sumInfo = clientMap.computeIfAbsent(sessionId, i -> new SumSession(totalOper));
         var currentSum = sumInfo.sum(idPosOper, buffReceive.getLong());
         buffSend.clear();
         if (currentSum.isEmpty()) {
            buffSend.put((byte) 2);
            buffSend.putLong(sessionId);
            buffSend.putLong(idPosOper).flip();
            logger.info("Sending ACK " + buffSend.remaining() + " bytes to " + exp.toString());
         } else {
            buffSend.put((byte) 3);
            buffSend.putLong(sessionId);
            buffSend.putLong(currentSum.get()).flip();
            logger.info("Sending RES " + buffSend.remaining() + " bytes to " + exp.toString());
         }
         dc.send(buffSend, exp);
      }
   }

   private static class SumSession {
      private Long sum;
      private final BitSet bitSet = new BitSet();
      private int remaining;

      SumSession(Long totalOper) {
         for (int i = 0; i < totalOper; i++) {
            bitSet.set(i);
         }
         remaining = totalOper.intValue();
         sum = 0l;
      }

      public Optional<Long> sum(Long idPosOper, Long opValue) {
         if (remaining <= 0) {
            return Optional.of(sum);
         }
         if (!bitSet.get(idPosOper.intValue())) {
            return Optional.empty();
         }
         bitSet.set(idPosOper.intValue(), false);
         remaining--;
         sum += opValue;
         if (remaining <= 0) {
            return Optional.of(sum);
         } else {
            return Optional.empty();
         }
      }
   }

   public static void usage() {
      System.out.println("Usage : ServerLongSum port");
   }

   public static void main(String[] args) throws IOException {
      if (args.length != 1) {
         usage();
         return;
      }
      ServerLongSum server;
      int port = Integer.valueOf(args[0]);
      if (!(port >= 1024) & port <= 65535) {
         logger.severe("The port number must be between 1024 and 65535");
         return;
      }
      try {
         server = new ServerLongSum(port);
      } catch (BindException e) {
         logger.severe("Server could not bind on " + port + "\nAnother server is probably running on this port.");
         return;
      }
      server.serve();
   }
}
