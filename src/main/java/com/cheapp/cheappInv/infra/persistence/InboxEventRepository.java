package com.cheapp.cheappInv.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxEventRepository extends JpaRepository<InboxEventEntity, Long> {
	boolean existsByEventId(String eventId);
}
