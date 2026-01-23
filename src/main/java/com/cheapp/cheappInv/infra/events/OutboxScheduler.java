package com.cheapp.cheappInv.infra.events;

import com.cheapp.cheappInv.infra.persistence.OutboxEventEntity;
import com.cheapp.cheappInv.infra.persistence.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class OutboxScheduler {
	private final OutboxEventRepository outboxEventRepository;
	private final OutboxPublisher publisher;
	private final Clock clock;
	private final boolean enabled;
	private final int batchSize;

	public OutboxScheduler(OutboxEventRepository outboxEventRepository,
						  OutboxPublisher publisher,
						  Clock clock,
						  @Value("${inventory.outbox.enabled:true}") boolean enabled,
						  @Value("${inventory.outbox.batch-size:50}") int batchSize) {
		this.outboxEventRepository = outboxEventRepository;
		this.publisher = publisher;
		this.clock = clock;
		this.enabled = enabled;
		this.batchSize = batchSize;
	}

	@Scheduled(fixedDelayString = "${inventory.outbox.poll-interval:2s}")
	@Transactional
	public void pollAndPublish() {
		if (!enabled) {
			return;
		}

		List<OutboxEventEntity> batch = outboxEventRepository.findUnpublished(PageRequest.of(0, batchSize));
		if (batch.isEmpty()) {
			return;
		}

		Instant now = Instant.now(clock);
		for (OutboxEventEntity e : batch) {
			publisher.publish(e.getEventId(), e.getEventType(), e.getCorrelationId(), e.getPayloadJson());
			e.markPublished(now);
		}
		outboxEventRepository.saveAll(batch);
	}
}
