package uz.xitlar.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
public class ResponseApi <T>{
    @Getter
    @Setter
    private T data;

    private Boolean success;

    @Getter
    @Setter
    private String message;

    public Boolean isSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }
}
