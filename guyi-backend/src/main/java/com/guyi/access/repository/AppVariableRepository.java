package com.guyi.access.repository;

import com.guyi.access.entity.AppVariable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AppVariableRepository extends JpaRepository<AppVariable, Integer> {

    List<AppVariable> findByAppId(Integer appId);

    List<AppVariable> findByAppIdAndIsPublic(Integer appId, Integer isPublic);

    Optional<AppVariable> findByAppIdAndKeyName(Integer appId, String keyName);

    boolean existsByAppIdAndKeyNameAndIdNot(Integer appId, String keyName, Integer id);
}
