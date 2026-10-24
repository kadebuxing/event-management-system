package com.eventmanagement.persistence;

import java.io.IOException;

public interface EventRepository {
    DataSnapshot load() throws IOException;

    void save(DataSnapshot snapshot) throws IOException;
}
