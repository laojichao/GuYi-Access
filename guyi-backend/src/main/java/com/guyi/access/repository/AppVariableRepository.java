package com.guyi.access.repository;

import com.guyi.access.entity.AppVariable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface AppVariableRepository extends JpaRepository<AppVariable, Integer> {

    List<AppVariable> findByAppId(Integer appId);

    List<AppVariable> findByAppIdAndIsPublic(Integer appId, Integer isPublic);

    @Modifying
    @Query("DELETE FROM AppVariable v WHERE v.appId = :appId")
    int deleteByAppId(@Param("appId") Integer appId);

    Optional<AppVariable> findByAppIdAndKeyName(Integer appId, String keyName);

    boolean existsByAppIdAndKeyNameAndIdNot(Integer appId, String keyName, Integer id);
}
