package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Scanner;
import java.util.logging.Logger;

public class ClientBetterUpperCaseUDP {

   private static final Logger logger = Logger.getLogger(ClientBetterUpperCaseUDP.class.getName());
   private static final int MAX_PACKET_SIZE = 1024;

   private static Charset ASCII_CHARSET = StandardCharsets.US_ASCII; //Charset.forName("US-ASCII");

   /**
    * Creates and returns an Optional containing a String message represented by the ByteBuffer buffer,
    * encoded in the following representation:
    * - the size (as a Big Indian int) of a charsetName encoded in ASCII<br/>
    * - the bytes encoding this charsetName in ASCII<br/>
    * - the bytes encoding the message in this charset.<br/>
    * The accepted ByteBuffer buffer must be in <strong>write mode</strong>
    * (i.e. need to be flipped before to be used).
    *
    * @param buffer a ByteBuffer containing the representation of an encoded String message
    * @return an Optional containing the String represented by buffer, or an empty Optional if the buffer cannot be decoded
    */
   public static Optional<String> decodeMessage(ByteBuffer buffer) {
      // need to read buffer
      buffer.flip();
      if (buffer.remaining() < Integer.BYTES) {
         logger.info("Decode buffer does not contain charset info");
         return Optional.empty();
      }
      var charsetNameSize = buffer.getInt();

      if (buffer.remaining() < charsetNameSize) {
         logger.info("Decode buffer does not contain charset");
         return Optional.empty();
      }

      // Ne pas passer par un buffer intermédiaire
      var bufferCharset = ByteBuffer.allocate(MAX_PACKET_SIZE);
      for (var i = 0; i < charsetNameSize; i++) {
         bufferCharset.put(buffer.get());
      }
      // need to read bufferCharset
      bufferCharset.flip();
      try {
         var charset = Charset.forName(ASCII_CHARSET.decode(bufferCharset).toString());
         return Optional.of(charset.decode(buffer).toString());
      } catch (IllegalArgumentException iae) {
         logger.info("Decode buffer does not contain correct charset");
         return Optional.empty();
      }
   }

   /**
    * Creates and returns an Optional containing a new ByteBuffer containing the encoded representation
    * of the String <code>msg</code> using the charset <code>charsetName</code>
    * in the following format:
    * - the size (as a Big Indian int) of the charsetName encoded in ASCII<br/>
    * - the bytes encoding this charsetName in ASCII<br/>
    * - the bytes encoding the String msg in this charset.<br/>
    * The returned ByteBuffer is in <strong>write mode</strong> (i.e. need to
    * be flipped before to be used).
    * If the buffer is larger than MAX_PACKET_SIZE bytes, then returns Optional.empty.
    *
    * @param msg         the String to encode
    * @param charsetName the name of the Charset to encode the String msg
    * @return an Optional containing a newly allocated ByteBuffer containing the representation of msg,
    * or an empty Optional if the buffer would be larger than 1024
    */
   public static Optional<ByteBuffer> encodeMessage(String msg, String charsetName) {
      var charset = Charset.forName(charsetName);
      var encodedCharsetName = ASCII_CHARSET.encode(charsetName);
      var buff = ByteBuffer.allocate(MAX_PACKET_SIZE);
      buff.putInt(encodedCharsetName.remaining());
      buff.put(encodedCharsetName);
      // Check no catch
      try {
         buff.put(charset.encode(msg));
      } catch (BufferOverflowException e) {
         logger.info("Encode buffer is larger than " + MAX_PACKET_SIZE);
         return Optional.empty();
      }
      return Optional.of(buff);
   }

   public static void usage() {
      System.out.println("Usage : ClientBetterUpperCaseUDP host port charsetName");
   }

   public static void main(String[] args) throws IOException {

      // check and retrieve parameters
      if (args.length != 3) {
         usage();
         return;
      }
      var host = args[0];
      var port = Integer.valueOf(args[1]);
      var charsetName = args[2];

      var dest = new InetSocketAddress(host, port);
      // buff to receive messages
      var buff = ByteBuffer.allocateDirect(MAX_PACKET_SIZE);

      try (var scan = new Scanner(System.in);
           var dc = DatagramChannel.open()) {
         while (scan.hasNextLine()) {
            var line = scan.nextLine();

            var enc = encodeMessage(line, charsetName);
            if (enc.isEmpty()) {
               System.out.println("Line is too long to be sent using the protocol BetterUpperCase");
               continue;
            }
            var packet = enc.get();
            packet.flip();
            dc.send(packet, dest);
            buff.clear();
            dc.receive(buff);

            decodeMessage(buff).ifPresentOrElse(
                  (str) -> System.out.println("Received: " + str),
                  () -> System.out.println("Received an invalid paquet"));
         }
      }
   }

}