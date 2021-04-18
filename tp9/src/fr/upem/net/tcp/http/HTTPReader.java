package fr.upem.net.tcp.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;


public class HTTPReader {

   private final Charset ASCII_CHARSET = Charset.forName("ASCII");
   private final SocketChannel sc;
   private final ByteBuffer buff;

   public HTTPReader(SocketChannel sc, ByteBuffer buff) {
      this.sc = sc;
      this.buff = buff;
   }

   /**
    * @return The ASCII string terminated by CRLF without the CRLF
    * <p>
    * The method assume that buff is in write mode and leaves it in write-mode
    * The method does perform a read from the socket if the buffer data. The will process
    * the data from the buffer if necessary will read from the socket.
    * @throws IOException HTTPException if the connection is closed before a line could be read
    */
   public String readLineCRLF() throws IOException {
      var sb = new StringBuilder();
      boolean lastCR = false;
      buff.flip();
      while (true) {
         if (!buff.hasRemaining()) {
            buff.clear();
            if (sc.read(buff) == -1) {
               throw new HTTPException();
            }
            buff.flip();
         }
         char currentChar = (char) buff.get(); // Only in ASCII
         sb.append(currentChar);
         if (currentChar == '\n' && lastCR) {
            break;
         }
         lastCR = (currentChar == '\r');
      }
      buff.compact();
      sb.setLength(sb.length() - 2);
      return sb.toString();
   }

   /**
    * @return The HTTPHeader object corresponding to the header read
    * @throws IOException HTTPException if the connection is closed before a header could be read
    *                     if the header is ill-formed
    */
   public HTTPHeader readHeader() throws IOException {
      var map = new HashMap<String, String>();
      var response = readLineCRLF();
      while (true) {
         var currentLine = readLineCRLF();
         if (!currentLine.contains(":")) {
            break;
         }
         var fieldAndValue = currentLine.split(":", 2);
         var field = fieldAndValue[0];
         var value = fieldAndValue[1];
         if (map.containsKey(field)) {
            var currentValue = map.get(field);
            map.put(field, currentValue + ";" + value);
         } else {
            map.put(fieldAndValue[0], fieldAndValue[1]);
         }
      }
      return HTTPHeader.create(response, map);
   }

   /**
    * @param size The method assume that buff is in write mode and leaves it in write-mode
    *             The method does perform a read from the socket if the buffer data. The will process
    *             the data from the buffer if necessary will read from the socket.
    * @return a ByteBuffer in write-mode containing size bytes read on the socket
    * @throws IOException HTTPException is the connection is closed before all bytes could be read
    */
   public ByteBuffer readBytes(int size) throws IOException {
      var readBuff = ByteBuffer.allocate(size);
      buff.flip();
      while (buff.hasRemaining() && readBuff.hasRemaining()) {
         readBuff.put(buff.get());
      }
      while (readBuff.hasRemaining()) {
         if (sc.read(readBuff) == -1) {
            throw new HTTPException();
         }
      }
      buff.compact();
      return readBuff;
   }

   /**
    * @return a ByteBuffer in write-mode containing a content read in chunks mode
    * @throws IOException HTTPException if the connection is closed before the end of the chunks
    *                     if chunks are ill-formed
    */

   public ByteBuffer readChunks() throws IOException {
      var buff = ByteBuffer.allocate(1024);
      while (true) {
         var size = Integer.parseInt(readLineCRLF(), 16);
         if (size == 0) {
            readLineCRLF();
            break;
         }
         if (buff.remaining() < size) {
            var buff2 = ByteBuffer.allocate(buff.capacity() + size);
            buff2.put(buff);
            buff = buff2;
         }
         buff.put(readBytes(size).flip());
         readLineCRLF();
      }
      return buff;
   }


   public static void main(String[] args) throws IOException {
      Charset charsetASCII = Charset.forName("ASCII");
      String request = "GET / HTTP/1.1\r\n"
            + "Host: www.w3.org\r\n"
            + "\r\n";
      SocketChannel sc = SocketChannel.open();
      sc.connect(new InetSocketAddress("www.w3.org", 80));
      sc.write(charsetASCII.encode(request));
      ByteBuffer bb = ByteBuffer.allocate(50);
      HTTPReader reader = new HTTPReader(sc, bb);
      System.out.println(reader.readLineCRLF());
      System.out.println(reader.readLineCRLF());
      System.out.println(reader.readLineCRLF());
      sc.close();

      bb = ByteBuffer.allocate(50);
      sc = SocketChannel.open();
      sc.connect(new InetSocketAddress("www.w3.org", 80));
      reader = new HTTPReader(sc, bb);
      sc.write(charsetASCII.encode(request));
      System.out.println(reader.readHeader());
      sc.close();

      bb = ByteBuffer.allocate(50);
      sc = SocketChannel.open();
      sc.connect(new InetSocketAddress("www.w3.org", 80));
      reader = new HTTPReader(sc, bb);
      sc.write(charsetASCII.encode(request));
      HTTPHeader header = reader.readHeader();
      System.out.println(header);
      ByteBuffer content = reader.readBytes(header.getContentLength());
      content.flip();
      System.out.println(header.getCharset().decode(content));
      sc.close();

      bb = ByteBuffer.allocate(50);
      request = "GET / HTTP/1.1\r\n"
            + "Host: www.u-pem.fr\r\n"
            + "\r\n";
      sc = SocketChannel.open();
      sc.connect(new InetSocketAddress("www.u-pem.fr", 80));
      reader = new HTTPReader(sc, bb);
      sc.write(charsetASCII.encode(request));
      header = reader.readHeader();
      System.out.println(header);
      content = reader.readChunks();
      content.flip();
      System.out.println(header.getCharset().decode(content));
      sc.close();
   }
}