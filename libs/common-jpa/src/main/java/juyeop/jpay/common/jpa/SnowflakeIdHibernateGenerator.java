package juyeop.jpay.common.jpa;

import juyeop.jpay.common.core.SnowflakeIds;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

/**
 * Hibernate IdentifierGenerator → SnowflakeIds 정적 싱글턴 어댑터.
 * Hibernate가 INSERT 직전에 generate() 호출 → SnowflakeIds.next() 반환.
 */
public class SnowflakeIdHibernateGenerator implements IdentifierGenerator {

    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner) {
        return SnowflakeIds.next();
    }
}