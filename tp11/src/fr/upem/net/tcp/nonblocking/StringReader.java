package fr.upem.net.tcp.nonblocking;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class StringReader implements Reader<String> {

   private enum State {DONE, WAITING, ERROR}

   ;

   static private int BUFFER_SIZE = 1_024;
   private State state = State.WAITING;
   private static final Charset UTF = Charset.forName("UTF-8");
   private final IntReader intReader = new IntReader();
   private final ByteBuffer internalbb = ByteBuffer.allocate(BUFFER_SIZE); // write-mode
   private String value;
   private boolean done = false;

   @Override
   public ProcessStatus process(ByteBuffer bb) {
      if (state == State.DONE || state == State.ERROR) {
         throw new IllegalStateException();
      }
      if (!done) {
         switch (intReader.process(bb)) {
            case ERROR:
               return ProcessStatus.ERROR;
            case REFILL:
               return ProcessStatus.REFILL;
            case DONE:
               if (intReader.get() > 1024 || intReader.get() <= 0) {
                  return ProcessStatus.ERROR;
               }
               internalbb.limit(intReader.get());
               done = true;
         }
      }
      bb.flip();
      try {
         if (bb.remaining() <= internalbb.remaining()) {
            internalbb.put(bb);
         } else {
            var oldLimit = bb.limit();
            bb.limit(internalbb.remaining());
            internalbb.put(bb);
            bb.limit(oldLimit);
         }
      } finally {
         bb.compact();
      }
      if (internalbb.position() < intReader.get()) {
         return ProcessStatus.REFILL;
      }
      state = State.DONE;
      internalbb.flip();
      value = UTF.decode(internalbb).toString();
      return ProcessStatus.DONE;
   }

   @Override
   public String get() {
      if (state != State.DONE) {
         throw new IllegalStateException();
      }
      return value;
   }

   @Override
   public void reset() {
      state = State.WAITING;
      intReader.reset();
      internalbb.clear();
      value = null;
      done = false;
   }
}