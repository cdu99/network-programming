package fr.upem.net.tcp.nonblocking;

public class Message {
   private final String login;
   private final String message;

   public Message(String login, String message) {
      this.login = login;
      this.message = message;
   }

   public String getLogin() {
      return login;
   }

   public String getMessage() {
      return message;
   }

   @Override
   public String toString() {
      return "Message{" +
            "login='" + login + '\'' +
            ", message='" + message + '\'' +
            '}';
   }
}
