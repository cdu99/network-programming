package fr.upem.net.buffers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class ReadStandardInputWithEncoding {

   private static final int BUFFER_SIZE = 1024;

   private static void usage(){
      System.out.println("Usage: ReadStandardInputWithEncoding charset");
   }

   private static String stringFromStandardInput(Charset cs) throws IOException {
      ReadableByteChannel in = Channels.newChannel(System.in);
      ByteBuffer buff = ByteBuffer.allocate(BUFFER_SIZE);

      while(in.read(buff) != -1) {
         if(!buff.hasRemaining()) {
            ByteBuffer newBuff = ByteBuffer.allocate(buff.capacity() * 2);
            buff.flip();
            newBuff.put(buff);
            buff = newBuff;
         }
      }

      buff.flip();
      CharBuffer cb = cs.decode(buff);
      return cb.toString();
   }

   public static void main(String[] args) throws IOException {
      if (args.length!=1){
         usage();
         return;
      }
      Charset cs=Charset.forName(args[0]);
      System.out.print(stringFromStandardInput(cs));
   }
}