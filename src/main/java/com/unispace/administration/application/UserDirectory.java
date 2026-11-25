package com.unispace.administration.application;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

public interface UserDirectory {
    record Summary(String userId, String firstName, String lastName, String role, String accountStatus) {}
    record Channel(String channelId, String name) {}
    record Detail(String userId, String firstName, String lastName, String role, String accountStatus,
                  String studentId, String universityEmail, Channel channel) {}
    record Page(List<Summary> items, String nextCursor) {}

    Page list(BigInteger afterId, int limit);

    Optional<Detail> find(BigInteger userId);
}
