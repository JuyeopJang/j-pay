package juyeop.jpay.common.web.error;

import org.springframework.http.HttpStatus;

public interface ErrorType {

    String slug();

    String title();

    HttpStatus httpStatus();

    default String typeUri() {
        return "https://j-pay.dev/errors/" + slug();
    }
}