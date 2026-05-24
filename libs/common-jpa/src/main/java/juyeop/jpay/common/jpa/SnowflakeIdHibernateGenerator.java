package juyeop.jpay.common.jpa;

import juyeop.jpay.common.core.SnowflakeIds;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

public class SnowflakeIdHibernateGenerator implements IdentifierGenerator {

    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner) {
        return SnowflakeIds.next();
    }
}