package com.lespider.opinionflow.rag.repo

import com.lespider.opinionflow.rag.domain.MilvusImportState
import org.springframework.data.jpa.repository.JpaRepository

interface MilvusImportStateRepository : JpaRepository<MilvusImportState, String>