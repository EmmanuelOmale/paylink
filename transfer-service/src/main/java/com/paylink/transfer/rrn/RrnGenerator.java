package com.paylink.transfer.rrn;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class RrnGenerator {

    private final JdbcClient jdbc;

    public RrnGenerator(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public String next(Instant at) {
        long sequence = jdbc.sql("SELECT NEXT VALUE FOR rrn_seq").query(Long.class).single();
        ZonedDateTime utc = at.atZone(ZoneOffset.UTC);
        return String.format(Locale.ROOT, "%d%03d%02d%06d",
                utc.getYear() % 10, utc.getDayOfYear(), utc.getHour(), sequence);
    }
}
