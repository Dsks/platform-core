package app.qomo.emailsender.application.exception;

public class InvalidEmailCommandException extends RuntimeException {

  public InvalidEmailCommandException(String message) {
    super(message);
  }
}