package juyeop.jpay.common.web.error;

import org.springframework.http.HttpStatus;

/**
 * RFC 7807 Problem Details의 type/title/status를 정의하는 인터페이스.
 * 각 도메인 모듈은 자기 ErrorType enum을 만들어 이 인터페이스를 구현.
 */
public interface ErrorType {

    /** type URI의 slug 부분 (예: "card-declined"). */
    String slug();

    /** RFC 7807 title — 사람 읽기용 짧은 요약. */
    String title();

    HttpStatus httpStatus();

    /** 기본 type URI. 도메인별 override 가능. */
    default String typeUri() {
        return "https://j-pay.dev/errors/" + slug();
    }
}