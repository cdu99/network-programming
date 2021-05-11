package fr.upem.net.tcp.nonblocking;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class MessageReader implements Reader<Message> {

   private enum State {DONE, WAITING, ERROR}

   ;

   static private int BUFFER_SIZE = 1_024;
   private State state = State.WAITING;
   private final StringReader stringReader = new StringReader();
   private final ByteBuffer internalbb = ByteBuffer.allocate(BUFFER_SIZE); // write-mode
   private Message value;
   private String pseudo;
   private String msg;
   private boolean gotPseudo = false;

   @Override
   public ProcessStatus process(ByteBuffer bb) {
      if (state == State.DONE || state == State.ERROR) {
         throw new IllegalStateException();
      }
      if (!gotPseudo) {
         switch (stringReader.process(bb)) {
            case ERROR:
               return ProcessStatus.ERROR;
            case REFILL:
               return ProcessStatus.REFILL;
            case DONE:
               pseudo = stringReader.get();
               gotPseudo = true;
               stringReader.reset();
         }
      }
      if (gotPseudo) {
         switch (stringReader.process(bb)) {
            case ERROR:
               return ProcessStatus.ERROR;
            case REFILL:
               return ProcessStatus.REFILL;
            case DONE:
               msg = stringReader.get();
               state = State.DONE;
               value = new Message(pseudo, msg);
               gotPseudo = false;
               return ProcessStatus.DONE;
         }
      }
      return ProcessStatus.ERROR;
   }

   @Override
   public Message get() {
      if (state != State.DONE) {
         throw new IllegalStateException();
      }
      return value;
   }

   @Override
   public void reset() {
      state = State.WAITING;
      stringReader.reset();
      internalbb.clear();
      value = null;
   }
}