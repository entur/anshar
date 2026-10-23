package no.rutebanken.anshar.routes.avro;

import org.apache.avro.util.ClassSecurityValidator;
import org.entur.avro.realtime.siri.model.SiriRecord;

public final class AvroClassSecurity {

    private static final String SIRI_AVRO_MODEL_PACKAGE = SiriRecord.class.getPackageName();

    private AvroClassSecurity() {
    }

    /**
     * Avro 1.12.2+ only loads specific-record classes that are explicitly trusted. Without this,
     * KafkaAvroSerializer fails with SecurityException for every SIRI Avro record.
     */
    public static synchronized void trustSiriAvroModel() {
        ClassSecurityValidator.ClassSecurityPredicate current = ClassSecurityValidator.getGlobal();
        if (current.isTrusted(SiriRecord.class)) {
            return;
        }
        ClassSecurityValidator.setGlobal(ClassSecurityValidator.composite(
                current,
                clazz -> SIRI_AVRO_MODEL_PACKAGE.equals(clazz.getPackageName())
        ));
    }
}
