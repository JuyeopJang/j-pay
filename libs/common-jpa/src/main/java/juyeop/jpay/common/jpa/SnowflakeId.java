package juyeop.jpay.common.jpa;

import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Snowflake 기반 PK 자동 발급 — `@Id` 필드에 부착.
 *
 * <pre>
 * @Id
 * @SnowflakeId
 * private Long id;
 * </pre>
 */
@IdGeneratorType(SnowflakeIdHibernateGenerator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface SnowflakeId {
}