package uz.xitlar.exception;

public class SelfRoleChangeException extends RuntimeException {
    public SelfRoleChangeException(String message) {
        super(message);
    }
}
