package fr.upem.net.tcp.http;

import fr.upem.net.udp.nonblocking.ClientIdUpperCaseUDPBurst;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.logging.Logger;

public class HTTPClient {

   private static final Charset ASCII_CHARSET = Charset.forName("ASCII");
   private static Logger logger = Logger.getLogger(HTTPClient.class.getName());
   private static final int BUFFER_SIZE = 1024;

   public static void main(String[] args) throws IOException {
      if (args.length != 2) {
         logger.warning("USAGE: HTTPClient address resource");
         return;
      }
      String address = args[0];
      String resource = args[1];
      String request = "GET " + resource + " HTTP/1.1\r\n"
            + "Host: " + address + "\r\n"
            + "\r\n";

      SocketChannel sc = SocketChannel.open();
      sc.connect(new InetSocketAddress(address, 80));
      sc.write(ASCII_CHARSET.encode(request));
      ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
      HTTPReader reader = new HTTPReader(sc, bb);
      var header = reader.readHeader();
      var code = header.getCode();
      if (code == 302 || code == 301) {
         var fields = header.getFields();
         var url = new URL(fields.get("location"));
         var newRequest = "GET " + url.getPath() + " HTTP/1.1\r\n"
               + "Host: " + url.getHost() + "\r\n"
               + "\r\n";
         logger.info("Redirection to: " + url);
         sc.close();
         sc = SocketChannel.open();
         sc.connect(new InetSocketAddress(url.getHost(), 80));
         sc.write(ASCII_CHARSET.encode(newRequest));
         reader = new HTTPReader(sc, bb);
         header = reader.readHeader();
      }

      if (!header.getContentType().equals("text/html") && !header.getContentType().equals("text/plain")) {
         logger.info("Not text/html or text/plain type");
         return;
      }
      if (header.getContentLength() != -1) {
         logger.info("Reading with Content-Length");
         var contentLength = header.getContentLength();
         var readBuff = reader.readBytes(contentLength);
         System.out.println(ASCII_CHARSET.decode(readBuff.flip()));
         return;
      }
      if (header.isChunkedTransfer()) {
         logger.info("Reading in chunk");
         var readBuff = reader.readChunks();
         System.out.println(ASCII_CHARSET.decode(readBuff.flip()));
      } else {
         logger.info("Wrong address or resource");
      }
      sc.close();
   }
}