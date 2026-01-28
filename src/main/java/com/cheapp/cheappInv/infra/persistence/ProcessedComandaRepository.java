package com.cheapp.cheappInv.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedComandaRepository extends JpaRepository<ProcessedComandaEntity, Long> {
	boolean existsByComandaId(String comandaId);
}
