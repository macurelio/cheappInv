package com.cheapp.cheappInv.infra.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {
	@Query("select e from OutboxEventEntity e where e.published = false order by e.id asc")
	List<OutboxEventEntity> findUnpublished(Pageable pageable);

	@Query("select e from OutboxEventEntity e where e.published = false order by e.id asc")
	List<OutboxEventEntity> findUnpublishedFirstN(org.springframework.data.domain.Limit limit);

	default List<OutboxEventEntity> findUnpublishedFirstN(int n) {
		return findUnpublishedFirstN(org.springframework.data.domain.Limit.of(n));
	}
}
