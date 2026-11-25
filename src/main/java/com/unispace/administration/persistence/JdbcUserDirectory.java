package com.unispace.administration.persistence;

import com.unispace.administration.application.UserDirectory;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUserDirectory implements UserDirectory {
    private static final String REGISTERED = """
            u.status IN ('ACTIVE','DISABLED') AND u.email_verified_at<=UTC_TIMESTAMP(6)
            """;

    private final JdbcTemplate jdbc;

    public JdbcUserDirectory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Page list(BigInteger afterId, int limit) {
        var rows = jdbc.query("""
                SELECT u.id,u.first_name,u.last_name,u.role,u.status
                FROM users u WHERE u.id>? AND
                """ + REGISTERED + """
                ORDER BY u.id LIMIT ?
                """, (row, index) -> summary(row), new BigDecimal(afterId), limit + 1);
        if (rows.size() <= limit) return new Page(List.copyOf(rows), null);
        var items = List.copyOf(rows.subList(0, limit));
        return new Page(items, items.getLast().userId());
    }

    @Override
    public Optional<Detail> find(BigInteger userId) {
        return jdbc.query("""
                SELECT u.id,u.first_name,u.last_name,u.role,u.status,u.student_id,u.university_email,
                       c.id AS channel_id,c.name AS channel_name
                FROM users u
                LEFT JOIN channel_memberships m ON m.user_id=u.id
                LEFT JOIN channels c ON c.id=m.channel_id
                WHERE u.id=? AND
                """ + REGISTERED, row -> {
            if (!row.next()) return Optional.empty();
            var user = summary(row);
            var channelId = row.getBigDecimal("channel_id");
            var channel = channelId == null ? null : new Channel(channelId.toBigIntegerExact().toString(),
                    row.getString("channel_name"));
            return Optional.of(new Detail(user.userId(), user.firstName(), user.lastName(), user.role(),
                    user.accountStatus(), row.getString("student_id"), row.getString("university_email"), channel));
        }, new BigDecimal(userId));
    }

    private static Summary summary(ResultSet row) throws SQLException {
        return new Summary(row.getBigDecimal("id").toBigIntegerExact().toString(), row.getString("first_name"),
                row.getString("last_name"), row.getString("role"), row.getString("status"));
    }
}
