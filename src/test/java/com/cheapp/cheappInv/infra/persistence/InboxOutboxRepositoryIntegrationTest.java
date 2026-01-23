package com.cheapp.cheappInv.infra.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class InboxOutboxRepositoryIntegrationTest {
	@Autowired
	InboxEventRepository inboxEventRepository;
	@Autowired
	OutboxEventRepository outboxEventRepository;

	@Test
	void inbox_existsByEventId_funciona() {
		inboxEventRepository.save(new InboxEventEntity("evt-1", "ItemAgregado", Instant.parse("2026-01-01T00:00:00Z")));
		assertThat(inboxEventRepository.existsByEventId("evt-1")).isTrue();
		assertThat(inboxEventRepository.existsByEventId("evt-x")).isFalse();
	}

	@Test
	void outbox_findUnpublished_devuelveSoloNoPublicados() {
		outboxEventRepository.save(new OutboxEventEntity("id-1", "StockRepuesto", 1, "corr", "{}", Instant.parse("2026-01-01T00:00:00Z")));
		OutboxEventEntity published = outboxEventRepository.save(new OutboxEventEntity("id-2", "StockRepuesto", 1, "corr", "{}", Instant.parse("2026-01-01T00:00:00Z")));
		published.markPublished(Instant.parse("2026-01-01T00:00:01Z"));
		outboxEventRepository.save(published);

		var list = outboxEventRepository.findUnpublishedFirstN(10);
		assertThat(list).extracting(OutboxEventEntity::getEventId).containsExactly("id-1");
	}
}
