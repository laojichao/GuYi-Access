package com.guyi.access.repository;

import com.guyi.access.entity.Blacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BlacklistRepository extends JpaRepository<Blacklist, Integer> {

    Optional<Blacklist> findByTypeAndValue(String type, String value);

    /** The unique constraint is on value alone, so de-duplication must use value alone too. */
    Optional<Blacklist> findByValue(String value);

    List<Blacklist> findAllByOrderByCreateTimeDesc();
}
